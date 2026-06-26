package com.navalrivals.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(

        @NotBlank(message = "Não pode ser vazio")
        String nickname,

        @NotBlank(message = "Não pode ser vazio")
        @Email(message = "Formato de e-mail inválido")
        String email,

        @Size(min = 6, message = "Precisa ter no mínimo 6 caracteres")
        @NotBlank(message = "Não pode ser vazio")
        String password
) {
}
