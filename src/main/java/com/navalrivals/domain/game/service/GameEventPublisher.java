package com.navalrivals.domain.game.service;

import com.navalrivals.domain.game.dto.GameEvent;
import com.navalrivals.domain.game.util.CellConverter;
import com.navalrivals.domain.ship.entity.Ship;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Centraliza a publicação de TODOS os eventos de jogo no tópico /topic/game/{gameId}/events.
 *
 * Cada método corresponde a um tipo de evento.
 * Todos usam SimpMessagingTemplate para enviar ao tópico STOMP.
 */
@Service
@RequiredArgsConstructor
public class GameEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    private void publish(UUID gameId, String event, Map<String, Object> payload) {
        var gameEvent = new GameEvent(event, gameId, payload);
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/events", gameEvent);
    }

    /**
     * ATTACK_RESULT — publicado após cada ataque.
     * Informa a ambos os jogadores o que aconteceu.
     */
    public void publishAttackResult(UUID gameId, UUID attackerId, String cell, boolean hit) {
        publish(gameId, "ATTACK_RESULT", Map.of(
                "attackerId", attackerId,
                "cell", cell,
                "hit", hit
        ));
    }

    /**
     * TURN_CHANGE — publicado após cada ataque (ou timeout).
     * Informa de quem é o próximo turno e o timeout.
     * O frontend inicia o countdown visual localmente.
     */
    public void publishTurnChange(UUID gameId, UUID nextTurn, int turnTimeout) {
        publish(gameId, "TURN_CHANGE", Map.of(
                "nextTurn", nextTurn,
                "turnTimeout", turnTimeout
        ));
    }

    /**
     * TURN_TIMEOUT — publicado quando o timer do turno expira no backend.
     * O turno passa automaticamente para o outro jogador.
     */
    public void publishTurnTimeout(UUID gameId, UUID timedOutPlayer, UUID nextTurn) {
        publish(gameId, "TURN_TIMEOUT", Map.of(
                "timedOutPlayer", timedOutPlayer,
                "nextTurn", nextTurn
        ));
    }

    /**
     * SHIP_SUNK — publicado quando um navio é completamente afundado.
     * Informa qual tipo e as posições (para o frontend revelar o navio no grid).
     */
    public void publishShipSunk(UUID gameId, UUID ownerId, Ship ship) {
        List<String> cells = ship.getPositions().stream()
                .map(CellConverter::toCell)
                .toList();

        publish(gameId, "SHIP_SUNK", Map.of(
                "ownerId", ownerId,
                "shipType", ship.getType().name(),
                "positions", cells
        ));
    }

    /**
     * GAME_OVER — publicado quando a partida termina.
     * Reasons: "ALL_SHIPS_SUNK", "OPPONENT_DISCONNECTED"
     */
    public void publishGameOver(UUID gameId, UUID winnerId, UUID loserId, String reason) {
        publish(gameId, "GAME_OVER", Map.of(
                "winnerId", winnerId,
                "loserId", loserId,
                "reason", reason
        ));
    }

    /**
     * OPPONENT_DISCONNECTED — publicado quando um jogador desconecta do WebSocket.
     * Informa o timeout para reconexão (30s). Se não reconectar, perde.
     */
    public void publishOpponentDisconnected(UUID gameId, UUID disconnectedPlayerId, int reconnectTimeout) {
        publish(gameId, "OPPONENT_DISCONNECTED", Map.of(
                "disconnectedPlayerId", disconnectedPlayerId,
                "reconnectTimeout", reconnectTimeout
        ));
    }

    /**
     * OPPONENT_RECONNECTED — publicado quando o jogador desconectado volta.
     * O jogo continua normalmente.
     */
    public void publishOpponentReconnected(UUID gameId, UUID reconnectedPlayerId) {
        publish(gameId, "OPPONENT_RECONNECTED", Map.of(
                "reconnectedPlayerId", reconnectedPlayerId
        ));
    }
}
