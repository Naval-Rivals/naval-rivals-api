package com.navalrivals.domain.game.service;

import com.navalrivals.domain.game.entity.Game;
import com.navalrivals.domain.game.enums.GameStatus;
import com.navalrivals.domain.game.storage.GameStorage;
import com.navalrivals.domain.room.enums.RoomStatus;
import com.navalrivals.domain.room.repository.RoomRepository;
import com.navalrivals.domain.room.service.RoomWebSocketService;
import com.navalrivals.domain.user.entity.User;
import com.navalrivals.domain.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Gerencia desconexão e reconexão de jogadores durante uma partida.
 *
 * Fluxo:
 * 1. Jogador desconecta → pausa timer, publica OPPONENT_DISCONNECTED
 * 2. Inicia countdown para reconexão (configurável)
 * 3a. Se reconectar antes do timeout → cancela countdown, publica OPPONENT_RECONNECTED, retoma timer
 * 3b. Se NÃO reconectar → o oponente vence por W.O., publica GAME_OVER
 */
@Slf4j
@Service
public class GameDisconnectService {

    private final int reconnectTimeoutSeconds;
    private final GameEventPublisher eventPublisher;
    private final GameStorage gameStorage;
    private final TurnTimerService turnTimerService;
    private final GameService gameService;
    private final RoomRepository roomRepository;
    private final RoomWebSocketService roomWebSocketService;
    private final UserRepository userRepository;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public GameDisconnectService(
            @Value("${game.reconnect-timeout-seconds}") int reconnectTimeoutSeconds,
            GameEventPublisher eventPublisher,
            GameStorage gameStorage,
            TurnTimerService turnTimerService,
            GameService gameService,
            RoomRepository roomRepository,
            RoomWebSocketService roomWebSocketService,
            UserRepository userRepository
    ) {
        this.reconnectTimeoutSeconds = reconnectTimeoutSeconds;
        this.eventPublisher = eventPublisher;
        this.gameStorage = gameStorage;
        this.turnTimerService = turnTimerService;
        this.gameService = gameService;
        this.roomRepository = roomRepository;
        this.roomWebSocketService = roomWebSocketService;
        this.userRepository = userRepository;
    }

    /**
     * Mapeia sessionId (WebSocket) → info do jogador na partida.
     * Preenchido quando o jogador chama /app/game/{gameId}/register.
     */
    private final Map<String, PlayerSession> sessionMap = new ConcurrentHashMap<>();

    /**
     * Mapeia playerId → ScheduledFuture do timeout de reconexão.
     * Se reconectar antes de expirar, cancela o future.
     */
    private final Map<UUID, ScheduledFuture<?>> reconnectTimers = new ConcurrentHashMap<>();

    /**
     * Registra a sessão de um jogador.
     * Chamado quando o jogador envia /app/game/{gameId}/register.
     */
    public void registerSession(String sessionId, UUID gameId, UUID playerId) {
        sessionMap.put(sessionId, new PlayerSession(gameId, playerId));
        log.debug("Sessão registrada: session={}, game={}, player={}", sessionId, gameId, playerId);
    }

