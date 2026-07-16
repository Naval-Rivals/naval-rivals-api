package com.navalrivals.domain.room.service;

import com.navalrivals.domain.game.entity.Game;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomWebSocketService roomWebSocketService;

    @Mock
    private RoomSessionService roomSessionService;

    @Mock
    private GameService gameService;

    @Mock
    private LobbySSEService lobbySSEService;

    @InjectMocks
    private RoomService roomService;

    // ======================== create ========================

    @Test
    @DisplayName("Deve criar sala com sucesso")
    void shouldCreateRoomSuccessfully() {
        User host = createUser();
        CreateRoomRequest request = new CreateRoomRequest(GameMode.TACTICAL);

        when(roomRepository.findByHostIdAndStatus(host.getId(), RoomStatus.WAITING))
                .thenReturn(Optional.empty());
        when(roomRepository.existsByCode(any())).thenReturn(false);

        RoomResponse response = roomService.create(host, request);

        assertNotNull(response);
        assertEquals(host.getId(), response.host().id());
        assertEquals(GameMode.TACTICAL, response.gameMode());
        assertEquals(RoomStatus.WAITING, response.status());
        assertNotNull(response.code());

        verify(roomRepository).save(any(Room.class));
    }

    @Test
    @DisplayName("Deve deletar sala anterior WAITING do mesmo host ao criar nova")
    void shouldDeletePreviousWaitingRoomBeforeCreatingNew() {
        User host = createUser();
        CreateRoomRequest request = new CreateRoomRequest(GameMode.CLASSIC);

        Room existingRoom = new Room(host, "NR-ABCD", GameMode.CLASSIC);
        existingRoom.setId(UUID.randomUUID());

        when(roomRepository.findByHostIdAndStatus(host.getId(), RoomStatus.WAITING))
                .thenReturn(Optional.of(existingRoom));
        when(roomRepository.existsByCode(any())).thenReturn(false);

        RoomResponse response = roomService.create(host, request);

        assertNotNull(response);
        verify(roomSessionService).unregisterRoom(existingRoom.getId());
        verify(roomRepository).delete(existingRoom);
        verify(roomRepository).save(any(Room.class));
    }

    @Test
    @DisplayName("Deve usar CLASSIC como default quando request é null")
    void shouldUseClassicAsDefaultWhenRequestIsNull() {
        User host = createUser();

        when(roomRepository.findByHostIdAndStatus(host.getId(), RoomStatus.WAITING))
                .thenReturn(Optional.empty());
        when(roomRepository.existsByCode(any())).thenReturn(false);

        RoomResponse response = roomService.create(host, null);

        assertNotNull(response);
        assertEquals(GameMode.CLASSIC, response.gameMode());

        verify(roomRepository).save(any(Room.class));
    }

    @Test
    @DisplayName("Deve usar CLASSIC como default quando gameMode do request é null")
    void shouldUseClassicAsDefaultWhenGameModeIsNull() {
        User host = createUser();
        CreateRoomRequest request = new CreateRoomRequest(null);

        when(roomRepository.findByHostIdAndStatus(host.getId(), RoomStatus.WAITING))
                .thenReturn(Optional.empty());
        when(roomRepository.existsByCode(any())).thenReturn(false);

        RoomResponse response = roomService.create(host, request);

        assertNotNull(response);
        assertEquals(GameMode.CLASSIC, response.gameMode());
    }

    // ======================== joinByCode ========================

    @Test
    @DisplayName("Deve entrar na sala com sucesso, criar game e notificar")
    void shouldJoinRoomSuccessfully() {
        User host = createUser();
        User player = createUser();
        JoinRoomRequest request = new JoinRoomRequest("NR-ABCD");

        Room room = new Room(host, "NR-ABCD", GameMode.CLASSIC);
        room.setId(UUID.randomUUID());

        Game game = mock(Game.class);
        UUID gameId = UUID.randomUUID();
        when(game.getId()).thenReturn(gameId);

        when(roomRepository.findByCodeForUpdate("NR-ABCD")).thenReturn(Optional.of(room));
        when(gameService.createGame(host, GameMode.CLASSIC)).thenReturn(game);
        when(gameService.joinGame(gameId, player)).thenReturn(game);

        RoomResponse response = roomService.joinByCode(request, player);

        assertNotNull(response);
        assertEquals(RoomStatus.FULL, response.status());
        assertEquals(player.getId(), response.opponent().id());
        assertEquals(gameId, response.gameId());

        verify(gameService).createGame(host, GameMode.CLASSIC);
        verify(gameService).joinGame(gameId, player);
        verify(roomWebSocketService).notifyPlayerJoined(room.getId(), player.getId(), player.getNickname());
        verify(roomWebSocketService).notifyRoomReady(room.getId(), player.getId(), player.getNickname(), gameId);
    }

    @Test
    @DisplayName("Deve lançar NotFoundException quando sala não encontrada pelo código")
    void shouldThrowNotFoundWhenRoomCodeDoesNotExist() {
        User player = createUser();
        JoinRoomRequest request = new JoinRoomRequest("NR-XXXX");

        when(roomRepository.findByCodeForUpdate("NR-XXXX")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> roomService.joinByCode(request, player));
    }

    @Test
    @DisplayName("Deve lançar exceção quando host tenta entrar na própria sala")
    void shouldThrowWhenHostTriesToJoinOwnRoom() {
        User host = createUser();
        JoinRoomRequest request = new JoinRoomRequest("NR-ABCD");

        Room room = new Room(host, "NR-ABCD", GameMode.CLASSIC);
        room.setId(UUID.randomUUID());

        when(roomRepository.findByCodeForUpdate("NR-ABCD")).thenReturn(Optional.of(room));

        assertThrows(PlayerWithoutPermissionException.class,
                () -> roomService.joinByCode(request, host));
    }

    @Test
    @DisplayName("Deve lançar RoomFullException quando sala está cheia")
    void shouldThrowWhenRoomIsFull() {
        User host = createUser();
        User existingOpponent = createUser();
        User newPlayer = createUser();
        JoinRoomRequest request = new JoinRoomRequest("NR-ABCD");

        Room room = new Room(host, "NR-ABCD", GameMode.CLASSIC);
        room.setId(UUID.randomUUID());
        room.setOpponent(existingOpponent);

        when(roomRepository.findByCodeForUpdate("NR-ABCD")).thenReturn(Optional.of(room));

        assertThrows(RoomFullException.class,
                () -> roomService.joinByCode(request, newPlayer));
    }

    @Test
    @DisplayName("Deve lançar RoomFullException quando partida já foi criada")
    void shouldThrowWhenGameAlreadyCreated() {
        User host = createUser();
        User player = createUser();
        JoinRoomRequest request = new JoinRoomRequest("NR-ABCD");

        Room room = new Room(host, "NR-ABCD", GameMode.CLASSIC);
        room.setId(UUID.randomUUID());
        room.setGameId(UUID.randomUUID());

        when(roomRepository.findByCodeForUpdate("NR-ABCD")).thenReturn(Optional.of(room));

        assertThrows(RoomFullException.class,
                () -> roomService.joinByCode(request, player));
    }

    // ======================== leave ========================

    @Test
    @DisplayName("Deve deletar sala quando host sai")
    void shouldDeleteRoomWhenHostLeaves() {
        User host = createUser();
        Room room = new Room(host, "NR-ABCD", GameMode.CLASSIC);
        UUID roomId = UUID.randomUUID();
        room.setId(roomId);

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        roomService.leave(roomId, host);

        verify(roomWebSocketService).notifyPlayerLeft(roomId, host.getId(), host.getNickname());
        verify(roomRepository).delete(room);
    }

    @Test
    @DisplayName("Deve deletar sala e finalizar jogo quando host sai e jogo está IN_PROGRESS")
    void shouldDeleteRoomAndForfeitGameWhenHostLeavesWithActiveGame() {
        User host = createUser();
        Room room = new Room(host, "NR-ABCD", GameMode.CLASSIC);
        UUID roomId = UUID.randomUUID();
        UUID gameId = UUID.randomUUID();
        room.setId(roomId);
        room.setGameId(gameId);

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(gameService.forfeitGame(gameId, host.getId())).thenReturn(true);

        roomService.leave(roomId, host);

        verify(gameService).forfeitGame(gameId, host.getId());
        verify(gameService, never()).removeGame(any());
        verify(roomRepository).delete(room);
    }

    @Test
    @DisplayName("Deve resetar sala para WAITING quando oponente sai antes do jogo ser criado")
    void shouldResetRoomToWaitingWhenOpponentLeavesBeforeGame() {
        User host = createUser();
        User opponent = createUser();
        Room room = new Room(host, "NR-ABCD", GameMode.CLASSIC);
        UUID roomId = UUID.randomUUID();
        room.setId(roomId);
        room.setOpponent(opponent);
        room.setStatus(RoomStatus.FULL);

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        roomService.leave(roomId, opponent);

        assertNull(room.getOpponent());
        assertEquals(RoomStatus.WAITING, room.getStatus());
        verify(roomRepository, never()).delete(any());
        verify(roomWebSocketService).notifyPlayerLeft(roomId, opponent.getId(), opponent.getNickname());
    }

    @Test
    @DisplayName("Deve deletar sala e finalizar jogo quando oponente sai e jogo está IN_PROGRESS")
    void shouldDeleteRoomAndForfeitGameWhenOpponentLeavesAfterGameStarted() {
        User host = createUser();
        User opponent = createUser();
        Room room = new Room(host, "NR-ABCD", GameMode.CLASSIC);
        UUID roomId = UUID.randomUUID();
        UUID gameId = UUID.randomUUID();
        room.setId(roomId);
        room.setOpponent(opponent);
        room.setGameId(gameId);

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(gameService.forfeitGame(gameId, opponent.getId())).thenReturn(true);

        roomService.leave(roomId, opponent);

        verify(gameService).forfeitGame(gameId, opponent.getId());
        verify(gameService, never()).removeGame(any());
        verify(roomRepository).delete(room);
        verify(roomWebSocketService).notifyPlayerLeft(roomId, opponent.getId(), opponent.getNickname());
    }

    @Test
    @DisplayName("Deve lançar exceção quando jogador não pertence à sala")
    void shouldThrowWhenPlayerDoesNotBelongToRoom() {
        User host = createUser();
        User stranger = createUser();
        Room room = new Room(host, "NR-ABCD", GameMode.CLASSIC);
        UUID roomId = UUID.randomUUID();
        room.setId(roomId);

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        assertThrows(PlayerWithoutPermissionException.class,
                () -> roomService.leave(roomId, stranger));
    }

    @Test
    @DisplayName("Deve lançar NotFoundException ao sair de sala inexistente")
    void shouldThrowNotFoundWhenLeavingNonexistentRoom() {
        UUID roomId = UUID.randomUUID();
        User user = createUser();

        when(roomRepository.findById(roomId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> roomService.leave(roomId, user));
    }

    // ======================== listWaitingRooms ========================

    @Test
    @DisplayName("Deve retornar lista de salas com status WAITING")
    void shouldReturnListOfWaitingRooms() {
        User host1 = createUser();
        User host2 = createUser();

        Room room1 = new Room(host1, "NR-AAAA", GameMode.CLASSIC);
        room1.setId(UUID.randomUUID());
        Room room2 = new Room(host2, "NR-BBBB", GameMode.TACTICAL);
        room2.setId(UUID.randomUUID());

        when(roomRepository.findByStatusOrderByCreatedAtDesc(RoomStatus.WAITING))
                .thenReturn(List.of(room1, room2));

        List<RoomResponse> result = roomService.listWaitingRooms();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("NR-AAAA", result.get(0).code());
        assertEquals("NR-BBBB", result.get(1).code());
    }

    @Test
    @DisplayName("Deve retornar lista vazia quando não há salas WAITING")
    void shouldReturnEmptyListWhenNoWaitingRooms() {
        when(roomRepository.findByStatusOrderByCreatedAtDesc(RoomStatus.WAITING))
                .thenReturn(List.of());

        List<RoomResponse> result = roomService.listWaitingRooms();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ======================== getById ========================

    @Test
    @DisplayName("Deve retornar sala por ID")
    void shouldReturnRoomById() {
        User host = createUser();
        Room room = new Room(host, "NR-ABCD", GameMode.CLASSIC);
        UUID roomId = UUID.randomUUID();
        room.setId(roomId);

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        RoomResponse response = roomService.getById(roomId);

        assertNotNull(response);
        assertEquals(roomId, response.id());
        assertEquals("NR-ABCD", response.code());
    }

    @Test
    @DisplayName("Deve lançar NotFoundException quando sala não encontrada por ID")
    void shouldThrowNotFoundWhenRoomDoesNotExist() {
        UUID roomId = UUID.randomUUID();

        when(roomRepository.findById(roomId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> roomService.getById(roomId));
    }

    // ======================== helpers ========================

    private User createUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setNickname("Player_" + user.getId().toString().substring(0, 4));
        return user;
    }
}
