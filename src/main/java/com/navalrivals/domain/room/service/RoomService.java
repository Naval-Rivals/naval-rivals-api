package com.navalrivals.domain.room.service;

import com.navalrivals.domain.game.enums.GameMode;
import com.navalrivals.domain.game.service.GameService;
import com.navalrivals.domain.room.dto.CreateRoomRequest;
import com.navalrivals.domain.room.dto.JoinRoomRequest;
import com.navalrivals.domain.room.dto.RoomResponse;
import com.navalrivals.domain.room.entity.Room;
import com.navalrivals.domain.room.enums.RoomStatus;
import com.navalrivals.domain.room.repository.RoomRepository;
import com.navalrivals.domain.user.entity.User;
import com.navalrivals.infra.exception.exceptions.NotFoundException;
import com.navalrivals.infra.exception.exceptions.PlayerWithoutPermissionException;
import com.navalrivals.infra.exception.exceptions.RoomFullException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomWebSocketService roomWebSocketService;
    private final RoomSessionService roomSessionService;
    private final GameService gameService;
    private final LobbySSEService lobbySSEService;

    private static final String CODE_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 4;
    private static final String CODE_PREFIX = "NR-";
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public RoomResponse create(User host, CreateRoomRequest request) {
        // Se o host já tem uma sala WAITING, deleta a anterior
        roomRepository.findByHostIdAndStatus(host.getId(), RoomStatus.WAITING)
                .ifPresent(existingRoom -> {
                    log.info("[ROOM] Sala anterior deletada — roomId={}, hostId={}", existingRoom.getId(), host.getId());
                    roomSessionService.unregisterRoom(existingRoom.getId());
                    roomRepository.delete(existingRoom);
                });

        GameMode gameMode = request != null && request.gameMode() != null
                ? request.gameMode()
                : GameMode.CLASSIC;
        String code = generateUniqueCode();
        var room = new Room(host, code, gameMode);
        roomRepository.save(room);
        lobbySSEService.notifyLobbyUpdated();
        log.info("[ROOM] Sala criada — roomId={}, code={}, hostId={}, mode={}", room.getId(), code, host.getId(), gameMode);
        return new RoomResponse(room);
    }

    @Transactional
    public RoomResponse joinByCode(JoinRoomRequest request, User player) {
        var room = roomRepository.findByCodeForUpdate(request.code().toUpperCase())
                .orElseThrow(() -> new NotFoundException("Sala não encontrada"));

        if (room.isHost(player.getId())) {
            throw new PlayerWithoutPermissionException("Não pode entrar na própria sala");
        }

        if (room.isFull()) {
            throw new RoomFullException("Sala já está cheia");
        }

        if (room.getGameId() != null) {
            throw new RoomFullException("Partida já foi criada para esta sala");
        }

        room.setOpponent(player);
        room.setStatus(RoomStatus.FULL);

        var game = gameService.createGame(room.getHost(), room.getGameMode());
        gameService.joinGame(game.getId(), player);
        room.setGameId(game.getId());

        roomWebSocketService.notifyPlayerJoined(room.getId(), player.getId(), player.getNickname());
        roomWebSocketService.notifyRoomReady(room.getId(), player.getId(), player.getNickname(), game.getId());
        lobbySSEService.notifyLobbyUpdated();

        log.info("[ROOM] Jogador entrou na sala — roomId={}, playerId={}, code={}, gameId={}", room.getId(), player.getId(), room.getCode(), game.getId());
        return new RoomResponse(room);
    }

    public RoomResponse getById(UUID roomId) {
        var room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Sala não encontrada"));
        return new RoomResponse(room);
    }

    @Transactional
    public void leave(UUID roomId, User user) {
        var room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Sala não encontrada"));

        if (!room.isParticipant(user.getId())) {
            throw new PlayerWithoutPermissionException("Jogador não pertence a essa sala");
        }

        log.info("[ROOM] Jogador saindo da sala — roomId={}, playerId={}, isHost={}", roomId, user.getId(), room.isHost(user.getId()));

        roomWebSocketService.notifyPlayerLeft(room.getId(), user.getId(), user.getNickname());

        if (room.isHost(user.getId())) {
            if (room.getGameId() != null) {
                if (!gameService.forfeitGame(room.getGameId(), user.getId())) {
                    gameService.removeGame(room.getGameId());
                }
            }
            roomRepository.delete(room);
            log.info("[ROOM] Sala deletada (host saiu) — roomId={}", roomId);
        } else {
            if (room.getGameId() != null) {
                if (!gameService.forfeitGame(room.getGameId(), user.getId())) {
                    gameService.removeGame(room.getGameId());
                }
                roomRepository.delete(room);
                log.info("[ROOM] Sala deletada (oponente saiu com game ativo) — roomId={}", roomId);
            } else {
                room.setOpponent(null);
                room.setStatus(RoomStatus.WAITING);
                log.info("[ROOM] Oponente saiu (sala voltou a WAITING) — roomId={}", roomId);
            }
        }

        lobbySSEService.notifyLobbyUpdated();
    }

    public List<RoomResponse> listWaitingRooms() {
        return roomRepository.findByStatusOrderByCreatedAtDesc(RoomStatus.WAITING).stream()
                .map(RoomResponse::new)
                .toList();
    }

    private String generateUniqueCode() {
        String code;
        do {
            code = CODE_PREFIX + generateRandomPart();
        } while (roomRepository.existsByCode(code));
        return code;
    }

    private String generateRandomPart() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARACTERS.charAt(random.nextInt(CODE_CHARACTERS.length())));
        }
        return sb.toString();
    }
}
