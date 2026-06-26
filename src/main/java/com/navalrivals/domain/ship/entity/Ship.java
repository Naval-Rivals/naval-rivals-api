package com.navalrivals.domain.ship.entity;

import com.navalrivals.domain.position.entity.Position;
import com.navalrivals.domain.ship.enums.ShipSize;

import java.util.List;
import java.util.UUID;

public class Ship {

    private UUID id;
    private List<Position> positions;
    private boolean sunken;
    private ShipSize size;
}
