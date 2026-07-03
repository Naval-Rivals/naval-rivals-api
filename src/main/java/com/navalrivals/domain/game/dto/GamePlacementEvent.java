package com.navalrivals.domain.game.dto;

import java.util.UUID;

public record GamePlacementEvent(
        String event,
        UUID gameId,
        UUID playerId,
        UUID firstTurn,
        Integer turnTimeout
) {
}
