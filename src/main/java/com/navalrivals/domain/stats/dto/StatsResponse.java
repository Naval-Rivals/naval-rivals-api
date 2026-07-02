package com.navalrivals.domain.stats.dto;

import com.navalrivals.domain.stats.entity.Stats;

public record StatsResponse(
        int totalGames,
        int victories,
        int defeats,
        String winRate
) {
    public StatsResponse(Stats stats){
        this(stats.getTotalGames(), stats.getVictories(), stats.getDefeats(), stats.getWinRate());
    }
}
