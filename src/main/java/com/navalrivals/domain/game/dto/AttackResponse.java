package com.navalrivals.domain.game.dto;

import java.util.UUID;

public record AttackResponse(
        UUID gameId,
        UUID attackerId,
        String cell,
        boolean hit,
        boolean sunk,
        String shipType,
        boolean gameOver,
        UUID winnerId,
        UUID nextTurn,
        String attackType
) {
}
