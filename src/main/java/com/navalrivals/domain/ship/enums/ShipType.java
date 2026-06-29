package com.navalrivals.domain.ship.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ShipType {
    CARRIER(5),
    BATTLESHIP(4),
    DESTROYER(3),
    PATROL_BOAT(2);

    private final int size;


}
