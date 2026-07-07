package com.navalrivals.domain.room.dto;

import com.navalrivals.domain.game.enums.GameMode;
import com.navalrivals.domain.room.entity.Room;
import com.navalrivals.domain.room.enums.RoomStatus;

import java.time.Instant;
import java.util.UUID;

public record RoomResponse(
        UUID id,
        String code,
        RoomStatus status,
        GameMode gameMode,
        PlayerInfo host,
        PlayerInfo opponent,
        UUID gameId,
        Instant createdAt
) {
    public RoomResponse(Room room) {
        this(
                room.getId(),
                room.getCode(),
                room.getStatus(),
                room.getGameMode(),
                new PlayerInfo(room.getHost().getId(), room.getHost().getNickname()),
                room.getOpponent() != null
                        ? new PlayerInfo(room.getOpponent().getId(), room.getOpponent().getNickname())
                        : null,
                room.getGameId(),
                room.getCreatedAt()
        );
    }

    public record PlayerInfo(UUID id, String nickname) {}
}
