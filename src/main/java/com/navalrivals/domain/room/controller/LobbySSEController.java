package com.navalrivals.domain.room.controller;

import com.navalrivals.domain.room.service.LobbySSEService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/lobby")
@RequiredArgsConstructor
public class LobbySSEController {

    private final LobbySSEService lobbySSEService;

    /**
     * Endpoint SSE para receber notificações de lobby em tempo real.
     * O frontend conecta via EventSource e recebe eventos "LOBBY_UPDATED".
     */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(){
        return lobbySSEService.subscribe();
    }


}
