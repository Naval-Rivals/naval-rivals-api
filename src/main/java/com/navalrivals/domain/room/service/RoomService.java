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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomWebSocketService roomWebSocketService;
    private final GameService gameService;

    private static final String CODE_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 4;
    private static final String CODE_PREFIX = "NR-";
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public RoomResponse create(User host, CreateRoomRequest request) {
        GameMode gameMode = request != null && request.gameMode() != null
                ? request.gameMode()
                : GameMode.CLASSIC;
        String code = generateUniqueCode();
        var room = new Room(host, code, gameMode);
        roomRepository.save(room);
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

        roomWebSocketService.notifyPlayerLeft(room.getId(), user.getId(), user.getNickname());

        if (room.isHost(user.getId())) {
            // Se a sala tem um jogo ativo, limpa da memória
            if (room.getGameId() != null) {
                gameService.removeGame(room.getGameId());
            }
            roomRepository.delete(room);
        } else {
            // Se a sala tem um jogo ativo, limpa da memória
            if (room.getGameId() != null) {
                gameService.removeGame(room.getGameId());
                room.setGameId(null);
            }
            room.setOpponent(null);
            room.setStatus(RoomStatus.WAITING);
        }
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
