package com.navalrivals.domain.room.controller;

import com.navalrivals.domain.room.dto.JoinRoomRequest;
import com.navalrivals.domain.room.dto.RoomResponse;
import com.navalrivals.domain.room.service.RoomService;
import com.navalrivals.domain.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    public ResponseEntity<RoomResponse> create(
            @AuthenticationPrincipal User user,
            UriComponentsBuilder uriBuilder
    ) {
        var response = roomService.create(user);
        var uri = uriBuilder.path("/rooms/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(uri).body(response);
    }

    @PostMapping("/join")
    public ResponseEntity<RoomResponse> join(
            @RequestBody @Valid JoinRoomRequest request,
            @AuthenticationPrincipal User user
    ) {
        var response = roomService.joinByCode(request, user);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomResponse> getById(@PathVariable UUID roomId) {
        var response = roomService.getById(roomId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> leave(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal User user
    ) {
        roomService.leave(roomId, user);
        return ResponseEntity.noContent().build();
    }
}
