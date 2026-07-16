package com.navalrivals.domain.room.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class LobbySSEService {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * Registra um novo cliente SSE no lobby.
     * Timeout de 5 minutos — o frontend reconecta automaticamente (comportamento nativo do EventSource).
     */
    public SseEmitter subscribe(){
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L );

        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        return emitter;
    }

    /**
     * Notifica todos os clientes inscritos no lobby que houve atualização.
     * Substitui o antigo messagingTemplate.convertAndSend("/topic/lobby", ...).
     */
    public void notifyLobbyUpdated(){
        List<SseEmitter> deadEmitters = new ArrayList<>();

        for (SseEmitter emitter : emitters){
            try{
                emitter.send(SseEmitter.event()
                        .name("LOBBY_UPDATED")
                        .data("{\"event\":\"LOBBY_UPDATED\"}")
                );
            }catch (IOException e){
                deadEmitters.add(emitter);
            }
        }
        emitters.removeAll(deadEmitters);
    }
}
