package com.navalrivals.domain.game.dto;

import com.navalrivals.domain.game.enums.AbilityType;
import jakarta.validation.constraints.NotNull;

public record AbilityRequest(
        @NotNull(message = "Tipo de habilidade é obrigatório")
        AbilityType ability,
        String cell
) {
}
