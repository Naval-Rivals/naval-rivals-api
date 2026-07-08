package com.navalrivals.domain.game.service;

import com.navalrivals.domain.game.entity.Game;
import com.navalrivals.domain.game.enums.GameStatus;
import com.navalrivals.domain.game.storage.GameStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Remove jogos abandonados/órfãos da memória periodicamente.
 *
 * Critérios de remoção:
 * - WAITING_OPPONENT ou PLACING_SHIPS: sem atividade por mais de X minutos (configurável)
 * - IN_PROGRESS: sem atividade por mais de X minutos (ambos jogadores abandonaram)
 * - FINISHED: sem remoção após X minutos (falha no fluxo normal de cleanup)
 *
 * Intervalo configurável via game.cleanup.interval-ms.
 */
@Slf4j
@Component
public class GameCleanupScheduler {

    private final Duration abandonedThreshold;
    private final Duration inactiveInProgressThreshold;
    private final Duration finishedThreshold;
    private final GameStorage gameStorage;
    private final TurnTimerService turnTimerService;

    public GameCleanupScheduler(
            @Value("${game.cleanup.abandoned-threshold-minutes}") int abandonedMinutes,
            @Value("${game.cleanup.inactive-in-progress-threshold-minutes}") int inactiveMinutes,
            @Value("${game.cleanup.finished-threshold-minutes}") int finishedMinutes,
            GameStorage gameStorage,
            TurnTimerService turnTimerService
    ) {
        this.abandonedThreshold = Duration.ofMinutes(abandonedMinutes);
        this.inactiveInProgressThreshold = Duration.ofMinutes(inactiveMinutes);
        this.finishedThreshold = Duration.ofMinutes(finishedMinutes);
        this.gameStorage = gameStorage;
        this.turnTimerService = turnTimerService;
    }

    @Scheduled(fixedRateString = "${game.cleanup.interval-ms}")
    public void cleanupAbandonedGames() {
        Instant now = Instant.now();

        var removed = gameStorage.removeIf(game -> {
            GameStatus status = game.getStatus();
            Instant lastActivity = game.getLastActivityAt();

            // WAITING_OPPONENT ou PLACING_SHIPS: sem atividade por 15 minutos
            if ((status == GameStatus.WAITING_OPPONENT || status == GameStatus.PLACING_SHIPS)
                    && lastActivity.isBefore(now.minus(abandonedThreshold))) {
                turnTimerService.cancelTimer(game.getId());
                log.debug("Cleanup: removendo jogo {} (status={}, inativo desde {})", game.getId(), status, lastActivity);
                return true;
            }

            // IN_PROGRESS: sem atividade por 10 minutos (ambos abandonaram)
            if (status == GameStatus.IN_PROGRESS
                    && lastActivity.isBefore(now.minus(inactiveInProgressThreshold))) {
                turnTimerService.cancelTimer(game.getId());
                log.debug("Cleanup: removendo jogo {} (IN_PROGRESS órfão, inativo desde {})", game.getId(), lastActivity);
                return true;
            }

            // FINISHED: não foi removido pelo fluxo normal após 2 minutos
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
    }
}
