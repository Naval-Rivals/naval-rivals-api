package com.navalrivals.domain.game.service;

import com.navalrivals.domain.game.entity.Game;
import com.navalrivals.domain.game.enums.GameStatus;
import com.navalrivals.domain.game.storage.GameStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Gerencia o timer de turno para cada partida usando Redis como state store.
 *
 * Abordagem distribuída:
 * - Ao iniciar um timer, salva uma chave "turn-deadline:{gameId}" com o timestamp de expiração.
 * - Um scheduler periódico (1s) verifica deadlines expiradas e processa timeouts.
 * - Usa distributed lock (SETNX) para garantir que apenas UMA instância processa cada timeout.
 * - Pausar/resumir atualiza a deadline no Redis.
 *
 * Isso funciona com múltiplas instâncias sem duplicação de processamento.
 */
@Slf4j
@Service
public class TurnTimerService {

    private static final String DEADLINE_KEY_PREFIX = "turn-deadline:";
    private static final String PAUSED_KEY_PREFIX = "turn-paused:";
    private static final String LOCK_KEY_PREFIX = "turn-lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final Duration DEADLINE_TTL = Duration.ofMinutes(5); // Safety TTL

    private final int turnTimeoutSeconds;
    private final GameEventPublisher eventPublisher;
    private final GameStorage storage;
    private final StringRedisTemplate redisTemplate;

    public TurnTimerService(
            @Value("${game.turn-timeout-seconds}") int turnTimeoutSeconds,
            GameEventPublisher eventPublisher,
            GameStorage storage,
            StringRedisTemplate redisTemplate
    ) {
        this.turnTimeoutSeconds = turnTimeoutSeconds;
        this.eventPublisher = eventPublisher;
        this.storage = storage;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Inicia (ou reinicia) o timer de turno para uma partida.
     * Salva o deadline (now + turnTimeout) no Redis.
     */
    public void startTimer(UUID gameId) {
        cancelTimer(gameId); // Remove pausa se existir
        Instant deadline = Instant.now().plusSeconds(turnTimeoutSeconds);
        redisTemplate.opsForValue().set(
                DEADLINE_KEY_PREFIX + gameId,
                deadline.toString(),
                DEADLINE_TTL
        );
        log.debug("[TIMER] Timer iniciado — gameId={}, deadline={}", gameId, deadline);
    }

    /**
     * Cancela o timer de uma partida completamente.
     */
    public void cancelTimer(UUID gameId) {
        redisTemplate.delete(DEADLINE_KEY_PREFIX + gameId);
        redisTemplate.delete(PAUSED_KEY_PREFIX + gameId);
        log.debug("[TIMER] Timer cancelado — gameId={}", gameId);
    }

    /**
     * Pausa o timer (desconexão de jogador).
     * Calcula quanto tempo restava e armazena.
     */
    public void pauseTimer(UUID gameId) {
        String deadlineStr = redisTemplate.opsForValue().getAndDelete(DEADLINE_KEY_PREFIX + gameId);
        if (deadlineStr == null) return;

        Instant deadline = Instant.parse(deadlineStr);
        long remainingMs = Duration.between(Instant.now(), deadline).toMillis();
        if (remainingMs > 0) {
            redisTemplate.opsForValue().set(
                    PAUSED_KEY_PREFIX + gameId,
                    String.valueOf(remainingMs),
                    DEADLINE_TTL
            );
            log.debug("[TIMER] Timer pausado — gameId={}, remainingMs={}", gameId, remainingMs);
        }
    }

    /**
     * Retoma o timer após reconexão, usando os milissegundos que restavam.
     */
    public void resumeTimer(UUID gameId) {
        String remainingStr = redisTemplate.opsForValue().getAndDelete(PAUSED_KEY_PREFIX + gameId);
        if (remainingStr == null) {
            // Não havia timer pausado — reinicia do zero
            startTimer(gameId);
            return;
        }

        long remainingMs = Long.parseLong(remainingStr);
        if (remainingMs <= 0) {
            startTimer(gameId);
            return;
        }

        Instant deadline = Instant.now().plusMillis(remainingMs);
        redisTemplate.opsForValue().set(
                DEADLINE_KEY_PREFIX + gameId,
                deadline.toString(),
                DEADLINE_TTL
        );
        log.debug("[TIMER] Timer retomado — gameId={}, remainingMs={}, deadline={}", gameId, remainingMs, deadline);
    }

    /**
     * Scheduler que verifica deadlines expiradas a cada 1 segundo.
     * Usa distributed lock para garantir que apenas uma instância processa cada timeout.
     */
    @Scheduled(fixedRate = 1000)
    public void checkExpiredDeadlines() {
        Set<String> keys = redisTemplate.keys(DEADLINE_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) return;

        Instant now = Instant.now();

        for (String key : keys) {
            String deadlineStr = redisTemplate.opsForValue().get(key);
            if (deadlineStr == null) continue;

            try {
                Instant deadline = Instant.parse(deadlineStr);
                if (now.isAfter(deadline)) {
                    // Deadline expirada — tentar adquirir lock para processar
                    String gameIdStr = key.replace(DEADLINE_KEY_PREFIX, "");
                    UUID gameId = UUID.fromString(gameIdStr);
                    processTimeoutWithLock(gameId);
                }
            } catch (Exception e) {
                log.warn("[TIMER] Erro ao processar deadline key={}: {}", key, e.getMessage());
                // Remove chave inválida
                redisTemplate.delete(key);
            }
        }
    }

    /**
     * Tenta adquirir lock e processar o timeout.
     * Se outra instância já pegou o lock, ignora silenciosamente.
     */
    private void processTimeoutWithLock(UUID gameId) {
        String lockKey = LOCK_KEY_PREFIX + gameId;

        // SETNX com TTL — só uma instância ganha
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            return; // Outra instância já está processando
        }

        try {
            // Remove a deadline para evitar reprocessamento
            redisTemplate.delete(DEADLINE_KEY_PREFIX + gameId);

            handleTimeout(gameId);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * Chamado quando o timer expira (jogador não atacou a tempo).
     * Busca o jogo, passa o turno, publica TURN_TIMEOUT + TURN_CHANGE, reinicia timer.
     */
    private void handleTimeout(UUID gameId) {
        var gameOpt = storage.findById(gameId);
        if (gameOpt.isEmpty()) return;

        Game game = gameOpt.get();
        if (game.getStatus() != GameStatus.IN_PROGRESS) return;

        UUID timedOutPlayer = game.getCurrentTurn();

        // Passa o turno
        game.forceSwapTurn();
        storage.save(game);

        UUID nextTurn = game.getCurrentTurn();

        // Publica eventos
        eventPublisher.publishTurnTimeout(gameId, timedOutPlayer, nextTurn);
        eventPublisher.publishTurnChange(gameId, nextTurn, turnTimeoutSeconds);

        // Reinicia timer para o novo turno
        startTimer(gameId);

        log.debug("[TIMER] Timeout processado — gameId={}, timedOutPlayer={}, nextTurn={}", gameId, timedOutPlayer, nextTurn);
    }

    public int getTurnTimeout() {
        return turnTimeoutSeconds;
    }
}
