package com.navalrivals.infra.cluster;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class ClusterEventListener implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte @Nullable [] pattern) {
        try {
            var envelope = objectMapper.readValue(message.getBody(), ClusterMessage.class);

            // Deserializar o payload JSON para Map para que o MappingJackson2MessageConverter
            // do STOMP serialize corretamente (não como JsonNode com metadados)
            Object payload = objectMapper.readValue(envelope.payloadJson(), Map.class);

            if (envelope.isUserSpecific()) {
                log.info("[CLUSTER] Entregando evento para usuário — userId={}, destination={}",
                        envelope.userId(), envelope.destination());
                messagingTemplate.convertAndSendToUser(
                        envelope.userId(),
                        envelope.destination(),
                        payload
                );
            } else {
                log.info("[CLUSTER] Entregando broadcast — destination={}, payload={}",
                        envelope.destination(), envelope.payloadJson());
                messagingTemplate.convertAndSend(
                        envelope.destination(),
                        payload
                );
            }
        } catch (Exception e) {
            log.error("[CLUSTER] Falha ao processar evento recebido do Redis", e);
        }
    }
}
