package com.navalrivals.domain.room.dto;

import java.util.UUID;

public record RoomEventMessage(
        String event,
        UUID roomId,
        UUID userId,
        String nickname,
        UUID gameId
) {
}
