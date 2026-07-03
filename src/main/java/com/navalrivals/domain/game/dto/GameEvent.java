package com.navalrivals.domain.game.dto;

import java.util.Map;
import java.util.UUID;

public record GameEvent(
        String event,
        UUID gameId,
        Map<String, Object> payload
) {
}
