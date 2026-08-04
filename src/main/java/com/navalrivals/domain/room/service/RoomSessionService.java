package com.navalrivals.domain.room.service;

import com.navalrivals.domain.game.service.GameDisconnectService;
import com.navalrivals.domain.room.entity.Room;
import com.navalrivals.domain.room.enums.RoomStatus;
import com.navalrivals.domain.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.UUID;

/**
 * Gerencia o tracking de sessões WebSocket de hosts na tela de espera.
 *
 * Usa Redis para armazenar o mapeamento sessionId → roomId,
 * permitindo que qualquer instância processe o disconnect do host.
 *
 * Quando o host cria uma sala e se inscreve no tópico da sala,
 * ele envia /app/room/{roomId}/register para registrar sua sessão.
 * Se o host desconectar (fechar aba, sair), a sala é deletada automaticamente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomSessionService {

    private static final String SESSION_KEY_PREFIX = "room-session:";
    private static final Duration SESSION_TTL = Duration.ofMinutes(10);

    private final RoomRepository roomRepository;
    private final LobbySSEService lobbySSEService;
    private final GameDisconnectService gameDisconnectService;
    private final StringRedisTemplate redisTemplate;

    /**
     * Registra a sessão WS do host para tracking de desconexão.
     * Chamado quando o host envia /app/room/{roomId}/register.
     */
    public void registerHostSession(String sessionId, UUID roomId, UUID userId) {
        var roomOpt = roomRepository.findById(roomId);
        if (roomOpt.isEmpty()) {
            log.debug("Sala {} não encontrada para register de sessão", roomId);
            return;
        }

        Room room = roomOpt.get();
        if (!room.isHost(userId)) {
            log.debug("Usuário {} não é host da sala {}, ignorando register", userId, roomId);
            return;
        }

        if (room.getStatus() != RoomStatus.WAITING) {
            log.debug("Sala {} não está em WAITING (status={}), ignorando register", roomId, room.getStatus());
            return;
        }

        redisTemplate.opsForValue().set(SESSION_KEY_PREFIX + sessionId, roomId.toString(), SESSION_TTL);
        log.info("Host {} registrado na sala {} (session={})", userId, roomId, sessionId);
    }

    /**
     * Chamado quando uma sessão WebSocket desconecta.
     * Se a sessão pertencia a um host de sala WAITING, deleta a sala.
     * Se a sala já avançou (FULL) e tem um game associado, propaga o disconnect para o game.
     */
    @Transactional
    public void handleDisconnect(String sessionId) {
        String roomIdStr = redisTemplate.opsForValue().getAndDelete(SESSION_KEY_PREFIX + sessionId);
        if (roomIdStr == null) {
            return; // Sessão não era de um host registrado em sala
        }

        UUID roomId = UUID.fromString(roomIdStr);

        var roomOpt = roomRepository.findByIdForUpdate(roomId);
        if (roomOpt.isEmpty()) {
            log.debug("Sala {} já foi removida, ignorando disconnect", roomId);
            return;
        }

        Room room = roomOpt.get();

        // Se a sala já tem gameId, o jogador está migrando para a tela de game.
        // O GameDisconnectService trata a desconexão real via WebSocketDisconnectListener.
        if (room.getGameId() != null) {
            log.debug("Sala {} já tem game {}, ignorando disconnect do room-register (migração normal)",
                    roomId, room.getGameId());
            return;
        }

        // Sala sem game — host desconectou antes de alguém entrar → deleta a sala
        roomRepository.delete(room);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    lobbySSEService.notifyLobbyUpdated();
                }
            });
        } else {
            lobbySSEService.notifyLobbyUpdated();
        }
        log.info("Sala {} deletada por desconexão do host (session={})", roomId, sessionId);
    }

    /**
     * Remove o registro de sessão para uma sala específica.
     * Chamado quando a sala é deletada por outros motivos (ex: substituição, cleanup).
     *
     * Usa scan para encontrar sessões associadas a essa sala.
     * Em produção com poucas salas simultâneas, o custo é mínimo.
     */
    public void unregisterRoom(UUID roomId) {
        var scanOptions = org.springframework.data.redis.core.ScanOptions.scanOptions()
                .match(SESSION_KEY_PREFIX + "*").count(100).build();

        try (var cursor = redisTemplate.scan(scanOptions)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                String value = redisTemplate.opsForValue().get(key);
                if (roomId.toString().equals(value)) {
                    redisTemplate.delete(key);
                }
            }
        }
    }
}
