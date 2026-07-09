package com.navalrivals.domain.room.service;

import com.navalrivals.domain.room.dto.RoomEventMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Responsável por publicar eventos de sala no tópico WebSocket.
 *
 * O SimpMessagingTemplate é o bean do Spring que permite enviar mensagens
 * para tópicos STOMP programaticamente (sem precisar de um @MessageMapping).
 *
 * Tópico: /topic/room/{roomId}
 * Todos os clientes inscritos nesse tópico receberão o evento.
 */

@Service
@RequiredArgsConstructor
public class RoomWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Publica evento PLAYER_JOINED — chamado quando um jogador entra na sala.
     */
    public void notifyPlayerJoined(UUID roomId, UUID userId, String nickname){
        var message = new RoomEventMessage("PLAYER_JOINED", roomId, userId, nickname, null);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
    }

    /**
     * Publica evento ROOM_READY — chamado quando a sala fica cheia (2 jogadores).
     * O frontend usa isso para saber que pode iniciar o jogo.
     */
    public void notifyRoomReady(UUID roomId, UUID userId, String nickname, UUID gameId){
        var message = new RoomEventMessage("ROOM_READY", roomId, userId, nickname, gameId);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
    }

    /**
     * Publica evento PLAYER_LEFT — chamado quando um jogador sai da sala.
     */
    public void notifyPlayerLeft(UUID roomId, UUID userId, String nickname){
        var message = new RoomEventMessage("PLAYER_LEFT", roomId, userId, nickname, null);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
    }

    /**
     * Publica evento LOBBY_UPDATED — sinal para clientes inscritos no lobby
     * re-buscarem a lista de salas via GET /rooms.
     *
     * Tópico: /topic/lobby
     */
    public void notifyLobbyUpdated() {
        messagingTemplate.convertAndSend("/topic/lobby", (Object) Map.of("event", "LOBBY_UPDATED"));
    }
}
