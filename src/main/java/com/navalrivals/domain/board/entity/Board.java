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

    // --- Habilidades ---
    private boolean torpedoUsed;
    private boolean radarUsed;
    private boolean empNavalUsed;

    // Escudo: 2 cargas, pode ativar 1 por vez
    private int shieldCharges;
    private boolean shieldActive;

    // EMP: turnos restantes com habilidades desativadas (aplicado pelo oponente)
    private int empDisabledTurns;

    public Board(User player) {
        this.playerId = player.getId();
        this.shots = new ArrayList<>();
        this.ships = new ArrayList<>();
        this.ready = false;
        this.torpedoUsed = false;
        this.radarUsed = false;
        this.empNavalUsed = false;
        this.shieldCharges = 2;
        this.shieldActive = false;
        this.empDisabledTurns = 0;
    }

    // --- Torpedo ---

    public boolean isTorpedoAvailable() {
        return !torpedoUsed;
    }

    public void markTorpedoUsed() {
        this.torpedoUsed = true;
    }

    // --- Radar ---

    public boolean isRadarAvailable() {
        return !radarUsed;
    }

    public void markRadarUsed() {
        this.radarUsed = true;
    }

    /**
     * Verifica bloco 3x3 centrado na posição informada.
     * Retorna lista de posições que contêm navio (sem revelar tipo).
     */
    public List<Position> executeRadar(Position center) {
        List<Position> revealed = new ArrayList<>();
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int row = center.getRow() + dr;
                int col = center.getCol() + dc;
                if (row < 0 || row >= 10 || col < 0 || col >= 10) continue;

                boolean hasShip = ships.stream()
                        .filter(ship -> !ship.isSunken())
                        .flatMap(ship -> ship.getPositions().stream())
                        .anyMatch(pos -> pos.getRow() == row && pos.getCol() == col);

                if (hasShip) {
                    revealed.add(new Position(row, col));
                }
            }
        }
        return revealed;
    }

    // --- Escudo ---

    public boolean isShieldAvailable() {
        return shieldCharges > 0;
    }

    public void activateShield() {
        this.shieldCharges--;
        this.shieldActive = true;
    }

    // --- EMP Naval ---

    public boolean isEmpNavalAvailable() {
        return !empNavalUsed;
    }

    public void markEmpNavalUsed() {
        this.empNavalUsed = true;
    }

    /**
     * Aplica EMP neste board (desativa habilidades por N turnos).
     */
    public void applyEmp(int turns) {
        this.empDisabledTurns = turns;
    }

    /**
     * Decrementa contador de EMP ao início do turno deste jogador.
     */
    public void decrementEmpDisabledTurns() {
        if (empDisabledTurns > 0) {
            empDisabledTurns--;
        }
    }

    /**
     * Verifica se habilidades estão desativadas por EMP.
     */
    public boolean isEmpDisabled() {
        return empDisabledTurns > 0;
    }

    // --- Navios e tiros ---

    public void setShip(List<Ship> ships) {
        ShipPlacementValidator.validate(ships);
        this.ships = ships;
        this.ready = true;
    }

    public Shot receiveShot(Position position) {
        // Verifica se escudo bloqueia
        if (shieldActive) {
            this.shieldActive = false;
            // Tiro bloqueado: NÃO registra na lista de shots (posição fica livre para novo ataque)
            return new Shot(position, false, Shot.BlockedBy.SHIELD);
        }

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
     * Se escudo estiver ativo, bloqueia.
     * Se acertar um navio, afunda o navio inteiro instantaneamente.
     * Se errar, comporta-se como um tiro normal.
     */
    public Shot receiveTorpedo(Position position) {
        // Verifica escudo
        if (shieldActive) {
            this.shieldActive = false;
            // Torpedo bloqueado por escudo: NÃO registra na lista de shots
            return new Shot(position, false, Shot.BlockedBy.SHIELD);
        }

        Ship targetShip = ships.stream()
                .filter(ship -> !ship.isSunken())
                .filter(ship -> ship.getPositions().stream()
                        .anyMatch(pos -> pos.getRow() == position.getRow() && pos.getCol() == position.getCol()))
                .findFirst()
                .orElse(null);

        if (targetShip == null) {
            Shot shot = new Shot(position, false);
            shots.add(shot);
            return shot;
        }

        // Hit — afunda o navio inteiro instantaneamente
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
