package com.navalrivals.domain.user.dto;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String nickname,
        String email
) {
}
