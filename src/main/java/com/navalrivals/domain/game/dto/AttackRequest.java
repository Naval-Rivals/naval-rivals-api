package com.navalrivals.domain.game.dto;

import jakarta.validation.constraints.NotNull;

public record AttackRequest(
        @NotNull(message = "Célula não pode ser vazia")
        String cell,
        String type
) {
}
