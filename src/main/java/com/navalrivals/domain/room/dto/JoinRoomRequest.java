package com.navalrivals.domain.room.dto;

import jakarta.validation.constraints.NotBlank;

public record JoinRoomRequest(
        @NotBlank(message = "Código da sala não pode ser vazio")
        String code
) {
}
