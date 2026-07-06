package com.navalrivals.domain.board.entity;

import com.navalrivals.domain.position.entity.Position;
import com.navalrivals.domain.ship.entity.Ship;
import com.navalrivals.domain.ship.validator.ShipPlacementValidator;
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
    private boolean torpedoUsed;

    public Board(User player){
        this.playerId = player.getId();
        this.shots = new ArrayList<>();
        this.ships = new ArrayList<>();
        this.ready = false;
        this.torpedoUsed = false;
    }

    public boolean isTorpedoAvailable() {
        return !torpedoUsed;
    }

    public void markTorpedoUsed() {
        this.torpedoUsed = true;
    }

    public void setShip(List<Ship> ships){
        ShipPlacementValidator.validate(ships);
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

    /**
     * Recebe um torpedo na posição indicada.
     * Se acertar um navio, afunda o navio inteiro instantaneamente
     * (registra shots em todas as posições do navio que ainda não foram atingidas).
     * Se errar, comporta-se como um tiro normal (registra miss).
     */
    public Shot receiveTorpedo(Position position) {
        Ship targetShip = ships.stream()
                .filter(ship -> !ship.isSunken())
                .filter(ship -> ship.getPositions().stream()
                        .anyMatch(pos -> pos.getRow() == position.getRow() && pos.getCol() == position.getCol()))
                .findFirst()
                .orElse(null);

        if (targetShip == null) {
            // Miss — comportamento idêntico ao tiro normal
            Shot shot = new Shot(position, false);
            shots.add(shot);
            return shot;
        }

        // Hit — afunda o navio inteiro instantaneamente
        // Registra shots para todas as posições do navio que ainda não foram atingidas
        for (Position shipPos : targetShip.getPositions()) {
            boolean alreadyShot = shots.stream()
                    .anyMatch(s -> s.getPosition().getRow() == shipPos.getRow()
                            && s.getPosition().getCol() == shipPos.getCol());
            if (!alreadyShot) {
                shots.add(new Shot(shipPos, true));
            }
        }

        targetShip.setSunken(true);
        return new Shot(position, true);
    }

    public boolean allShipsSunk() {
        return ships.stream().allMatch(Ship::isSunken);
    }
}
