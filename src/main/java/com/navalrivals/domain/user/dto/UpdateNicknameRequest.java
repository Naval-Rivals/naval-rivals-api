package com.navalrivals.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateNicknameRequest(
        @NotBlank(message = "Não pode ser vazio")
        String nickname
) {
}
