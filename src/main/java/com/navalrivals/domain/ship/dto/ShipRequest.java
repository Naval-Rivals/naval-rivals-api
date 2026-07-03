package com.navalrivals.domain.ship.dto;

import com.navalrivals.domain.position.entity.Position;
import com.navalrivals.domain.ship.enums.ShipType;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ShipRequest(
        @NotNull(message = "Não pode ser vazio")
        ShipType type,
        @NotNull(message = "Não pode ser vazio")
        List<Position> positions
) {
}
