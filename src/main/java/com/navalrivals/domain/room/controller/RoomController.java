package com.navalrivals.domain.room.controller;

import com.navalrivals.domain.room.dto.CreateRoomRequest;
import com.navalrivals.domain.room.dto.JoinRoomRequest;
import com.navalrivals.domain.room.dto.RoomResponse;
import com.navalrivals.domain.room.service.RoomService;
import com.navalrivals.domain.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    public ResponseEntity<RoomResponse> create(
            @RequestBody(required = false) CreateRoomRequest request,
            @AuthenticationPrincipal User user,
            UriComponentsBuilder uriBuilder
    ) {
        log.info("[ROOM] POST /rooms — userId={}", user.getId());
        var response = roomService.create(user, request);
        var uri = uriBuilder.path("/rooms/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(uri).body(response);
    }

    @PostMapping("/join")
    public ResponseEntity<RoomResponse> join(
            @RequestBody @Valid JoinRoomRequest request,
            @AuthenticationPrincipal User user
    ) {
        log.info("[ROOM] POST /rooms/join — userId={}, code={}", user.getId(), request.code());
        var response = roomService.joinByCode(request, user);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<RoomResponse>> listWaiting() {
        var rooms = roomService.listWaitingRooms();
        return ResponseEntity.ok(rooms);
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomResponse> getById(@PathVariable UUID roomId) {
        MDC.put("roomId", roomId.toString());
        try {
            var response = roomService.getById(roomId);
            return ResponseEntity.ok(response);
        } finally {
            MDC.remove("roomId");
        }
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> leave(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal User user
    ) {
        MDC.put("roomId", roomId.toString());
        try {
            log.info("[ROOM] DELETE /rooms/{} — userId={}", roomId, user.getId());
            roomService.leave(roomId, user);
            return ResponseEntity.noContent().build();
        } finally {
            MDC.remove("roomId");
        }
    }
}
