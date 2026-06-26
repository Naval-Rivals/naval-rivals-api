package com.navalrivals.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(

        @Size(max = 150, message = "Pode ter no máximo 150 caracteres")
        @NotBlank(message = "Não pode ser vazio")
        String nickname,

        @NotBlank(message = "Não pode ser vazio")
        @Email(message = "Formato de e-mail inválido")
        String email,

        @Size(min = 6, message = "Precisa ter no mínimo 6 caracteres")
        @Size(max = 254, message = "Não pode ultrapassar o tamanho de 254 caracteres")
        @NotBlank(message = "Não pode ser vazio")
        String password
) {
}
