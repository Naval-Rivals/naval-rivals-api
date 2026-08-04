package com.navalrivals.infra.cluster;

import com.navalrivals.domain.room.service.LobbySSEService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class LobbySSEClusterListener implements MessageListener {

    private final LobbySSEService lobbySSEService;

    @Override
    public void onMessage(Message message, byte @Nullable [] pattern) {
        try {
            log.debug("[CLUSTER] Evento de lobby recebido via Redis Pub/Sub");
            lobbySSEService.broadcastLocal();
        } catch (Exception e) {
            log.debug("[CLUSTER] Erro ao processar evento de lobby (clientes SSE desconectados): {}", e.getMessage());
        }
    }
}
