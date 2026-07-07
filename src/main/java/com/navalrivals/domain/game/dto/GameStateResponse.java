package com.navalrivals.domain.game.dto;

import com.navalrivals.domain.game.enums.GameMode;
import com.navalrivals.domain.game.enums.GameStatus;
import com.navalrivals.domain.position.entity.Position;
import com.navalrivals.domain.ship.entity.Ship;
import com.navalrivals.domain.ship.enums.ShipType;
import com.navalrivals.domain.shot.entity.Shot;

import java.util.List;
import java.util.UUID;

public record GameStateResponse(
        UUID gameId,
        GameStatus status,
        GameMode gameMode,
        UUID currentTurn,
        UUID myPlayerId,
        List<ShipInfo> myShips,
        List<ShotInfo> myShotsReceived,
        List<ShotInfo> myShotsMade,
        boolean torpedoAvailable,
        AbilitiesInfo abilities
) {
    public record ShipInfo(ShipType type, List<Position> positions, boolean sunk) {
        public ShipInfo(Ship ship) {
            this(ship.getType(), ship.getPositions(), ship.isSunken());
        }
    }

    public record ShotInfo(Position position, boolean hit) {
        public ShotInfo(Shot shot) {
            this(shot.getPosition(), shot.isHit());
        }
    }

    /**
     * Informações de habilidades disponíveis para o modo tático.
     * Null se o modo for CLASSIC.
     */
    public record AbilitiesInfo(
            boolean radarAvailable,
            int shieldCharges,
            boolean shieldActive,
            boolean empNavalAvailable,
            int empDisabledTurns
    ) {}
}
