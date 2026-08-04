package com.navalrivals.domain.game.service;

import com.navalrivals.domain.game.dto.GameEvent;
import com.navalrivals.domain.game.util.CellConverter;
import com.navalrivals.domain.ship.entity.Ship;
import com.navalrivals.infra.cluster.ClusterEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class GameEventPublisher {

    private final ClusterEventPublisher eventPublisher;

    private void publish(UUID gameId, String event, Map<String, Object> payload) {
        var gameEvent = new GameEvent(event, gameId, payload);
        log.info("[WS OUT] /topic/game/{}/events → {} | payload={}", gameId, event, payload);
        eventPublisher.publish("/topic/game/" + gameId + "/events", gameEvent);
    }

    /**
     * Publica evento para um jogador específico (usado para radar, que só o jogador vê).
     */
    private void publishToUser(UUID gameId, UUID playerId, String event, Map<String, Object> payload) {
        var gameEvent = new GameEvent(event, gameId, payload);
        log.info("[WS OUT] /user/{}/topic/game/{}/events → {} | payload={}", playerId, gameId, event, payload);
        eventPublisher.publishToUser(
                playerId.toString(),
                "/topic/game/" + gameId + "/events",
                gameEvent
        );
    }

    /**
     * ATTACK_RESULT — publicado após cada ataque.
     * Informa a ambos os jogadores o que aconteceu.
     */
    public void publishAttackResult(UUID gameId, UUID attackerId, String cell, boolean hit, String attackType) {
        publish(gameId, "ATTACK_RESULT", Map.of(
                "attackerId", attackerId,
                "cell", cell,
                "hit", hit,
                "attackType", attackType
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

    // ========== EVENTOS DE HABILIDADES (Modo Tático) ==========

    /**
     * RADAR_RESULT — publicado APENAS para o jogador que usou o radar.
     * Retorna as células no bloco 3x3 que contêm navio.
     */
    public void publishRadarResult(UUID gameId, UUID playerId, String centerCell, List<String> revealedCells) {
        publishToUser(gameId, playerId, "RADAR_RESULT", Map.of(
                "playerId", playerId,
                "centerCell", centerCell,
                "revealedCells", revealedCells
        ));
        // Notifica o oponente que radar foi usado (sem revelar resultado)
        publish(gameId, "RADAR_USED", Map.of(
                "playerId", playerId,
                "centerCell", centerCell
        ));
    }

    /**
     * SHIELD_ACTIVATED — notifica ambos os jogadores que o escudo foi ativado.
     */
    public void publishShieldActivated(UUID gameId, UUID playerId, int remainingCharges) {
        publish(gameId, "SHIELD_ACTIVATED", Map.of(
                "playerId", playerId,
                "remainingCharges", remainingCharges
        ));
    }

    /**
     * SHIELD_BLOCKED — notifica que um tiro foi bloqueado pelo escudo.
     */
    public void publishShieldBlocked(UUID gameId, UUID defenderId, String cell) {
        publish(gameId, "SHIELD_BLOCKED", Map.of(
                "defenderId", defenderId,
                "cell", cell
        ));
    }

    /**
     * EMP_ACTIVATED — notifica ambos que EMP foi usado.
     */
    public void publishEmpActivated(UUID gameId, UUID playerId, UUID targetId, int disabledTurns) {
        publish(gameId, "EMP_ACTIVATED", Map.of(
                "playerId", playerId,
                "targetId", targetId,
                "disabledTurns", disabledTurns
        ));
    }
}
