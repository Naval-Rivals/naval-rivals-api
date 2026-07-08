package com.navalrivals.domain.game.service;

import com.navalrivals.domain.game.dto.GamePlacementEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Responsável por publicar eventos de jogo via WebSocket.
 *
 * Usa SimpMessagingTemplate para enviar mensagens programaticamente
 * para tópicos STOMP, sem depender de @MessageMapping.
 *
 * Tópico de posicionamento: /topic/game/{gameId}/placement
 * Clientes inscritos nesse tópico receberão os eventos da fase de placement.
 */

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

    /**
     * Publica OPPONENT_READY — informa ao oponente que este jogador já posicionou.
     *
     * Chamado SEMPRE que um jogador termina de posicionar (exceto quando ambos
     * já terminaram, nesse caso vai direto para GAME_STARTED).
     *
     * @param gameId   ID da partida
     * @param playerId ID do jogador que acabou de posicionar
     */
    public void notifyOpponentReady(UUID gameId, UUID playerId){
        var event = new GamePlacementEvent("OPPONENT_READY", gameId, playerId, null, null);
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/placement", event);
    }

    /**
     * Publica GAME_STARTED — informa que ambos posicionaram e o jogo começou.
     *
     * Enviado quando o segundo jogador finaliza o posicionamento.
     * Contém quem joga primeiro (firstTurn) e o timeout de turno.
     *
     * @param gameId    ID da partida
     * @param playerId  ID do jogador que foi o último a posicionar
     * @param firstTurn ID do jogador que tem o primeiro turno
     */
    public void notifyGameStarted(UUID gameId, UUID playerId, UUID firstTurn){
        var event = new GamePlacementEvent("GAME_STARTED", gameId, playerId, firstTurn, turnTimeoutSeconds);
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/placement", event);
    }

}
