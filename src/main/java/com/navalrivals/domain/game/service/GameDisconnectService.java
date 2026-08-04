package com.navalrivals.domain.game.service;

import com.navalrivals.domain.game.entity.Game;
import com.navalrivals.domain.game.enums.GameStatus;
import com.navalrivals.domain.game.storage.GameStorage;
import com.navalrivals.domain.room.repository.RoomRepository;
import com.navalrivals.domain.room.service.LobbySSEService;
import com.navalrivals.domain.room.service.RoomWebSocketService;
import com.navalrivals.domain.user.entity.User;
import com.navalrivals.domain.user.repository.UserRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Gerencia desconexão e reconexão de jogadores durante uma partida.
 *
 * Estado distribuído via Redis:
 * - game-session:{sessionId} → "{gameId}:{playerId}" (qual jogo/jogador cada sessão pertence)
 * - game-active-session:{playerId} → sessionId (sessão ativa mais recente)
 * - game-disconnected:{playerId} → gameId (flag de desconexão com TTL = reconnectTimeout)
 *
 * Timers de reconexão permanecem locais (ScheduledExecutorService) pois o timeout
 * já é protegido pela chave Redis game-disconnected com TTL.
 * Se a instância que agendou o timer morrer, a chave expira e o TurnTimeoutScheduler
 * (ou GameCleanupScheduler) cuida da limpeza.
 */
@Slf4j
@Service
public class GameDisconnectService {

