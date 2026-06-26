package com.navalrivals.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginUserRequest(

        @NotBlank(message = "Não pode ser vazio")
        @Email(message = "Formato de e-mail inválido")
        String email,

        @NotBlank(message = "Não pode ser vazio")
        String password
) {
}
