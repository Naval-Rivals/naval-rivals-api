package com.navalrivals.domain.game.service;

import com.navalrivals.domain.game.entity.Game;
import com.navalrivals.domain.game.enums.GameStatus;
import com.navalrivals.domain.game.storage.GameStorage;
import com.navalrivals.domain.room.enums.RoomStatus;
import com.navalrivals.domain.room.repository.RoomRepository;
import com.navalrivals.domain.room.service.LobbySSEService;
import com.navalrivals.domain.room.service.RoomWebSocketService;
import com.navalrivals.domain.user.entity.User;
import com.navalrivals.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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
    private final GameResultService gameResultService;
    private final RoomRepository roomRepository;
    private final RoomWebSocketService roomWebSocketService;
    private final UserRepository userRepository;
    private final LobbySSEService lobbySSEService;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

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
            LobbySSEService lobbySSEService
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
    }

    /**
     * Mapeia sessionId (WebSocket) → info do jogador na partida.
     * Preenchido quando o jogador chama /app/game/{gameId}/register.
     */
    private final Map<String, PlayerSession> sessionMap = new ConcurrentHashMap<>();

    /**
     * Mapeia playerId → sessionId ativo mais recente.
     * Usado para detectar se um disconnect é de uma sessão obsoleta (F5/reload)
     * quando uma nova sessão já foi registrada.
     */
    private final Map<UUID, String> activeSessionByPlayer = new ConcurrentHashMap<>();

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
        activeSessionByPlayer.put(playerId, sessionId);
        log.debug("Sessão registrada: session={}, game={}, player={}", sessionId, gameId, playerId);
    }

    /**
     * Chamado quando uma sessão WebSocket desconecta (SessionDisconnectEvent).
     * Verifica se é um jogador em partida ativa e inicia o countdown de reconexão.
     *
     * Usa um pequeno delay (2s) antes de processar o disconnect para evitar race conditions
     * quando o frontend fecha e reconecta rapidamente (navegação entre telas).
     */
    public void handleDisconnect(String sessionId) {
        PlayerSession session = sessionMap.remove(sessionId);
        if (session == null) return;

        UUID gameId = session.gameId();
        UUID playerId = session.playerId();

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
        String currentActiveSession = activeSessionByPlayer.get(playerId);
        if (currentActiveSession != null && !currentActiveSession.equals(sessionId)) {
            log.info("Disconnect de sessão obsoleta ignorado (após delay): session={}, player={}, activeSession={}",
                    sessionId, playerId, currentActiveSession);
            return;
        }

        // Remove do activeSessionByPlayer pois o jogador realmente desconectou
        activeSessionByPlayer.remove(playerId, sessionId);

        var gameOpt = gameStorage.findById(gameId);
        if (gameOpt.isEmpty()) return;

        Game game = gameOpt.get();
        if (game.getStatus() != GameStatus.IN_PROGRESS
                && game.getStatus() != GameStatus.PLACING_SHIPS) {
            return;
        }

        log.info("Jogador {} desconectou do jogo {} (confirmado após delay)", playerId, gameId);

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
     * Trata desconexão de um jogador identificado por gameId + playerId.
     * Usado quando o disconnect é detectado via room-register (RoomSessionService)
     * e o jogador NUNCA registrou sessão no game (ex: host fechou aba antes de /app/game/{gameId}/register).
     *
     * Executa a mesma lógica do handleDisconnect(sessionId), mas sem depender do sessionMap.
     */
    public void handleDisconnectByPlayer(UUID gameId, UUID playerId) {
        // Se já existe um timer de reconexão para esse jogador, não duplicar
        if (reconnectTimers.containsKey(playerId)) {
            log.debug("Timer de reconexão já existe para player={}, game={}, ignorando", playerId, gameId);
            return;
        }

        var gameOpt = gameStorage.findById(gameId);
        if (gameOpt.isEmpty()) return;

        Game game = gameOpt.get();
        if (game.getStatus() != GameStatus.IN_PROGRESS
                && game.getStatus() != GameStatus.PLACING_SHIPS) {
            return;
        }

        log.info("Jogador {} desconectou do jogo {} (detectado via room-register)", playerId, gameId);

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
     * - Deleta a room (uma vez iniciada, não pode ser reaberta)
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

        // Busca a room associada, emite PLAYER_LEFT e deleta
        roomRepository.findByGameId(gameId).ifPresent(room -> {
            String nickname = userRepository.findById(disconnectedPlayerId)
                    .map(User::getNickname)
                    .orElse("Unknown");

            roomWebSocketService.notifyPlayerLeft(room.getId(), disconnectedPlayerId, nickname);

            // Deleta a room — uma vez que o jogo foi criado, a sala não pode ser reaberta
            roomRepository.delete(room);
            lobbySSEService.notifyLobbyUpdated();
        });

        // Remove o game da memória
        gameService.removeGame(gameId);

        log.info("Jogo {} cancelado durante PLACING_SHIPS por desconexão do jogador {}. Sala deletada.", gameId, disconnectedPlayerId);
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
        gameResultService.persistGameResult(game);

        // Publica GAME_OVER
        eventPublisher.publishGameOver(gameId, winnerId, disconnectedPlayerId, "OPPONENT_DISCONNECTED");

        // Atualiza stats dos jogadores de forma assíncrona (não bloqueia)
        gameResultService.updatePlayerStatsAsync(winnerId, disconnectedPlayerId);

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
            UUID player1Id = game.getPlayer1().getPlayerId();
            cancelReconnectTimer(player1Id);
            activeSessionByPlayer.remove(player1Id);

            if (game.getPlayer2() != null) {
                UUID player2Id = game.getPlayer2().getPlayerId();
                cancelReconnectTimer(player2Id);
                activeSessionByPlayer.remove(player2Id);
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
