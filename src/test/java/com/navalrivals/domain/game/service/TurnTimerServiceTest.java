package com.navalrivals.domain.game.service;

import com.navalrivals.domain.game.entity.Game;
import com.navalrivals.domain.game.enums.GameMode;
import com.navalrivals.domain.game.enums.GameStatus;
import com.navalrivals.domain.game.storage.GameStorage;
import com.navalrivals.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TurnTimerServiceTest {

    @Mock
    private GameEventPublisher eventPublisher;

    @Mock
    private GameStorage storage;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private TurnTimerService turnTimerService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        turnTimerService = new TurnTimerService(60, eventPublisher, storage, redisTemplate);
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
        ReflectionTestUtils.setField(game, "status", GameStatus.IN_PROGRESS);

        return game;
    }

    // ======================== startTimer ========================

    @Test
    @DisplayName("startTimer - deve salvar deadline no Redis")
    void startTimer_shouldSaveDeadlineInRedis() {
        UUID gameId = UUID.randomUUID();

        turnTimerService.startTimer(gameId);

        verify(valueOps).set(eq("turn-deadline:" + gameId), anyString(), any(Duration.class));
    }

    // ======================== cancelTimer ========================

    @Test
    @DisplayName("cancelTimer - deve remover deadline e pausa do Redis")
    void cancelTimer_shouldDeleteKeysFromRedis() {
        UUID gameId = UUID.randomUUID();
        when(redisTemplate.delete(anyString())).thenReturn(true);

        turnTimerService.cancelTimer(gameId);

        verify(redisTemplate).delete("turn-deadline:" + gameId);
        verify(redisTemplate).delete("turn-paused:" + gameId);
    }

    // ======================== pauseTimer ========================

    @Test
    @DisplayName("pauseTimer - deve calcular tempo restante e salvar no Redis")
    void pauseTimer_shouldStoreRemainingTime() {
        UUID gameId = UUID.randomUUID();
        Instant futureDeadline = Instant.now().plusSeconds(30);

        when(valueOps.getAndDelete("turn-deadline:" + gameId)).thenReturn(futureDeadline.toString());

        turnTimerService.pauseTimer(gameId);

        verify(valueOps).set(eq("turn-paused:" + gameId), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("pauseTimer - sem deadline ativa é noop")
    void pauseTimer_noDeadline_shouldDoNothing() {
        UUID gameId = UUID.randomUUID();

        when(valueOps.getAndDelete("turn-deadline:" + gameId)).thenReturn(null);

        turnTimerService.pauseTimer(gameId);

        verify(valueOps, never()).set(contains("turn-paused:"), anyString(), any(Duration.class));
    }

    // ======================== resumeTimer ========================

    @Test
    @DisplayName("resumeTimer - com timer pausado deve criar nova deadline com tempo restante")
    void resumeTimer_withPausedTimer_shouldSetNewDeadline() {
        UUID gameId = UUID.randomUUID();

        when(valueOps.getAndDelete("turn-paused:" + gameId)).thenReturn("30000"); // 30s restantes

        turnTimerService.resumeTimer(gameId);

        // Deve salvar nova deadline (não chamar cancelTimer antes, pois resume já limpa pausa)
        verify(valueOps).set(eq("turn-deadline:" + gameId), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("resumeTimer - sem timer pausado deve iniciar do zero")
    void resumeTimer_withoutPausedTimer_shouldStartFresh() {
        UUID gameId = UUID.randomUUID();
        when(redisTemplate.delete(anyString())).thenReturn(true);

        when(valueOps.getAndDelete("turn-paused:" + gameId)).thenReturn(null);

        turnTimerService.resumeTimer(gameId);

        // Deve ter chamado startTimer que salva deadline
        verify(valueOps).set(eq("turn-deadline:" + gameId), anyString(), any(Duration.class));
    }

    // ======================== checkExpiredDeadlines ========================

    @Test
    @DisplayName("checkExpiredDeadlines - deadline expirada deve tentar processar timeout")
    void checkExpiredDeadlines_expiredDeadline_shouldProcessTimeout() {
        UUID gameId = UUID.randomUUID();
        String key = "turn-deadline:" + gameId;
        Instant expiredDeadline = Instant.now().minusSeconds(5);

        Set<String> keys = new HashSet<>();
        keys.add(key);
        when(redisTemplate.keys("turn-deadline:*")).thenReturn(keys);
        when(valueOps.get(key)).thenReturn(expiredDeadline.toString());
        when(valueOps.setIfAbsent(eq("turn-lock:" + gameId), eq("1"), any(Duration.class))).thenReturn(true);
        when(redisTemplate.delete("turn-deadline:" + gameId)).thenReturn(true);
        when(redisTemplate.delete("turn-lock:" + gameId)).thenReturn(true);

        Game game = createGameInProgress();
        when(storage.findById(gameId)).thenReturn(Optional.of(game));

        turnTimerService.checkExpiredDeadlines();

        verify(eventPublisher).publishTurnTimeout(eq(gameId), any(), any());
        verify(eventPublisher).publishTurnChange(eq(gameId), any(), eq(60));
    }

    @Test
    @DisplayName("checkExpiredDeadlines - deadline não expirada não deve processar")
    void checkExpiredDeadlines_futureDeadline_shouldNotProcess() {
        UUID gameId = UUID.randomUUID();
        String key = "turn-deadline:" + gameId;
        Instant futureDeadline = Instant.now().plusSeconds(30);

        Set<String> keys = new HashSet<>();
        keys.add(key);
        when(redisTemplate.keys("turn-deadline:*")).thenReturn(keys);
        when(valueOps.get(key)).thenReturn(futureDeadline.toString());

        turnTimerService.checkExpiredDeadlines();

        verify(eventPublisher, never()).publishTurnTimeout(any(), any(), any());
    }

    @Test
    @DisplayName("checkExpiredDeadlines - lock não adquirido (outra instância processando) é noop")
    void checkExpiredDeadlines_lockNotAcquired_shouldSkip() {
        UUID gameId = UUID.randomUUID();
        String key = "turn-deadline:" + gameId;
        Instant expiredDeadline = Instant.now().minusSeconds(5);

        Set<String> keys = new HashSet<>();
        keys.add(key);
        when(redisTemplate.keys("turn-deadline:*")).thenReturn(keys);
        when(valueOps.get(key)).thenReturn(expiredDeadline.toString());
        when(valueOps.setIfAbsent(eq("turn-lock:" + gameId), eq("1"), any(Duration.class))).thenReturn(false);

        turnTimerService.checkExpiredDeadlines();

        verify(eventPublisher, never()).publishTurnTimeout(any(), any(), any());
    }

    // ======================== handleTimeout ========================

    @Test
    @DisplayName("handleTimeout - deve trocar turno e publicar eventos quando jogo está IN_PROGRESS")
    void handleTimeout_shouldSwapTurnAndPublishEvents() {
        UUID gameId = UUID.randomUUID();
        Game game = createGameInProgress();
        UUID originalTurn = game.getCurrentTurn();

        when(storage.findById(gameId)).thenReturn(Optional.of(game));

        ReflectionTestUtils.invokeMethod(turnTimerService, "handleTimeout", gameId);

        UUID newTurn = game.getCurrentTurn();
        assertNotEquals(originalTurn, newTurn);

        verify(eventPublisher).publishTurnTimeout(gameId, originalTurn, newTurn);
        verify(eventPublisher).publishTurnChange(gameId, newTurn, 60);
    }

    @Test
    @DisplayName("handleTimeout - jogo não encontrado é noop")
    void handleTimeout_gameNotFound_shouldDoNothing() {
        UUID gameId = UUID.randomUUID();

        when(storage.findById(gameId)).thenReturn(Optional.empty());

        ReflectionTestUtils.invokeMethod(turnTimerService, "handleTimeout", gameId);

        verify(eventPublisher, never()).publishTurnTimeout(any(), any(), any());
    }

    @Test
    @DisplayName("handleTimeout - jogo FINISHED é noop")
    void handleTimeout_gameFinished_shouldDoNothing() {
        UUID gameId = UUID.randomUUID();
        Game game = createGameInProgress();
        game.finish(game.getCurrentTurn());

        when(storage.findById(gameId)).thenReturn(Optional.of(game));

        ReflectionTestUtils.invokeMethod(turnTimerService, "handleTimeout", gameId);

        verify(eventPublisher, never()).publishTurnTimeout(any(), any(), any());
    }

    // ======================== getTurnTimeout ========================

    @Test
    @DisplayName("getTurnTimeout - deve retornar 60 segundos")
    void getTurnTimeout_shouldReturn60() {
        assertEquals(60, turnTimerService.getTurnTimeout());
    }
}
