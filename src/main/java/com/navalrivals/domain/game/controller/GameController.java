package com.navalrivals.domain.game.controller;

import com.navalrivals.domain.game.dto.GameResultResponse;
import com.navalrivals.domain.game.dto.PlaceShipRequest;
import com.navalrivals.domain.game.service.GameService;
import com.navalrivals.domain.ship.dto.ShipRequest;
import com.navalrivals.domain.ship.entity.Ship;
import com.navalrivals.domain.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
        List<Ship> ships = requestBody.ships().stream()
                .map(this::toShip)
                .toList();

        gameService.placeShips(gameId, user, ships);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{gameId}/result")
    public ResponseEntity<GameResultResponse> getResult(
            @PathVariable UUID gameId
    ) {
        var response = gameService.getGameResult(gameId);
        return ResponseEntity.ok(response);
    }

    private Ship toShip(ShipRequest request) {
        return new Ship(request.type(), request.positions(), false);
    }
}
