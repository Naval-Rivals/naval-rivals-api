package com.navalrivals.domain.game.controller;

import com.navalrivals.domain.game.dto.GameResultResponse;
import com.navalrivals.domain.game.dto.GameStateResponse;
import com.navalrivals.domain.game.dto.PlaceShipRequest;
import com.navalrivals.domain.game.service.GameService;
import com.navalrivals.domain.ship.dto.ShipRequest;
import com.navalrivals.domain.ship.entity.Ship;
import com.navalrivals.domain.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/games")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;

    @PostMapping("/{gameId}/ships")
    public ResponseEntity<Void> placeShips(
            @PathVariable UUID gameId,
            @RequestBody @Valid PlaceShipRequest requestBody,
            @AuthenticationPrincipal User user
    ) {
        MDC.put("gameId", gameId.toString());
        try {
            List<Ship> ships = requestBody.ships().stream()
                    .map(this::toShip)
                    .toList();

            gameService.placeShips(gameId, user, ships);
            return ResponseEntity.ok().build();
        } finally {
            MDC.remove("gameId");
        }
    }

    @GetMapping("/{gameId}/result")
    public ResponseEntity<GameResultResponse> getResult(
            @PathVariable UUID gameId
    ) {
        MDC.put("gameId", gameId.toString());
        try {
            var response = gameService.getGameResult(gameId);
            return ResponseEntity.ok(response);
        } finally {
            MDC.remove("gameId");
        }
    }

    @GetMapping("/{gameId}/state")
    public ResponseEntity<GameStateResponse> getState(
            @PathVariable UUID gameId,
            @AuthenticationPrincipal User user
    ) {
        MDC.put("gameId", gameId.toString());
        try {
            var response = gameService.getGameState(gameId, user);
            return ResponseEntity.ok(response);
        } finally {
            MDC.remove("gameId");
        }
    }

    private Ship toShip(ShipRequest request) {
        return new Ship(request.type(), request.positions(), false);
    }
}
