package com.navalrivals.domain.board.entity;

import com.navalrivals.domain.position.entity.Position;
import com.navalrivals.domain.ship.entity.Ship;
import com.navalrivals.domain.shot.entity.Shot;
import com.navalrivals.domain.user.entity.User;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public class Board {

    private final UUID playerId;
    private final List<Shot> shots;
    private List<Ship> ships;
    private boolean ready;

    public Board(User player){
        this.playerId = player.getId();
        this.shots = new ArrayList<>();
        this.ships = new ArrayList<>();
        this.ready = false;
    }

    public void setShip(List<Ship> ships){
        this.ships = ships;
        this.ready = true;
    }

    public Shot receiveShot(Position position) {
        boolean hit = ships.stream()
                .flatMap(ship -> ship.getPositions().stream())
                .anyMatch(pos -> pos.getRow() == position.getRow() && pos.getCol() == position.getCol());

        if (hit) {
            ships.stream()
                    .filter(ship -> ship.getPositions().stream()
                            .anyMatch(pos -> pos.getRow() == position.getRow() && pos.getCol() == position.getCol()))
                    .findFirst()
                    .ifPresent(ship -> {
                        boolean allHit = ship.getPositions().stream()
                                .allMatch(pos -> shots.stream().anyMatch(s -> s.getPosition().getRow() == pos.getRow() && s.getPosition().getCol() == pos.getCol())
                                        || (pos.getRow() == position.getRow() && pos.getCol() == position.getCol()));
                        if (allHit) ship.setSunken(true);
                    });
        }

        Shot shot = new Shot(position, hit);
        shots.add(shot);
        return shot;
    }

    public boolean allShipsSunk() {
        return ships.stream().allMatch(Ship::isSunken);
    }
}
