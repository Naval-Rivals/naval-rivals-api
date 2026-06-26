package com.navalrivals.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateNicknameRequest(

        @Size(max = 150, message = "Pode ter no máximo 150 caracteres")
        @NotBlank(message = "Não pode ser vazio")
        String nickname
) {
}
