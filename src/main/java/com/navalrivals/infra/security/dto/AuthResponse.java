package com.navalrivals.infra.security.dto;

import com.navalrivals.domain.user.entity.User;

import java.util.UUID;

public record AuthResponse(
        UUID id,
        String nickname,
        String email,
        String token
) {
    public AuthResponse(String token, User user){
        this(user.getId(), user.getNickname(), user.getEmail(), token);
    }
}
