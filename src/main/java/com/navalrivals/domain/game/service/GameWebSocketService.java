package com.navalrivals.domain.game.service;

import com.navalrivals.domain.game.dto.GamePlacementEvent;
import com.navalrivals.infra.cluster.ClusterEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class GameWebSocketService {

    private final int turnTimeoutSeconds;
    private final ClusterEventPublisher eventPublisher;

    public GameWebSocketService(
            @Value("${game.turn-timeout-seconds}") int turnTimeoutSeconds,
            ClusterEventPublisher eventPublisher
    ) {
        this.turnTimeoutSeconds = turnTimeoutSeconds;
        this.eventPublisher = eventPublisher;
    }

    public void notifyOpponentReady(UUID gameId, UUID playerId){
        log.info("[WS OUT] /topic/game/{}/placement → OPPONENT_READY — playerId={}", gameId, playerId);
        var event = new GamePlacementEvent("OPPONENT_READY", gameId, playerId, null, null);
        eventPublisher.publish("/topic/game/" + gameId + "/placement", event);
    }

    public void notifyGameStarted(UUID gameId, UUID playerId, UUID firstTurn){
        log.info("[WS OUT] /topic/game/{}/placement → GAME_STARTED — playerId={}, firstTurn={}", gameId, playerId, firstTurn);
        var event = new GamePlacementEvent("GAME_STARTED", gameId, playerId, firstTurn, turnTimeoutSeconds);
        eventPublisher.publish("/topic/game/" + gameId + "/placement", event);
    }

}
