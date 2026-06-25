package com.navalrivals.infra.security.dto;

import com.navalrivals.domain.user.dto.UserResponse;

public record AuthResponse(
        String accessToken,
        UserResponse user
) {
}
