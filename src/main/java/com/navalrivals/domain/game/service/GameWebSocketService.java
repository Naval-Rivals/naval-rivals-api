package com.navalrivals.domain.game.service;

import com.navalrivals.domain.game.dto.GamePlacementEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class GameWebSocketService {

    private final int turnTimeoutSeconds;
    private final SimpMessagingTemplate messagingTemplate;

    public GameWebSocketService(
            @Value("${game.turn-timeout-seconds}") int turnTimeoutSeconds,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.turnTimeoutSeconds = turnTimeoutSeconds;
        this.messagingTemplate = messagingTemplate;
    }

    public void notifyOpponentReady(UUID gameId, UUID playerId){
        log.info("[WS OUT] /topic/game/{}/placement → OPPONENT_READY — playerId={}", gameId, playerId);
        var event = new GamePlacementEvent("OPPONENT_READY", gameId, playerId, null, null);
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/placement", event);
    }

    public void notifyGameStarted(UUID gameId, UUID playerId, UUID firstTurn){
        log.info("[WS OUT] /topic/game/{}/placement → GAME_STARTED — playerId={}, firstTurn={}", gameId, playerId, firstTurn);
        var event = new GamePlacementEvent("GAME_STARTED", gameId, playerId, firstTurn, turnTimeoutSeconds);
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/placement", event);
    }

}
