package com.navalrivals.domain.room.dto;

import com.navalrivals.domain.game.enums.GameMode;

public record CreateRoomRequest(
        GameMode gameMode
) {
}
