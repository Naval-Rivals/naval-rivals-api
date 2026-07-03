package com.navalrivals.domain.game.service;

import com.navalrivals.domain.game.entity.Game;
import com.navalrivals.domain.game.enums.GameStatus;
import com.navalrivals.domain.game.storage.GameStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Gerencia o timer de turno para cada partida.
 *
 * Abordagem "fire-once":
 * - Agenda UMA ÚNICA task para disparar após TURN_TIMEOUT_SECONDS
 * - Se o jogador atacar antes do timeout, cancela e reage
 * - Zero mensagens intermediárias (nada de TIMER_TICK)
 * - Frontend faz o countdown visual localmente ao receber TURN_CHANGE
 *
 * Isso escala para milhares de jogos com carga mínima no servidor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TurnTimerService {

    private static final int TURN_TIMEOUT_SECONDS = 60;

    private final GameEventPublisher eventPublisher;
    private final GameStorage storage;

    /**
     * Armazena o ScheduledFuture de cada jogo para poder cancelar.
     */
    private final Map<UUID, ScheduledFuture<?>> activeTimers = new ConcurrentHashMap<>();

    /**
     * Armazena os segundos restantes no momento da pausa (para reconexão).
     */
    private final Map<UUID, Long> pausedRemainingMs = new ConcurrentHashMap<>();

    /**
     * Armazena o instante em que o timer foi iniciado (para calcular restante na pausa).
     */
    private final Map<UUID, Long> timerStartedAt = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    /**
     * Inicia (ou reinicia) o timer de turno para uma partida.
     * Agenda uma ÚNICA task que dispara após 60 segundos.
     *
     * Chamado quando:
     * - O jogo começa (GAME_STARTED)
     * - Um ataque é feito (turno mudou)
     * - Um timeout acontece (turno passou automaticamente)
     * - Reconexão retoma o timer
     */
    public void startTimer(UUID gameId) {
        cancelTimer(gameId);
        scheduleTimeout(gameId, TURN_TIMEOUT_SECONDS * 1000L);
    }

    /**
     * Cancela o timer de uma partida completamente.
     * Chamado quando:
     * - O jogador ataca (antes de startTimer para o novo turno)
     * - O jogo termina (GAME_OVER)
     */
    public void cancelTimer(UUID gameId) {
        ScheduledFuture<?> future = activeTimers.remove(gameId);
        if (future != null) {
            future.cancel(false);
        }
        pausedRemainingMs.remove(gameId);
        timerStartedAt.remove(gameId);
    }

    /**
     * Pausa o timer (desconexão de jogador).
     * Calcula quanto tempo restava e armazena para retomar depois.
     */
    public void pauseTimer(UUID gameId) {
        ScheduledFuture<?> future = activeTimers.remove(gameId);
        if (future != null) {
            future.cancel(false);
        }

        Long startedAt = timerStartedAt.remove(gameId);
        if (startedAt != null) {
            long elapsed = System.currentTimeMillis() - startedAt;
            long remaining = (TURN_TIMEOUT_SECONDS * 1000L) - elapsed;
            if (remaining > 0) {
                pausedRemainingMs.put(gameId, remaining);
            }
        }
    }

    /**
     * Retoma o timer após reconexão, usando os milissegundos que restavam.
     */
    public void resumeTimer(UUID gameId) {
        Long remainingMs = pausedRemainingMs.remove(gameId);
        if (remainingMs == null || remainingMs <= 0) {
            // Não havia timer pausado ou já expirou — reinicia do zero
            startTimer(gameId);
            return;
        }
        scheduleTimeout(gameId, remainingMs);
    }

    /**
     * Agenda a task de timeout.
     */
    private void scheduleTimeout(UUID gameId, long delayMs) {
        timerStartedAt.put(gameId, System.currentTimeMillis());

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                handleTimeout(gameId);
            } catch (Exception e) {
                log.error("Erro no timeout do jogo {}: {}", gameId, e.getMessage());
            }
        }, delayMs, TimeUnit.MILLISECONDS);

        activeTimers.put(gameId, future);
    }

    /**
     * Chamado quando o timer expira (jogador não atacou a tempo).
     * Busca o jogo, passa o turno, publica TURN_TIMEOUT + TURN_CHANGE, reinicia timer.
     */
    private void handleTimeout(UUID gameId) {
        timerStartedAt.remove(gameId);
        activeTimers.remove(gameId);

        var gameOpt = storage.findById(gameId);
        if (gameOpt.isEmpty()) return;

        Game game = gameOpt.get();
        if (game.getStatus() != GameStatus.IN_PROGRESS) return;

        UUID timedOutPlayer = game.getCurrentTurn();

        // Passa o turno
        game.forceSwapTurn();

        UUID nextTurn = game.getCurrentTurn();

        // Publica eventos
        eventPublisher.publishTurnTimeout(gameId, timedOutPlayer, nextTurn);
        eventPublisher.publishTurnChange(gameId, nextTurn, TURN_TIMEOUT_SECONDS);

        // Reinicia timer para o novo turno
        startTimer(gameId);
    }

    public int getTurnTimeout() {
        return TURN_TIMEOUT_SECONDS;
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
