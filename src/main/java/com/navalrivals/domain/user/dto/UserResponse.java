package com.navalrivals.domain.user.dto;

import com.navalrivals.domain.user.entity.User;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String nickname,
        String email
) {
    public UserResponse(User user) {
        this(user.getId(), user.getNickname(), user.getEmail());
    }
}
