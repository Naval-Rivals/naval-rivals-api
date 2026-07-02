package com.navalrivals.domain.ranking.dto;

import java.util.UUID;

public record RankingResponse(
        Long position,
        UUID userId,
        String nickname,
        int victories,
        int totalGames,
        String winRate
) {
}
