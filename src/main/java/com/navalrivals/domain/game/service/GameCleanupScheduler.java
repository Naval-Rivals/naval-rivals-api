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
 * Remove jogos abandonados da memória periodicamente.
 *
 * Um jogo é considerado abandonado se está em WAITING_OPPONENT ou PLACING_SHIPS
 * por mais de 15 minutos (ninguém posicionou, sala ficou inativa).
 *
 * Roda a cada 5 minutos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameCleanupScheduler {

    private static final Duration ABANDONED_THRESHOLD = Duration.ofMinutes(15);

    private final GameStorage gameStorage;
    private final TurnTimerService turnTimerService;

    @Scheduled(fixedRate = 300_000) // 5 minutos
    public void cleanupAbandonedGames() {
        Instant cutoff = Instant.now().minus(ABANDONED_THRESHOLD);

        var removed = gameStorage.removeIf(game -> {
            GameStatus status = game.getStatus();
            boolean isStale = (status == GameStatus.WAITING_OPPONENT || status == GameStatus.PLACING_SHIPS)
                    && game.getCreatedAt().isBefore(cutoff);
            return isStale;
        });

        if (removed > 0) {
            log.info("Cleanup: {} jogos abandonados removidos da memória", removed);
        }
    }
}