    /**
     * Chamado quando uma sessão WebSocket desconecta (SessionDisconnectEvent).
     * Verifica se é um jogador em partida ativa e inicia o countdown de reconexão.
     */
    public void handleDisconnect(String sessionId) {
        PlayerSession session = sessionMap.remove(sessionId);
        if (session == null) return;

        UUID gameId = session.gameId();
        UUID playerId = session.playerId();

        var gameOpt = gameStorage.findById(gameId);
        if (gameOpt.isEmpty()) return;

        Game game = gameOpt.get();
        if (game.getStatus() != GameStatus.IN_PROGRESS
                && game.getStatus() != GameStatus.PLACING_SHIPS) {
            return;
        }

        log.info("Jogador {} desconectou do jogo {}", playerId, gameId);

        // Pausa o timer de turno
        turnTimerService.pauseTimer(gameId);

        // Publica OPPONENT_DISCONNECTED
        eventPublisher.publishOpponentDisconnected(gameId, playerId, reconnectTimeoutSeconds);

        // Agenda timeout de reconexão (30s)
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            handleReconnectTimeout(gameId, playerId);
        }, reconnectTimeoutSeconds, TimeUnit.SECONDS);

        reconnectTimers.put(playerId, future);
    }

    /**
     * Chamado quando um jogador reconecta (nova sessão WebSocket para o mesmo jogo).
     * Invocado pelo GameWebSocketController no /register.
     */
    public void handleReconnect(String newSessionId, UUID gameId, UUID playerId) {
        // Cancela o timeout de reconexão se estiver ativo
        ScheduledFuture<?> future = reconnectTimers.remove(playerId);
        if (future != null) {
            future.cancel(false);
            log.info("Jogador {} reconectou ao jogo {} (timeout cancelado)", playerId, gameId);

            // Registra nova sessão
            registerSession(newSessionId, gameId, playerId);

            // Publica OPPONENT_RECONNECTED
            eventPublisher.publishOpponentReconnected(gameId, playerId);

            // Retoma timer de turno
            turnTimerService.resumeTimer(gameId);
        } else {
            // Não tinha desconexão pendente — registro normal
            registerSession(newSessionId, gameId, playerId);
        }
    }

    /**
     * Chamado quando o timeout de 30s expira sem reconexão.
     * - Se IN_PROGRESS: o jogador que ficou conectado vence por W.O.
     * - Se PLACING_SHIPS: cancela o jogo, reseta a room e emite PLAYER_LEFT.
     */
    private void handleReconnectTimeout(UUID gameId, UUID disconnectedPlayerId) {
        reconnectTimers.remove(disconnectedPlayerId);

        try {
            var gameOpt = gameStorage.findById(gameId);
            if (gameOpt.isEmpty()) return;

            Game game = gameOpt.get();
            if (game.getStatus() == GameStatus.FINISHED) return;

            if (game.getStatus() == GameStatus.PLACING_SHIPS) {
                handlePlacingShipsDisconnect(gameId, disconnectedPlayerId);
            } else {
                handleInProgressDisconnect(game, gameId, disconnectedPlayerId);
            }
        } catch (Exception e) {
            log.error("Erro ao processar timeout de reconexão do jogo {}: {}", gameId, e.getMessage(), e);
            // Garante limpeza mesmo em caso de erro
            turnTimerService.cancelTimer(gameId);
            gameService.removeGame(gameId);
        }
    }

    /**
     * Trata desconexão durante PLACING_SHIPS:
     * - Emite GAME_OVER com reason OPPONENT_DISCONNECTED no tópico do game
     * - Emite PLAYER_LEFT no tópico da room
     * - Reseta a room (remove opponent, volta WAITING, limpa gameId)
     * - Remove o game da memória
     */
    private void handlePlacingShipsDisconnect(UUID gameId, UUID disconnectedPlayerId) {
        // Determina vencedor (quem ficou online)
        var game = gameStorage.findById(gameId).orElse(null);
        if (game == null) return;

        UUID winnerId;
        if (game.getPlayer1().getPlayerId().equals(disconnectedPlayerId)) {
            winnerId = game.getPlayer2().getPlayerId();
        } else {
            winnerId = game.getPlayer1().getPlayerId();
        }

        // Publica GAME_OVER no tópico do game
        eventPublisher.publishGameOver(gameId, winnerId, disconnectedPlayerId, "OPPONENT_DISCONNECTED");

        // Busca a room associada e emite PLAYER_LEFT + reseta
        roomRepository.findByGameId(gameId).ifPresent(room -> {
            String nickname = userRepository.findById(disconnectedPlayerId)
                    .map(User::getNickname)
                    .orElse("Unknown");

            roomWebSocketService.notifyPlayerLeft(room.getId(), disconnectedPlayerId, nickname);

            // Reseta a room para WAITING
            room.setOpponent(null);
            room.setGameId(null);
            room.setStatus(RoomStatus.WAITING);
            roomRepository.save(room);
        });

        // Remove o game da memória
        gameService.removeGame(gameId);

        log.info("Jogo {} cancelado durante PLACING_SHIPS por desconexão do jogador {}", gameId, disconnectedPlayerId);
    }

    /**
     * Trata desconexão durante IN_PROGRESS:
     * - Finaliza o jogo com vitória por W.O.
     * - Persiste resultado
     * - Publica GAME_OVER
     * - Remove o game da memória
     */
    private void handleInProgressDisconnect(Game game, UUID gameId, UUID disconnectedPlayerId) {
        // Determina o vencedor (o que ficou online)
        UUID winnerId;
        if (game.getPlayer1().getPlayerId().equals(disconnectedPlayerId)) {
            winnerId = game.getPlayer2().getPlayerId();
        } else {
            winnerId = game.getPlayer1().getPlayerId();
        }

        // Finaliza o jogo — se retornar false, outro thread já finalizou
        if (!game.finish(winnerId)) {
            log.debug("Jogo {} já foi finalizado por outro thread, ignorando", gameId);
            return;
        }

        // Persiste resultado ANTES de publicar (frontend busca logo após GAME_OVER)
        gameService.persistGameResult(game);

        // Publica GAME_OVER
        eventPublisher.publishGameOver(gameId, winnerId, disconnectedPlayerId, "OPPONENT_DISCONNECTED");

        // Limpa timer e game da memória
        turnTimerService.cancelTimer(gameId);
        gameService.removeGame(gameId);

        log.info("Jogo {} encerrado por desconexão. Vencedor: {}", gameId, winnerId);
    }

    private record PlayerSession(UUID gameId, UUID playerId) {}

    /**
     * Remove todas as sessões e timers de reconexão associados a um jogo finalizado.
     * Chamado quando o jogo termina (por ataque ou desconexão) para evitar memory leak.
     */
    public void cleanupGame(UUID gameId) {
        // Remove sessões associadas ao jogo
        sessionMap.entrySet().removeIf(entry -> entry.getValue().gameId().equals(gameId));

        // Cancela timers de reconexão dos jogadores desse jogo
        var gameOpt = gameStorage.findById(gameId);
        if (gameOpt.isPresent()) {
            Game game = gameOpt.get();
            cancelReconnectTimer(game.getPlayer1().getPlayerId());
            if (game.getPlayer2() != null) {
                cancelReconnectTimer(game.getPlayer2().getPlayerId());
            }
        }
    }

    private void cancelReconnectTimer(UUID playerId) {
        ScheduledFuture<?> future = reconnectTimers.remove(playerId);
        if (future != null) {
            future.cancel(false);
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
