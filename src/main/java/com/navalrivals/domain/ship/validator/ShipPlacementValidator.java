package com.navalrivals.domain.ship.validator;

import com.navalrivals.domain.position.entity.Position;
import com.navalrivals.domain.ship.entity.Ship;
import com.navalrivals.domain.ship.enums.ShipType;

import java.util.*;

public class ShipPlacementValidator {

    private static final int GRID_SIZE = 10;

    // Expected fleet: 1 CARRIER, 1 BATTLESHIP, 1 CRUISER, 1 SUBMARINE, 1 DESTROYER
    private static final Map<ShipType, Integer> EXPECTED_FLEET = Map.of(
            ShipType.CARRIER, 1,
            ShipType.BATTLESHIP, 1,
            ShipType.CRUISER, 1,
            ShipType.SUBMARINE, 1,
            ShipType.DESTROYER, 1
    );

    public static void validate(List<Ship> ships) {
        validateFleetComposition(ships);
        validateShipSizes(ships);
        validatePositionsInBounds(ships);
        validateShipsAreLinear(ships);
        validateNoOverlap(ships);
    }

    private static void validateFleetComposition(List<Ship> ships) {
        if (ships == null || ships.size() != 5) {
            throw new IllegalArgumentException("Deve posicionar exatamente 5 navios");
        }
        Map<ShipType, Integer> counts = new HashMap<>();
        for (Ship ship : ships) {
            counts.merge(ship.getType(), 1, Integer::sum);
        }
        if (!counts.equals(EXPECTED_FLEET)) {
            throw new IllegalArgumentException("Composição de frota inválida");
        }
    }

    private static void validateShipSizes(List<Ship> ships) {
        for (Ship ship : ships) {
            if (ship.getPositions() == null || ship.getPositions().size() != ship.getType().getSize()) {
                throw new IllegalArgumentException(
                        "Navio " + ship.getType() + " deve ter " + ship.getType().getSize() + " posições");
            }
        }
    }

    private static void validatePositionsInBounds(List<Ship> ships) {
        for (Ship ship : ships) {
            for (Position pos : ship.getPositions()) {
                if (pos.getRow() < 0 || pos.getRow() >= GRID_SIZE
                        || pos.getCol() < 0 || pos.getCol() >= GRID_SIZE) {
                    throw new IllegalArgumentException(
                            "Posição fora do tabuleiro: (" + pos.getRow() + "," + pos.getCol() + ")");
                }
            }
        }
    }

    private static void validateShipsAreLinear(List<Ship> ships) {
        for (Ship ship : ships) {
            List<Position> positions = ship.getPositions();
            if (positions.size() == 1) continue;

            boolean sameRow = positions.stream().allMatch(p -> p.getRow() == positions.get(0).getRow());
            boolean sameCol = positions.stream().allMatch(p -> p.getCol() == positions.get(0).getCol());

            if (!sameRow && !sameCol) {
                throw new IllegalArgumentException(
                        "Navio " + ship.getType() + " deve estar em linha reta (horizontal ou vertical)");
            }

            // Check contiguous
            if (sameRow) {
                List<Integer> cols = positions.stream().map(Position::getCol).sorted().toList();
                for (int i = 1; i < cols.size(); i++) {
                    if (cols.get(i) - cols.get(i - 1) != 1) {
                        throw new IllegalArgumentException(
                                "Navio " + ship.getType() + " deve ter posições contíguas");
                    }
                }
            } else {
                List<Integer> rows = positions.stream().map(Position::getRow).sorted().toList();
                for (int i = 1; i < rows.size(); i++) {
                    if (rows.get(i) - rows.get(i - 1) != 1) {
                        throw new IllegalArgumentException(
                                "Navio " + ship.getType() + " deve ter posições contíguas");
                    }
                }
            }
        }
    }

    private static void validateNoOverlap(List<Ship> ships) {
        Set<String> occupiedCells = new HashSet<>();
        for (Ship ship : ships) {
            for (Position pos : ship.getPositions()) {
                String key = pos.getRow() + "," + pos.getCol();
                if (!occupiedCells.add(key)) {
                    throw new IllegalArgumentException(
                            "Posição (" + pos.getRow() + "," + pos.getCol() + ") está sobreposta");
                }
            }
        }
    }
}
