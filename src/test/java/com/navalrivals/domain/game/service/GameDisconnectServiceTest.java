package com.navalrivals.domain.game.service;

import com.navalrivals.domain.game.entity.Game;
import com.navalrivals.domain.game.enums.GameMode;
import com.navalrivals.domain.game.enums.GameStatus;
import com.navalrivals.domain.game.storage.GameStorage;
import com.navalrivals.domain.room.repository.RoomRepository;
import com.navalrivals.domain.room.service.LobbySSEService;
import com.navalrivals.domain.room.service.RoomWebSocketService;
import com.navalrivals.domain.user.entity.User;
import com.navalrivals.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameDisconnectServiceTest {

    @Mock
    private GameEventPublisher eventPublisher;

    @Mock
    private GameStorage gameStorage;

    @Mock
    private TurnTimerService turnTimerService;

    @Mock
    private GameService gameService;

    @Mock
    private GameResultService gameResultService;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomWebSocketService roomWebSocketService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LobbySSEService lobbySSEService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private GameDisconnectService disconnectService;

    private UUID gameId;
    private UUID player1Id;
    private UUID player2Id;
    private Game game;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);

        disconnectService = new GameDisconnectService(
                30, eventPublisher, gameStorage, turnTimerService,
                gameService, gameResultService, roomRepository,
                roomWebSocketService, userRepository, lobbySSEService,
                redisTemplate
        );

        player1Id = UUID.randomUUID();
        player2Id = UUID.randomUUID();

        game = createGameInProgress();
        gameId = game.getId();
    }

    @AfterEach
    void tearDown() {
        disconnectService.shutdown();
    }

    // ==================== registerSession ====================

    @Test
    @DisplayName("registerSession - registra sessão no Redis")
    void registerSession_shouldStoreInRedis() {
        disconnectService.registerSession("session-1", gameId, player1Id);

        verify(valueOps).set(eq("game-session:session-1"), eq(gameId + ":" + player1Id), any(Duration.class));
        verify(valueOps).set(eq("game-active-session:" + player1Id), eq("session-1"), any(Duration.class));
    }

    // ==================== handleDisconnect ====================

    @Test
    @DisplayName("handleDisconnect - sessão não registrada é noop")
    void handleDisconnect_unregisteredSession_shouldDoNothing() {
        when(valueOps.getAndDelete("game-session:unknown-session")).thenReturn(null);

        disconnectService.handleDisconnect("unknown-session");

        verifyNoInteractions(gameStorage, turnTimerService, eventPublisher);
    }

    @Test
    @DisplayName("handleDisconnect - sessão registrada agenda processamento com delay")
    void handleDisconnect_registeredSession_shouldScheduleProcessing() throws InterruptedException {
        String sessionValue = gameId + ":" + player1Id;
        when(valueOps.getAndDelete("game-session:session-1")).thenReturn(sessionValue);
        when(valueOps.get("game-active-session:" + player1Id)).thenReturn(null);
        when(gameStorage.findById(gameId)).thenReturn(Optional.of(game));
        when(redisTemplate.delete(anyString())).thenReturn(true);

        disconnectService.handleDisconnect("session-1");

        // Espera o delay de 2s + margem
        Thread.sleep(2500);

        verify(turnTimerService).pauseTimer(gameId);
        verify(eventPublisher).publishOpponentDisconnected(gameId, player1Id, 30);
    }

    @Test
    @DisplayName("handleDisconnect - sessão obsoleta (nova sessão já ativa) é ignorada após delay")
    void handleDisconnect_obsoleteSession_shouldBeIgnoredAfterDelay() throws InterruptedException {
        String sessionValue = gameId + ":" + player1Id;
        when(valueOps.getAndDelete("game-session:session-1")).thenReturn(sessionValue);
        // Nova sessão já registrada
        when(valueOps.get("game-active-session:" + player1Id)).thenReturn("session-2");

        disconnectService.handleDisconnect("session-1");

        // Espera o delay
        Thread.sleep(2500);

        verify(turnTimerService, never()).pauseTimer(any());
        verify(eventPublisher, never()).publishOpponentDisconnected(any(), any(), anyInt());
    }

    // ==================== handleReconnect ====================

    @Test
    @DisplayName("handleReconnect - com disconnect pendente cancela timeout e publica RECONNECTED")
    void handleReconnect_withPendingDisconnect_shouldCancelAndPublishReconnected() throws InterruptedException {
        // Simula disconnect
        String sessionValue = gameId + ":" + player1Id;
        when(valueOps.getAndDelete("game-session:session-1")).thenReturn(sessionValue);
        when(valueOps.get("game-active-session:" + player1Id)).thenReturn(null);
        when(gameStorage.findById(gameId)).thenReturn(Optional.of(game));
        when(redisTemplate.delete(anyString())).thenReturn(true);

        disconnectService.handleDisconnect("session-1");
        Thread.sleep(2500); // Espera processamento do disconnect

        // Simula reconexão
        when(valueOps.getAndDelete("game-disconnected:" + player1Id)).thenReturn(gameId.toString());

        disconnectService.handleReconnect("session-2", gameId, player1Id);

        verify(eventPublisher).publishOpponentReconnected(gameId, player1Id);
        verify(turnTimerService).resumeTimer(gameId);
    }

    @Test
    @DisplayName("handleReconnect - sem disconnect pendente apenas registra sessão")
    void handleReconnect_withoutPendingDisconnect_shouldJustRegister() {
        when(valueOps.getAndDelete("game-disconnected:" + player1Id)).thenReturn(null);

        disconnectService.handleReconnect("session-1", gameId, player1Id);

        verify(eventPublisher, never()).publishOpponentReconnected(any(), any());
        verify(turnTimerService, never()).resumeTimer(any());
        // Deve registrar sessão
        verify(valueOps).set(eq("game-session:session-1"), eq(gameId + ":" + player1Id), any(Duration.class));
    }

    // ==================== cleanupGame ====================

    @Test
    @DisplayName("cleanupGame - remove chaves Redis dos jogadores")
    void cleanupGame_shouldCleanupRedisKeys() {
        when(gameStorage.findById(gameId)).thenReturn(Optional.of(game));
        when(redisTemplate.delete(anyString())).thenReturn(true);

        disconnectService.cleanupGame(gameId);

        verify(redisTemplate).delete("game-active-session:" + player1Id);
        verify(redisTemplate).delete("game-disconnected:" + player1Id);
        verify(redisTemplate).delete("game-active-session:" + player2Id);
        verify(redisTemplate).delete("game-disconnected:" + player2Id);
    }

    // ==================== Helpers ====================

    private Game createGameInProgress() {
        User user1 = new User();
        user1.setId(player1Id);
        user1.setNickname("Player1");
        user1.setEmail("player1@test.com");
        user1.setPassword("password");

        User user2 = new User();
        user2.setId(player2Id);
        user2.setNickname("Player2");
        user2.setEmail("player2@test.com");
        user2.setPassword("password");

        Game realGame = new Game(user1, GameMode.CLASSIC);
        realGame.join(user2);
        setGameStatusInProgress(realGame);

        return realGame;
    }

    private void setGameStatusInProgress(Game game) {
        try {
            var statusField = Game.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(game, GameStatus.IN_PROGRESS);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao configurar status do jogo para teste", e);
        }
    }
}
