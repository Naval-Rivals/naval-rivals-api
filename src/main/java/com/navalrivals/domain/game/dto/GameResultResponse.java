package com.navalrivals.domain.game.dto;

import com.navalrivals.domain.game.entity.GameResult;
import com.navalrivals.domain.game.enums.GameMode;
import com.navalrivals.domain.game.enums.GameStatus;
import com.navalrivals.domain.user.dto.UserResponse;

import java.time.Instant;
import java.util.UUID;

public record GameResultResponse(
        UUID gameId,
        String roomCode,
        GameStatus status,
        GameMode gameMode,
        UserResponse winner,
        UserResponse loser,
        Long durationSeconds,
        PlayerGameStats winnerStats,
        PlayerGameStats loserStats,
        Instant finishedAt
) {
    public GameResultResponse(GameResult result) {
        this(
                result.getId(),
                result.getRoomCode(),
                result.getStatus(),
                result.getGameMode(),
                new UserResponse(result.getWinner()),
                new UserResponse(result.getLoser()),
                result.getDurationSeconds(),
                new PlayerGameStats(
                        result.getWinnerShots(),
                        result.getWinnerHits(),
                        result.getWinnerMisses(),
                        result.getWinnerShipsDestroyed()
                ),
                new PlayerGameStats(
                        result.getLoserShots(),
                        result.getLoserHits(),
                        result.getLoserMisses(),
                        result.getLoserShipsDestroyed()
                ),
                result.getFinishedAt()
        );
    }
}
