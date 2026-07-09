package com.navalrivals.domain.game.listener;

import com.navalrivals.domain.game.service.GameDisconnectService;
import com.navalrivals.domain.room.service.RoomSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Listener de eventos do Spring WebSocket.
 *
 * O Spring publica SessionDisconnectEvent automaticamente quando uma conexão
 * STOMP é encerrada (close, timeout, erro de rede, etc.).
 *
 * Este listener pega o sessionId e delega para:
 * - GameDisconnectService: trata desconexão durante partidas
 * - RoomSessionService: trata desconexão do host na tela de espera
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketDisconnectListener {

    private final GameDisconnectService gameDisconnectService;
    private final RoomSessionService roomSessionService;

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        log.debug("WebSocket desconectado: sessionId={}", sessionId);
        gameDisconnectService.handleDisconnect(sessionId);
        roomSessionService.handleDisconnect(sessionId);
    }
}
