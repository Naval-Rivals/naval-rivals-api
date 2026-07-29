package com.navalrivals.domain.user.dto;

import com.navalrivals.domain.stats.dto.StatsResponse;
import com.navalrivals.domain.user.entity.User;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String nickname,
        String email
//        StatsResponse stats
) {
    public UserResponse(User user) {
        this(user.getId(), user.getNickname(), user.getEmail()
//             user.getStats() != null ? new StatsResponse(user.getStats()) : null
             );
    }
}
