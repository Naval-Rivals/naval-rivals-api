package com.navalrivals.domain.game.service;

import com.navalrivals.domain.game.entity.Game;
import com.navalrivals.domain.game.enums.GameStatus;
import com.navalrivals.domain.game.storage.GameStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Remove jogos abandonados/órfãos da memória periodicamente.
 *
 * Usa distributed lock para garantir que apenas UMA instância executa o cleanup por vez.
 *
 * Critérios de remoção:
 * - WAITING_OPPONENT ou PLACING_SHIPS: sem atividade por mais de X minutos
 * - IN_PROGRESS: sem atividade por mais de X minutos (ambos jogadores abandonaram)
 * - FINISHED: sem remoção após X minutos (falha no fluxo normal de cleanup)
 *
 * Intervalo configurável via game.cleanup.interval-ms.
 */
@Slf4j
@Component
public class GameCleanupScheduler {

    private static final String LOCK_KEY = "scheduler-lock:game-cleanup";
    private static final Duration LOCK_TTL = Duration.ofSeconds(60);

    private final Duration abandonedThreshold;
    private final Duration inactiveInProgressThreshold;
    private final Duration finishedThreshold;
    private final GameStorage gameStorage;
    private final TurnTimerService turnTimerService;
    private final StringRedisTemplate redisTemplate;

    public GameCleanupScheduler(
            @Value("${game.cleanup.abandoned-threshold-minutes}") int abandonedMinutes,
            @Value("${game.cleanup.inactive-in-progress-threshold-minutes}") int inactiveMinutes,
            @Value("${game.cleanup.finished-threshold-minutes}") int finishedMinutes,
            GameStorage gameStorage,
            TurnTimerService turnTimerService,
            StringRedisTemplate redisTemplate
    ) {
        this.abandonedThreshold = Duration.ofMinutes(abandonedMinutes);
        this.inactiveInProgressThreshold = Duration.ofMinutes(inactiveMinutes);
        this.finishedThreshold = Duration.ofMinutes(finishedMinutes);
        this.gameStorage = gameStorage;
        this.turnTimerService = turnTimerService;
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(fixedRateString = "${game.cleanup.interval-ms}")
    public void cleanupAbandonedGames() {
        // Distributed lock — apenas uma instância executa
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            return;
        }

        try {
            Instant now = Instant.now();

            var removed = gameStorage.removeIf(game -> {
                GameStatus status = game.getStatus();
                Instant lastActivity = game.getLastActivityAt();

                if ((status == GameStatus.WAITING_OPPONENT || status == GameStatus.PLACING_SHIPS)
                        && lastActivity.isBefore(now.minus(abandonedThreshold))) {
                    turnTimerService.cancelTimer(game.getId());
                    log.debug("Cleanup: removendo jogo {} (status={}, inativo desde {})", game.getId(), status, lastActivity);
                    return true;
                }

                if (status == GameStatus.IN_PROGRESS
                        && lastActivity.isBefore(now.minus(inactiveInProgressThreshold))) {
                    turnTimerService.cancelTimer(game.getId());
                    log.debug("Cleanup: removendo jogo {} (IN_PROGRESS órfão, inativo desde {})", game.getId(), lastActivity);
                    return true;
                }

                if (status == GameStatus.FINISHED
                        && lastActivity.isBefore(now.minus(finishedThreshold))) {
                    log.debug("Cleanup: removendo jogo {} (FINISHED não limpo, desde {})", game.getId(), lastActivity);
                    return true;
                }

                return false;
            });

            if (removed > 0) {
                log.info("Cleanup: {} jogos abandonados/órfãos removidos da memória", removed);
            }
        } finally {
            redisTemplate.delete(LOCK_KEY);
        }
    }
}
