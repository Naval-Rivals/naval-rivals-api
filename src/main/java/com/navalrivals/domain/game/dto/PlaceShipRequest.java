package com.navalrivals.domain.game.dto;

import com.navalrivals.domain.ship.dto.ShipRequest;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PlaceShipRequest(
        @NotNull
        List<ShipRequest> ships
) {
}
