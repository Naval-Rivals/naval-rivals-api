package com.navalrivals.infra.security.interceptor;

import com.navalrivals.domain.user.entity.User;
import com.navalrivals.domain.user.repository.UserRepository;
import com.navalrivals.infra.exception.exceptions.NotFoundException;
import com.navalrivals.infra.security.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final TokenService tokenService;
    private final UserRepository userRepository;

    /**
     * Intercepta TODAS as mensagens STOMP que entram no servidor.
     * Só age no CONNECT (momento em que o cliente abre a conexão).
     *
     * Fluxo:
     * 1. Pega o header "Authorization" enviado pelo frontend no CONNECT frame
     * 2. Remove o prefixo "Bearer "
     * 3. Valida o JWT e extrai o email (subject)
     * 4. Busca o User no banco
     * 5. Seta o Principal (Authentication) na sessão WebSocket
     *
     * Se o token for inválido ou ausente, lança exceção — o STOMP retorna ERROR frame
     * e a conexão é recusada.
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel){
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())){
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")){
                throw new SecurityException("Token não fornecido");
            }

            String token = authHeader.replace("Bearer ", "");
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
        }
        return message;
    }
}
