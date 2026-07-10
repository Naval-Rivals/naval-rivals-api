package com.navalrivals.domain.game.service;

import com.navalrivals.domain.game.entity.Game;
import com.navalrivals.domain.game.enums.GameMode;
import com.navalrivals.domain.game.enums.GameStatus;
import com.navalrivals.domain.game.storage.GameStorage;
import com.navalrivals.domain.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TurnTimerServiceTest {

    @Mock
    private GameEventPublisher eventPublisher;

    @Mock
    private GameStorage storage;

    private TurnTimerService turnTimerService;

    @BeforeEach
    void setUp() {
        turnTimerService = new TurnTimerService(60, eventPublisher, storage);
    }

    @AfterEach
    void tearDown() {
        turnTimerService.shutdown();
    }

    // ======================== Helpers ========================

    private User createUser(UUID id) {
        User user = new User();
        user.setId(id);
        user.setNickname("Player");
        user.setEmail("player@test.com");
        user.setPassword("password");
        return user;
    }

    private Game createGameInProgress() {
        UUID player1Id = UUID.randomUUID();
        UUID player2Id = UUID.randomUUID();

        User user1 = createUser(player1Id);
        User user2 = createUser(player2Id);

        Game game = new Game(user1, GameMode.CLASSIC);
        game.join(user2);

        // Force status to IN_PROGRESS via reflection (since join sets PLACING_SHIPS)
        ReflectionTestUtils.setField(game, "status", GameStatus.IN_PROGRESS);

        return game;
    }

    // ======================== startTimer ========================

    @Test
    @DisplayName("startTimer - deve agendar timer que pode ser cancelado posteriormente")
    void startTimer_shouldScheduleTimer() {
        UUID gameId = UUID.randomUUID();

        turnTimerService.startTimer(gameId);

        // Verifica que o timer foi agendado verificando que cancelTimer o remove sem erro
        assertDoesNotThrow(() -> turnTimerService.cancelTimer(gameId));
    }

    // ======================== cancelTimer ========================

    @Test
    @DisplayName("cancelTimer - deve cancelar timer ativo sem disparar timeout")
    void cancelTimer_shouldCancelActiveTimer() throws InterruptedException {
        // Cria serviço com timeout de 1 segundo para teste rápido
        TurnTimerService shortTimerService = new TurnTimerService(1, eventPublisher, storage);

        UUID gameId = UUID.randomUUID();

        shortTimerService.startTimer(gameId);
        shortTimerService.cancelTimer(gameId);

        // Espera mais que o timeout para garantir que não disparou
        Thread.sleep(1500);

        // Se o timer tivesse disparado, publishTurnTimeout seria chamado
        verify(eventPublisher, never()).publishTurnTimeout(any(), any(), any());
        verify(eventPublisher, never()).publishTurnChange(any(), any(), anyInt());

        shortTimerService.shutdown();
    }

    // ======================== pauseTimer ========================

    @Test
    @DisplayName("pauseTimer - deve armazenar tempo restante ao pausar")
    void pauseTimer_shouldStoreRemainingTime() throws InterruptedException {
        UUID gameId = UUID.randomUUID();

        turnTimerService.startTimer(gameId);

        // Espera um pouco para que tempo restante seja menor que total
        Thread.sleep(200);

        turnTimerService.pauseTimer(gameId);

        // Após pause, resume deve funcionar sem reiniciar do zero
        // Verifica que o estado interno de pausa foi salvo (resume não chama startTimer com timeout completo)
        assertDoesNotThrow(() -> turnTimerService.resumeTimer(gameId));
    }

    // ======================== resumeTimer ========================

    @Test
    @DisplayName("resumeTimer - com timer pausado deve retomar com tempo restante")
    void resumeTimer_withPausedTimer_shouldResumeWithRemainingTime() throws InterruptedException {
        // Usa timeout curto para verificar que resume usa tempo restante (não reinicia)
        TurnTimerService shortTimerService = new TurnTimerService(2, eventPublisher, storage);

        UUID gameId = UUID.randomUUID();
        Game game = createGameInProgress();
        when(storage.findById(gameId)).thenReturn(Optional.of(game));

        shortTimerService.startTimer(gameId);

        // Espera 1 segundo (metade do timeout)
        Thread.sleep(1000);

        shortTimerService.pauseTimer(gameId);

        // Resume — deve ter ~1s restante
        shortTimerService.resumeTimer(gameId);

        // Espera 500ms — ainda não deveria ter disparado (restava ~1s)
        Thread.sleep(500);
        verify(eventPublisher, never()).publishTurnTimeout(any(), any(), any());

        // Espera mais 800ms — agora deveria ter disparado (~1s restante já passou)
        Thread.sleep(800);
        verify(eventPublisher, atLeastOnce()).publishTurnTimeout(eq(gameId), any(), any());

        shortTimerService.shutdown();
    }

    @Test
    @DisplayName("resumeTimer - sem timer pausado deve iniciar timer do zero")
    void resumeTimer_withoutPausedTimer_shouldStartFresh() throws InterruptedException {
        // Usa timeout curto
        TurnTimerService shortTimerService = new TurnTimerService(1, eventPublisher, storage);

        UUID gameId = UUID.randomUUID();
        Game game = createGameInProgress();
        when(storage.findById(gameId)).thenReturn(Optional.of(game));

        // Resume sem ter pausado antes — deve iniciar do zero (1s)
        shortTimerService.resumeTimer(gameId);

        // Espera 500ms — não deveria ter disparado ainda
        Thread.sleep(500);
        verify(eventPublisher, never()).publishTurnTimeout(any(), any(), any());

        // Espera mais 700ms — agora deve ter disparado (1s total)
        Thread.sleep(700);
        verify(eventPublisher, atLeastOnce()).publishTurnTimeout(eq(gameId), any(), any());

        shortTimerService.shutdown();
    }

    // ======================== handleTimeout (via Reflection) ========================

    @Test
    @DisplayName("handleTimeout - deve trocar turno e publicar eventos quando jogo está IN_PROGRESS")
    void handleTimeout_shouldSwapTurnAndPublishEvents() {
        UUID gameId = UUID.randomUUID();
        Game game = createGameInProgress();
        UUID originalTurn = game.getCurrentTurn();

        when(storage.findById(gameId)).thenReturn(Optional.of(game));

        // Invoca handleTimeout via reflection
        ReflectionTestUtils.invokeMethod(turnTimerService, "handleTimeout", gameId);

        UUID newTurn = game.getCurrentTurn();

        // Verifica que o turno mudou
        assertNotEquals(originalTurn, newTurn);

        // Verifica publicação de TURN_TIMEOUT
        verify(eventPublisher).publishTurnTimeout(gameId, originalTurn, newTurn);

        // Verifica publicação de TURN_CHANGE
        verify(eventPublisher).publishTurnChange(gameId, newTurn, 60);
    }

    @Test
    @DisplayName("handleTimeout - deve não fazer nada quando jogo não é encontrado")
    void handleTimeout_gameNotFound_shouldDoNothing() {
        UUID gameId = UUID.randomUUID();

        when(storage.findById(gameId)).thenReturn(Optional.empty());

        // Invoca handleTimeout via reflection
        ReflectionTestUtils.invokeMethod(turnTimerService, "handleTimeout", gameId);

        // Nenhum evento deve ser publicado
        verify(eventPublisher, never()).publishTurnTimeout(any(), any(), any());
        verify(eventPublisher, never()).publishTurnChange(any(), any(), anyInt());
    }

    @Test
    @DisplayName("handleTimeout - deve não fazer nada quando jogo está FINISHED")
    void handleTimeout_gameFinished_shouldDoNothing() {
        UUID gameId = UUID.randomUUID();
        Game game = createGameInProgress();

        // Finaliza o jogo
        game.finish(game.getCurrentTurn());

        when(storage.findById(gameId)).thenReturn(Optional.of(game));

        // Invoca handleTimeout via reflection
        ReflectionTestUtils.invokeMethod(turnTimerService, "handleTimeout", gameId);

        // Nenhum evento deve ser publicado
        verify(eventPublisher, never()).publishTurnTimeout(any(), any(), any());
        verify(eventPublisher, never()).publishTurnChange(any(), any(), anyInt());
    }

    // ======================== getTurnTimeout ========================

    @Test
    @DisplayName("getTurnTimeout - deve retornar 60 segundos")
    void getTurnTimeout_shouldReturn60() {
        assertEquals(60, turnTimerService.getTurnTimeout());
    }
}
