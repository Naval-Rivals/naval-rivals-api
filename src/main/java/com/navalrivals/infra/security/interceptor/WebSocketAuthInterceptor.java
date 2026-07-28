package com.navalrivals.infra.security.interceptor;

import com.navalrivals.domain.user.entity.User;
import com.navalrivals.domain.user.repository.UserRepository;
import com.navalrivals.infra.exception.exceptions.NotFoundException;
import com.navalrivals.infra.security.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final TokenService tokenService;
    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel){
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())){
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")){
                log.warn("[WS AUTH] Conexão WebSocket rejeitada — token não fornecido, sessionId={}", accessor.getSessionId());
                throw new SecurityException("Token não fornecido");
            }

            String token = authHeader.replace("Bearer ", "");

            try {
                String email = tokenService.validateToken(token);

                var user = (User) userRepository.findByEmail(email)
                        .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));

                var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()) {
                    @Override
                    public String getName() {
                        return user.getId().toString();
                    }
                };

                accessor.setUser(authentication);
                log.info("[WS AUTH] Conexão WebSocket aceita — userId={}, sessionId={}", user.getId(), accessor.getSessionId());
            } catch (Exception e) {
                log.warn("[WS AUTH] Conexão WebSocket rejeitada — token inválido, sessionId={}, motivo: {}", accessor.getSessionId(), e.getMessage());
                throw e;
            }
        }
        return message;
    }
}
