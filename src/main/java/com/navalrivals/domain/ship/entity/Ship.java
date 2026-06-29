package com.navalrivals.domain.ship.entity;

import com.navalrivals.domain.position.entity.Position;
import com.navalrivals.domain.ship.enums.ShipType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Ship {

    private ShipType type;
    private List<Position> positions;
    private boolean sunken;
}
