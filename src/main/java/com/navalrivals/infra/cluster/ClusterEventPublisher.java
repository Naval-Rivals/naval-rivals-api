package com.navalrivals.infra.cluster;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navalrivals.domain.game.dto.GameEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ClusterEventPublisher {

    private static final String CHANNEL = "naval-rivals:ws-events";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void publish(String destination, Object payload) {
        try {
            var envelope = new ClusterMessage(destination, objectMapper.writeValueAsString(payload));
            redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(envelope));
        } catch (Exception e) {
            log.error("[CLUSTER] Falha ao publicar evento no Redis — destination={}", destination, e);
        }
    }

    public void publishToUser(String userId, String destination, Object payload) {
        try {
            var envelope = new ClusterMessage(destination, objectMapper.writeValueAsString(payload), userId);
            redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(envelope));
        } catch (Exception e) {
            log.error("[CLUSTER] Falha ao publicar evento de usuário no Redis — destination={}", destination, e);
        }

    }
}