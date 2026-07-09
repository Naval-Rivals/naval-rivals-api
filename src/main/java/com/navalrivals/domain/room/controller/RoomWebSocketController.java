package com.navalrivals.domain.room.controller;

import com.navalrivals.domain.room.service.RoomSessionService;
import com.navalrivals.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

/**
 * Controller WebSocket para ações de sala em tempo real.
 *
 * Endpoints STOMP:
 * - /app/room/{roomId}/register → registra sessão do host para tracking de desconexão
 */
@Controller
@RequiredArgsConstructor
public class RoomWebSocketController {

    private final RoomSessionService roomSessionService;

    /**
     * Registra a sessão WebSocket do host na sala.
     *
     * O frontend DEVE chamar isso após criar a sala e se inscrever no tópico /topic/room/{roomId}.
     * Permite que o servidor detecte quando o host fecha a aba e delete a sala automaticamente.
     */
    @MessageMapping("/room/{roomId}/register")
    public void register(
            @DestinationVariable UUID roomId,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        User user = extractUser(principal);
        String sessionId = headerAccessor.getSessionId();
        roomSessionService.registerHostSession(sessionId, roomId, user.getId());
    }

    private User extractUser(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken auth) {
            return (User) auth.getPrincipal();
        }
        throw new IllegalStateException("Principal não é UsernamePasswordAuthenticationToken");
    }
}
