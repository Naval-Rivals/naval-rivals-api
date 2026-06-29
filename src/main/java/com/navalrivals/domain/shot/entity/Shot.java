package com.navalrivals.domain.shot.entity;

import com.navalrivals.domain.position.entity.Position;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Shot {
    private Position position;
    private boolean hit;
}
