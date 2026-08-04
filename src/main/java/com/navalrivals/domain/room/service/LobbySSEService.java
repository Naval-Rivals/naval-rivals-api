package com.navalrivals.domain.room.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class LobbySSEService {

    private static final String LOBBY_CHANNEL = "naval-rivals:lobby-events";

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final StringRedisTemplate redisTemplate;

    /**
     * Registra um novo cliente SSE no lobby.
     * Timeout de 5 minutos — o frontend reconecta automaticamente (comportamento nativo do EventSource).
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);

        emitters.add(emitter);

        Runnable removeEmitter = () -> emitters.remove(emitter);
        emitter.onCompletion(removeEmitter);
        emitter.onTimeout(removeEmitter);
        emitter.onError(e -> removeEmitter.run());

        return emitter;
    }

    /**
     * Notifica todos os clientes inscritos no lobby que houve atualização.
     * Publica no Redis para que todos os pods recebam.
     */
    public void notifyLobbyUpdated() {
        redisTemplate.convertAndSend(LOBBY_CHANNEL, "LOBBY_UPDATED");
    }

    /**
     * Chamado pelo LobbySSEClusterListener ao receber evento do Redis.
     * Faz o broadcast apenas para os emitters DESTE pod.
     */
    public void broadcastLocal() {
        List<SseEmitter> deadEmitters = new ArrayList<>();

        log.info("[SSE OUT] /lobby/events → LOBBY_UPDATED | {} clientes conectados", emitters.size());

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("LOBBY_UPDATED")
                        .data("{\"event\":\"LOBBY_UPDATED\"}")
                );
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }

        if (!deadEmitters.isEmpty()) {
            emitters.removeAll(deadEmitters);
            log.debug("[SSE] {} emitters mortos removidos", deadEmitters.size());
        }
    }

}
