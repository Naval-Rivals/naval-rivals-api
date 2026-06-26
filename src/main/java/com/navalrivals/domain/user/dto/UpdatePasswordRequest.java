package com.navalrivals.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePasswordRequest(

        @NotBlank(message = "Não pode ser vazio")
        String currentPassword,

        @Size(min = 6, message = "Precisa ter no mínimo 6 caracteres")
        @Size(max = 254, message = "Não pode ultrapassar o tamanho de 254 caracteres")
        @NotBlank(message = "Não pode ser vazio")
        String newPassword
) {
}
