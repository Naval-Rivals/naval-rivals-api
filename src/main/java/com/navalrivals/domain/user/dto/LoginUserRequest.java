package com.navalrivals.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginUserRequest(

        @NotBlank(message = "Não pode ser vazio")
        String login,

        @NotBlank(message = "Não pode ser vazio")
        String password
) {
}
