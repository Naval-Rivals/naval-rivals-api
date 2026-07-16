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

    private GameDisconnectService disconnectService;

    private UUID gameId;
    private UUID player1Id;
    private UUID player2Id;
    private Game game;

    @BeforeEach
    void setUp() {
        disconnectService = new GameDisconnectService(
                30, eventPublisher, gameStorage, turnTimerService,
                gameService, gameResultService, roomRepository,
                roomWebSocketService, userRepository, lobbySSEService
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
    @DisplayName("registerSession - registra sessão e handleDisconnect consegue encontrá-la")
    void registerSession_shouldMapSessionToPlayerAndGame() {
        disconnectService.registerSession("session-1", gameId, player1Id);

        when(gameStorage.findById(gameId)).thenReturn(Optional.of(game));

        disconnectService.handleDisconnect("session-1");

        verify(turnTimerService).pauseTimer(gameId);
        verify(eventPublisher).publishOpponentDisconnected(gameId, player1Id, 30);
    }

    // ==================== handleDisconnect ====================

    @Test
    @DisplayName("handleDisconnect - sessão não registrada é noop")
    void handleDisconnect_unregisteredSession_shouldDoNothing() {
        disconnectService.handleDisconnect("unknown-session");

        verifyNoInteractions(gameStorage, turnTimerService, eventPublisher);
    }

    @Test
    @DisplayName("handleDisconnect - jogo não encontrado é noop")
    void handleDisconnect_gameNotFound_shouldDoNothing() {
        disconnectService.registerSession("session-1", gameId, player1Id);
        when(gameStorage.findById(gameId)).thenReturn(Optional.empty());

        disconnectService.handleDisconnect("session-1");

        verifyNoInteractions(turnTimerService, eventPublisher);
    }

    @Test
    @DisplayName("handleDisconnect - jogo FINISHED é noop")
    void handleDisconnect_gameFinished_shouldDoNothing() {
        game.finish(player1Id); // muda status para FINISHED

        disconnectService.registerSession("session-1", gameId, player2Id);
        when(gameStorage.findById(gameId)).thenReturn(Optional.of(game));

        disconnectService.handleDisconnect("session-1");

        verifyNoInteractions(turnTimerService, eventPublisher);
    }

    @Test
    @DisplayName("handleDisconnect - jogo IN_PROGRESS pausa timer e publica OPPONENT_DISCONNECTED")
    void handleDisconnect_gameInProgress_shouldPauseTimerAndPublishDisconnect() {
        disconnectService.registerSession("session-1", gameId, player1Id);
        when(gameStorage.findById(gameId)).thenReturn(Optional.of(game));

        disconnectService.handleDisconnect("session-1");

        verify(turnTimerService).pauseTimer(gameId);
        verify(eventPublisher).publishOpponentDisconnected(gameId, player1Id, 30);
    }

    // ==================== handleReconnect ====================

    @Test
    @DisplayName("handleReconnect - cancela timeout, publica OPPONENT_RECONNECTED e retoma timer")
    void handleReconnect_withPendingTimeout_shouldCancelAndResumeTimer() {
        disconnectService.registerSession("session-1", gameId, player1Id);
        when(gameStorage.findById(gameId)).thenReturn(Optional.of(game));

        disconnectService.handleDisconnect("session-1");

        // Reconecta com nova sessão
        disconnectService.handleReconnect("session-2", gameId, player1Id);

        verify(eventPublisher).publishOpponentReconnected(gameId, player1Id);
        verify(turnTimerService).resumeTimer(gameId);
    }

    @Test
    @DisplayName("handleReconnect - sem timeout pendente registra sessão normalmente")
    void handleReconnect_withoutPendingTimeout_shouldJustRegisterSession() {
        disconnectService.handleReconnect("session-1", gameId, player1Id);

        // Sem timeout pendente, não publica reconnected
        verify(eventPublisher, never()).publishOpponentReconnected(any(), any());
        verify(turnTimerService, never()).resumeTimer(any());
    }

    // ==================== timeout (handleReconnectTimeout via scheduler) ====================

    @Test
    @DisplayName("handleReconnectTimeout - após timeout, finaliza jogo e publica GAME_OVER")
    void handleReconnectTimeout_shouldFinishGameAndPublishGameOver() throws InterruptedException {
        // Usa timeout curto (1s) para testar expiração
        disconnectService.shutdown();
        disconnectService = new GameDisconnectService(
                1, eventPublisher, gameStorage, turnTimerService,
                gameService, gameResultService, roomRepository,
                roomWebSocketService, userRepository, lobbySSEService
        );

        disconnectService.registerSession("session-1", gameId, player1Id);
        when(gameStorage.findById(gameId)).thenReturn(Optional.of(game));

        disconnectService.handleDisconnect("session-1");

        // Espera timeout expirar
        Thread.sleep(1500);

        // Verifica que o jogo foi finalizado com player2 como vencedor (player1 desconectou)
        verify(gameResultService).persistGameResult(game);
        verify(eventPublisher).publishGameOver(gameId, player2Id, player1Id, "OPPONENT_DISCONNECTED");
        verify(turnTimerService).cancelTimer(gameId);
        verify(gameService).removeGame(gameId);
    }

    @Test
    @DisplayName("handleReconnectTimeout - jogo FINISHED no momento do timeout é noop")
    void handleReconnectTimeout_gameAlreadyFinished_shouldDoNothing() throws InterruptedException {
        disconnectService.shutdown();
        disconnectService = new GameDisconnectService(
                1, eventPublisher, gameStorage, turnTimerService,
                gameService, gameResultService, roomRepository,
                roomWebSocketService, userRepository, lobbySSEService
        );

        disconnectService.registerSession("session-1", gameId, player1Id);

        // Primeira chamada (handleDisconnect) → IN_PROGRESS
        // Segunda chamada (timeout) → FINISHED
        when(gameStorage.findById(gameId))
                .thenReturn(Optional.of(game))  // handleDisconnect
                .thenAnswer(inv -> {
                    game.finish(player2Id);      // simula que jogo terminou entre disconnect e timeout
                    return Optional.of(game);
                });

        disconnectService.handleDisconnect("session-1");

        Thread.sleep(1500);

        verify(gameResultService, never()).persistGameResult(any());
        verify(eventPublisher, never()).publishGameOver(any(), any(), any(), eq("OPPONENT_DISCONNECTED"));
    }

    // ==================== cleanupGame ====================

    @Test
    @DisplayName("cleanupGame - remove sessões e cancela timers do jogo")
    void cleanupGame_shouldRemoveSessionsAndCancelTimers() {
        disconnectService.registerSession("session-1", gameId, player1Id);
        disconnectService.registerSession("session-2", gameId, player2Id);

        when(gameStorage.findById(gameId)).thenReturn(Optional.of(game));

        disconnectService.cleanupGame(gameId);

        // Após cleanup, handleDisconnect com sessões antigas deve ser noop
        disconnectService.handleDisconnect("session-1");
        disconnectService.handleDisconnect("session-2");

        // pauseTimer não deve ser chamado pois as sessões foram removidas
        verify(turnTimerService, never()).pauseTimer(any());
    }

    @Test
    @DisplayName("cleanupGame - cancela timer de reconexão pendente")
    void cleanupGame_shouldCancelPendingReconnectTimers() throws InterruptedException {
        disconnectService.shutdown();
        disconnectService = new GameDisconnectService(
                2, eventPublisher, gameStorage, turnTimerService,
                gameService, gameResultService, roomRepository,
                roomWebSocketService, userRepository, lobbySSEService
        );

        disconnectService.registerSession("session-1", gameId, player1Id);
        when(gameStorage.findById(gameId)).thenReturn(Optional.of(game));

        // Desconecta para iniciar timer de reconexão
        disconnectService.handleDisconnect("session-1");

        // Limpa o jogo (cancela o timer pendente)
        disconnectService.cleanupGame(gameId);

        // Espera mais que o timeout
        Thread.sleep(2500);

        // O timeout não deve ter sido executado (persistGameResult não deve ser chamado)
        verify(gameResultService, never()).persistGameResult(any());
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

        // Avançar para IN_PROGRESS colocando navios (simulado via reflexão)
        setGameStatusInProgress(realGame);

        return realGame;
    }

    /**
     * Usa reflexão para forçar o status do jogo para IN_PROGRESS
     * sem precisar posicionar navios.
     */
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
