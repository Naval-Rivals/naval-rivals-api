package com.navalrivals.domain.room.service;

import com.navalrivals.domain.room.entity.Room;
import com.navalrivals.domain.room.enums.RoomStatus;
import com.navalrivals.domain.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerencia o tracking de sessões WebSocket de hosts na tela de espera.
 *
 * Quando o host cria uma sala e se inscreve no tópico da sala,
 * ele envia /app/room/{roomId}/register para registrar sua sessão.
 * Se o host desconectar (fechar aba, sair), a sala é deletada automaticamente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomSessionService {

    private final RoomRepository roomRepository;
    private final RoomWebSocketService roomWebSocketService;

    /**
     * Mapeia sessionId → roomId do host registrado naquela sessão.
     */
    private final Map<String, UUID> sessionToRoom = new ConcurrentHashMap<>();

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

        sessionToRoom.put(sessionId, roomId);
        log.info("Host {} registrado na sala {} (session={})", userId, roomId, sessionId);
    }

    /**
     * Chamado quando uma sessão WebSocket desconecta.
     * Se a sessão pertencia a um host de sala WAITING, deleta a sala.
     */
    @Transactional
    public void handleDisconnect(String sessionId) {
        UUID roomId = sessionToRoom.remove(sessionId);
        if (roomId == null) {
            return; // Sessão não era de um host registrado em sala
        }

        var roomOpt = roomRepository.findById(roomId);
        if (roomOpt.isEmpty()) {
            log.debug("Sala {} já foi removida, ignorando disconnect", roomId);
            return;
        }

        Room room = roomOpt.get();

        // Só deleta se a sala ainda está WAITING (se já avançou para FULL/IN_PROGRESS, o GameDisconnectService cuida)
        if (room.getStatus() != RoomStatus.WAITING) {
            log.debug("Sala {} não está mais em WAITING (status={}), ignorando disconnect de host", roomId, room.getStatus());
            return;
        }

        roomRepository.delete(room);
        roomWebSocketService.notifyLobbyUpdated();
        log.info("Sala {} deletada por desconexão do host (session={})", roomId, sessionId);
    }

    /**
     * Remove o registro de sessão para uma sala específica.
     * Chamado quando a sala é deletada por outros motivos (ex: substituição, cleanup).
     */
    public void unregisterRoom(UUID roomId) {
        sessionToRoom.entrySet().removeIf(entry -> entry.getValue().equals(roomId));
    }
}