    private static final String SESSION_KEY_PREFIX = "game-session:";
    private static final String ACTIVE_SESSION_KEY_PREFIX = "game-active-session:";
    private static final String DISCONNECTED_KEY_PREFIX = "game-disconnected:";
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);

    private final int reconnectTimeoutSeconds;
    private final GameEventPublisher eventPublisher;
    private final GameStorage gameStorage;
    private final TurnTimerService turnTimerService;
    private final GameService gameService;
    private final GameResultService gameResultService;
    private final RoomRepository roomRepository;
    private final RoomWebSocketService roomWebSocketService;
    private final UserRepository userRepository;
    private final LobbySSEService lobbySSEService;
    private final StringRedisTemplate redisTemplate;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    /**
     * Timers locais de reconexão. Se o jogador reconectar nesta instância, cancela.
     * Se a instância morrer, a chave Redis game-disconnected expira e o cleanup cuida.
     */
    private final Map<UUID, ScheduledFuture<?>> reconnectTimers = new ConcurrentHashMap<>();

    public GameDisconnectService(
            @Value("${game.reconnect-timeout-seconds}") int reconnectTimeoutSeconds,
            GameEventPublisher eventPublisher,
            GameStorage gameStorage,
            TurnTimerService turnTimerService,
            GameService gameService,
            GameResultService gameResultService,
            RoomRepository roomRepository,
            RoomWebSocketService roomWebSocketService,
            UserRepository userRepository,
            LobbySSEService lobbySSEService,
            StringRedisTemplate redisTemplate
    ) {
        this.reconnectTimeoutSeconds = reconnectTimeoutSeconds;
        this.eventPublisher = eventPublisher;
        this.gameStorage = gameStorage;
        this.turnTimerService = turnTimerService;
        this.gameService = gameService;
        this.gameResultService = gameResultService;
        this.roomRepository = roomRepository;
        this.roomWebSocketService = roomWebSocketService;
        this.userRepository = userRepository;
        this.lobbySSEService = lobbySSEService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Registra a sessão de um jogador no Redis.
     * Chamado quando o jogador envia /app/game/{gameId}/register.
     */
    public void registerSession(String sessionId, UUID gameId, UUID playerId) {
        String sessionValue = gameId + ":" + playerId;
        redisTemplate.opsForValue().set(SESSION_KEY_PREFIX + sessionId, sessionValue, SESSION_TTL);
        redisTemplate.opsForValue().set(ACTIVE_SESSION_KEY_PREFIX + playerId, sessionId, SESSION_TTL);
        log.debug("Sessão registrada: session={}, game={}, player={}", sessionId, gameId, playerId);
    }

    /**
     * Chamado quando uma sessão WebSocket desconecta (SessionDisconnectEvent).
     * Usa delay de 2s para evitar race conditions na transição de telas.
     */
    public void handleDisconnect(String sessionId) {
        String sessionValue = redisTemplate.opsForValue().getAndDelete(SESSION_KEY_PREFIX + sessionId);
        if (sessionValue == null) return;

        String[] parts = sessionValue.split(":");
        UUID gameId = UUID.fromString(parts[0]);
        UUID playerId = UUID.fromString(parts[1]);

        // Agenda processamento com delay para dar tempo de uma nova sessão se registrar
        scheduler.schedule(() -> {
            processDisconnect(sessionId, gameId, playerId);
        }, 2, TimeUnit.SECONDS);
    }

    /**
     * Processa o disconnect efetivamente após o delay.
     * Se o jogador já reconectou com nova sessão, ignora.
     */
    private void processDisconnect(String sessionId, UUID gameId, UUID playerId) {
        // Se o jogador já tem uma sessão MAIS RECENTE ativa, esse disconnect
        // é de uma sessão obsoleta (ex: navegação entre telas) — ignorar.
        String currentActiveSession = redisTemplate.opsForValue().get(ACTIVE_SESSION_KEY_PREFIX + playerId);
        if (currentActiveSession != null && !currentActiveSession.equals(sessionId)) {
            log.info("Disconnect de sessão obsoleta ignorado (após delay): session={}, player={}, activeSession={}",
                    sessionId, playerId, currentActiveSession);
            return;
        }

        // Remove active session
        redisTemplate.delete(ACTIVE_SESSION_KEY_PREFIX + playerId);

        var gameOpt = gameStorage.findById(gameId);
        if (gameOpt.isEmpty()) return;

        Game game = gameOpt.get();
        if (game.getStatus() != GameStatus.IN_PROGRESS
                && game.getStatus() != GameStatus.PLACING_SHIPS) {
            return;
        }

        // Marca como disconnected no Redis com TTL (proteção contra instância morrer)
        redisTemplate.opsForValue().set(
                DISCONNECTED_KEY_PREFIX + playerId,
                gameId.toString(),
                Duration.ofSeconds(reconnectTimeoutSeconds + 5)
        );

        log.info("Jogador {} desconectou do jogo {} (confirmado após delay)", playerId, gameId);

        // Pausa o timer de turno
        turnTimerService.pauseTimer(gameId);

        // Publica OPPONENT_DISCONNECTED
        eventPublisher.publishOpponentDisconnected(gameId, playerId, reconnectTimeoutSeconds);

        // Agenda timeout de reconexão
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            handleReconnectTimeout(gameId, playerId);
        }, reconnectTimeoutSeconds, TimeUnit.SECONDS);

        reconnectTimers.put(playerId, future);
    }

    /**
     * Trata desconexão de um jogador identificado por gameId + playerId.
     * Usado quando o disconnect é detectado via room-register (RoomSessionService)
     * e o jogador NUNCA registrou sessão no game.
     */
    public void handleDisconnectByPlayer(UUID gameId, UUID playerId) {
        // Se já existe flag de disconnected no Redis, não duplicar
        String existing = redisTemplate.opsForValue().get(DISCONNECTED_KEY_PREFIX + playerId);
        if (existing != null) {
            log.debug("Flag de disconnect já existe para player={}, game={}, ignorando", playerId, gameId);
            return;
        }

        var gameOpt = gameStorage.findById(gameId);
        if (gameOpt.isEmpty()) return;

        Game game = gameOpt.get();
        if (game.getStatus() != GameStatus.IN_PROGRESS
                && game.getStatus() != GameStatus.PLACING_SHIPS) {
            return;
        }

        // Marca como disconnected
        redisTemplate.opsForValue().set(
                DISCONNECTED_KEY_PREFIX + playerId,
                gameId.toString(),
                Duration.ofSeconds(reconnectTimeoutSeconds + 5)
        );

        log.info("Jogador {} desconectou do jogo {} (detectado via room-register)", playerId, gameId);

        // Pausa o timer de turno
        turnTimerService.pauseTimer(gameId);

        // Publica OPPONENT_DISCONNECTED
        eventPublisher.publishOpponentDisconnected(gameId, playerId, reconnectTimeoutSeconds);

        // Agenda timeout de reconexão
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
        // Remove flag de disconnected
        String disconnectedGameId = redisTemplate.opsForValue().getAndDelete(DISCONNECTED_KEY_PREFIX + playerId);

        // Cancela o timeout de reconexão local se estiver ativo
        ScheduledFuture<?> future = reconnectTimers.remove(playerId);

        if (disconnectedGameId != null || future != null) {
            if (future != null) {
                future.cancel(false);
            }
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
     * Chamado quando o timeout de reconexão expira sem reconexão.
     */
    private void handleReconnectTimeout(UUID gameId, UUID disconnectedPlayerId) {
        reconnectTimers.remove(disconnectedPlayerId);

        // Remove a flag de disconnect do Redis
        redisTemplate.delete(DISCONNECTED_KEY_PREFIX + disconnectedPlayerId);

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
            turnTimerService.cancelTimer(gameId);
            gameService.removeGame(gameId);
        }
    }

    /**
     * Trata desconexão durante PLACING_SHIPS.
     */
    private void handlePlacingShipsDisconnect(UUID gameId, UUID disconnectedPlayerId) {
        var game = gameStorage.findById(gameId).orElse(null);
        if (game == null) return;

        UUID winnerId;
        if (game.getPlayer1().getPlayerId().equals(disconnectedPlayerId)) {
            winnerId = game.getPlayer2().getPlayerId();
        } else {
            winnerId = game.getPlayer1().getPlayerId();
        }

        eventPublisher.publishGameOver(gameId, winnerId, disconnectedPlayerId, "OPPONENT_DISCONNECTED");

        roomRepository.findByGameId(gameId).ifPresent(room -> {
            String nickname = userRepository.findById(disconnectedPlayerId)
                    .map(User::getNickname)
                    .orElse("Unknown");

            roomWebSocketService.notifyPlayerLeft(room.getId(), disconnectedPlayerId, nickname);
            roomRepository.delete(room);
            lobbySSEService.notifyLobbyUpdated();
        });

        gameService.removeGame(gameId);
        log.info("Jogo {} cancelado durante PLACING_SHIPS por desconexão do jogador {}. Sala deletada.", gameId, disconnectedPlayerId);
    }

    /**
     * Trata desconexão durante IN_PROGRESS.
     */
    private void handleInProgressDisconnect(Game game, UUID gameId, UUID disconnectedPlayerId) {
        UUID winnerId;
        if (game.getPlayer1().getPlayerId().equals(disconnectedPlayerId)) {
            winnerId = game.getPlayer2().getPlayerId();
        } else {
            winnerId = game.getPlayer1().getPlayerId();
        }

        if (!game.finish(winnerId)) {
            log.debug("Jogo {} já foi finalizado por outro thread, ignorando", gameId);
            return;
        }

        gameResultService.persistGameResult(game);
        eventPublisher.publishGameOver(gameId, winnerId, disconnectedPlayerId, "OPPONENT_DISCONNECTED");
        gameResultService.updatePlayerStatsAsync(winnerId, disconnectedPlayerId);

        turnTimerService.cancelTimer(gameId);
        gameService.removeGame(gameId);

        log.info("Jogo {} encerrado por desconexão. Vencedor: {}", gameId, winnerId);
    }

    /**
     * Remove sessões e timers associados a um jogo finalizado.
     */
    public void cleanupGame(UUID gameId) {
        var gameOpt = gameStorage.findById(gameId);
        if (gameOpt.isPresent()) {
            Game game = gameOpt.get();
            UUID player1Id = game.getPlayer1().getPlayerId();
            cleanupPlayer(player1Id);

            if (game.getPlayer2() != null) {
                UUID player2Id = game.getPlayer2().getPlayerId();
                cleanupPlayer(player2Id);
            }
        }
    }

    private void cleanupPlayer(UUID playerId) {
        // Remove chaves Redis
        redisTemplate.delete(ACTIVE_SESSION_KEY_PREFIX + playerId);
        redisTemplate.delete(DISCONNECTED_KEY_PREFIX + playerId);

        // Cancela timer local
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
