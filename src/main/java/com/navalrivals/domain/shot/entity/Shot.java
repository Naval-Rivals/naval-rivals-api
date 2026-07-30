package com.navalrivals.domain.shot.entity;

import com.navalrivals.domain.position.entity.Position;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class Shot {
    private Position position;
    private boolean hit;
    private BlockedBy blockedBy;

    public Shot(Position position, boolean hit) {
        this.position = position;
        this.hit = hit;
        this.blockedBy = null;
    }

    public Shot(Position position, boolean hit, BlockedBy blockedBy) {
        this.position = position;
        this.hit = hit;
        this.blockedBy = blockedBy;
    }

    public boolean isBlocked() {
        return blockedBy != null;
    }

    public enum BlockedBy {
        SHIELD
    }
}
