package com.navalrivals.domain.ranking.dto;

import java.util.UUID;

public interface RankingProjection {
    Long getPosition();
    UUID getUserId();
    String getNickname();
    int getVictories();
    int getTotalGames();
    String getWinRate();
}
