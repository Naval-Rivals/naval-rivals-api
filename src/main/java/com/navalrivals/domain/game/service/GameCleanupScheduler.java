package com.navalrivals.domain.game.service;

import com.navalrivals.domain.game.entity.Game;
import com.navalrivals.domain.game.enums.GameStatus;
import com.navalrivals.domain.game.storage.GameStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Remove jogos abandonados/órfãos da memória periodicamente.
 *
 * Critérios de remoção:
 * - WAITING_OPPONENT ou PLACING_SHIPS: sem atividade por mais de 15 minutos
 * - IN_PROGRESS: sem atividade por mais de 10 minutos (ambos jogadores abandonaram)
 * - FINISHED: sem remoção após 2 minutos (falha no fluxo normal de cleanup)
 *
 * Roda a cada 2 minutos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameCleanupScheduler {

    private static final Duration ABANDONED_THRESHOLD = Duration.ofMinutes(15);
    private static final Duration INACTIVE_IN_PROGRESS_THRESHOLD = Duration.ofMinutes(10);
    private static final Duration FINISHED_THRESHOLD = Duration.ofMinutes(2);

    private final GameStorage gameStorage;
    private final TurnTimerService turnTimerService;

    @Scheduled(fixedRate = 120_000) // 2 minutos
    public void cleanupAbandonedGames() {
        Instant now = Instant.now();

        var removed = gameStorage.removeIf(game -> {
            GameStatus status = game.getStatus();
            Instant lastActivity = game.getLastActivityAt();

            // WAITING_OPPONENT ou PLACING_SHIPS: sem atividade por 15 minutos
            if ((status == GameStatus.WAITING_OPPONENT || status == GameStatus.PLACING_SHIPS)
                    && lastActivity.isBefore(now.minus(ABANDONED_THRESHOLD))) {
                turnTimerService.cancelTimer(game.getId());
                log.debug("Cleanup: removendo jogo {} (status={}, inativo desde {})", game.getId(), status, lastActivity);
                return true;
            }

            // IN_PROGRESS: sem atividade por 10 minutos (ambos abandonaram)
            if (status == GameStatus.IN_PROGRESS
                    && lastActivity.isBefore(now.minus(INACTIVE_IN_PROGRESS_THRESHOLD))) {
                turnTimerService.cancelTimer(game.getId());
                log.debug("Cleanup: removendo jogo {} (IN_PROGRESS órfão, inativo desde {})", game.getId(), lastActivity);
                return true;
            }

            // FINISHED: não foi removido pelo fluxo normal após 2 minutos
            if (status == GameStatus.FINISHED
                    && lastActivity.isBefore(now.minus(FINISHED_THRESHOLD))) {
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
