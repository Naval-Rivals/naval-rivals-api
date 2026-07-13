package com.navalrivals.domain.game.dto;

import com.navalrivals.domain.game.entity.GameResult;
import com.navalrivals.domain.game.enums.GameStatus;
import com.navalrivals.domain.user.entity.User;

import java.time.Instant;
import java.util.UUID;

public record MatchHistoryResponse(
        UUID gameId,
        String roomCode,
        GameStatus status,
        String opponentNickname,
        UUID opponentId,
        boolean victory,
        Long durationSeconds,
        PlayerGameStats myStats,
        PlayerGameStats opponentStats,
        Instant finishedAt,
        String gameMode
) {
    public static MatchHistoryResponse from(GameResult result, UUID userId) {
        boolean isWinner = result.getWinner().getId().equals(userId);

        User opponent = isWinner ? result.getLoser() : result.getWinner();

        PlayerGameStats myStats = isWinner
                ? new PlayerGameStats(result.getWinnerShots(), result.getWinnerHits(), result.getWinnerMisses(), result.getWinnerShipsDestroyed())
                : new PlayerGameStats(result.getLoserShots(), result.getLoserHits(), result.getLoserMisses(), result.getLoserShipsDestroyed());

        PlayerGameStats opponentStats = isWinner
                ? new PlayerGameStats(result.getLoserShots(), result.getLoserHits(), result.getLoserMisses(), result.getLoserShipsDestroyed())
                : new PlayerGameStats(result.getWinnerShots(), result.getWinnerHits(), result.getWinnerMisses(), result.getWinnerShipsDestroyed());

        return new MatchHistoryResponse(
                result.getId(),
                result.getRoomCode(),
                result.getStatus(),
                opponent.getNickname(),
                opponent.getId(),
                isWinner,
                result.getDurationSeconds(),
                myStats,
                opponentStats,
                result.getFinishedAt(),
                result.getGameMode().toString()
        );
    }
}
