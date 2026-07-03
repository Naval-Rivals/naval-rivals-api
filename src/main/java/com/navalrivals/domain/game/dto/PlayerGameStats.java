package com.navalrivals.domain.game.dto;

public record PlayerGameStats(
        int shots,
        int hits,
        int misses,
        int shipsDestroyed
) {
}
