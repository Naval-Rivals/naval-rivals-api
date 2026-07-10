package com.navalrivals.domain.ship.validator;

import com.navalrivals.domain.ship.entity.Ship;
import com.navalrivals.domain.ship.enums.ShipType;
import com.navalrivals.domain.position.entity.Position;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShipPlacementValidatorTest {

    @Test
    @DisplayName("validate - frota válida completa não deve lançar exceção")
    void validate_validFleet_shouldNotThrow() {
        List<Ship> fleet = buildValidFleet();

        assertDoesNotThrow(() -> ShipPlacementValidator.validate(fleet));
    }

    @Test
    @DisplayName("validate - lista null deve lançar exceção")
    void validate_nullShips_shouldThrow() {
        assertThrows(Exception.class, () -> ShipPlacementValidator.validate(null));
    }

    @Test
    @DisplayName("validate - número errado de navios deve lançar exceção")
    void validate_wrongNumberOfShips_shouldThrow() {
        List<Ship> fleet = buildValidFleet();

        // 4 navios - removendo o último
        List<Ship> tooFew = new ArrayList<>(fleet.subList(0, 4));
        assertThrows(Exception.class, () -> ShipPlacementValidator.validate(tooFew));

        // 6 navios - duplicando um
        List<Ship> tooMany = new ArrayList<>(fleet);
        tooMany.add(new Ship(ShipType.DESTROYER, List.of(
                new Position(8, 0),
                new Position(8, 1)
        ), false));
        assertThrows(Exception.class, () -> ShipPlacementValidator.validate(tooMany));
    }

    @Test
    @DisplayName("validate - tipo de navio duplicado deve lançar exceção")
    void validate_duplicateShipType_shouldThrow() {
        List<Ship> fleet = new ArrayList<>();
        // 2 CARRIERS ao invés de composição correta
        fleet.add(new Ship(ShipType.CARRIER, List.of(
                new Position(0, 0), new Position(0, 1), new Position(0, 2),
                new Position(0, 3), new Position(0, 4)
        ), false));
        fleet.add(new Ship(ShipType.CARRIER, List.of(
                new Position(1, 0), new Position(1, 1), new Position(1, 2),
                new Position(1, 3), new Position(1, 4)
        ), false));
        fleet.add(new Ship(ShipType.CRUISER, List.of(
                new Position(2, 0), new Position(2, 1), new Position(2, 2)
        ), false));
        fleet.add(new Ship(ShipType.SUBMARINE, List.of(
                new Position(3, 0), new Position(3, 1), new Position(3, 2)
        ), false));
        fleet.add(new Ship(ShipType.DESTROYER, List.of(
                new Position(4, 0), new Position(4, 1)
        ), false));

        assertThrows(Exception.class, () -> ShipPlacementValidator.validate(fleet));
    }

    @Test
    @DisplayName("validate - tamanho do navio incorreto deve lançar exceção")
    void validate_wrongShipSize_shouldThrow() {
        List<Ship> fleet = buildValidFleet();

        // Substituir CARRIER (size 5) por um com apenas 3 posições
        fleet.set(0, new Ship(ShipType.CARRIER, List.of(
                new Position(0, 0), new Position(0, 1), new Position(0, 2)
        ), false));

        assertThrows(Exception.class, () -> ShipPlacementValidator.validate(fleet));
    }

    @Test
    @DisplayName("validate - posição fora dos limites deve lançar exceção")
    void validate_positionOutOfBounds_shouldThrow() {
        // row negativo
        List<Ship> fleetNegativeRow = buildValidFleet();
        fleetNegativeRow.set(4, new Ship(ShipType.DESTROYER, List.of(
                new Position(-1, 0), new Position(0, 0)
        ), false));
        assertThrows(Exception.class, () -> ShipPlacementValidator.validate(fleetNegativeRow));

        // col >= 10
        List<Ship> fleetColOutOfBounds = buildValidFleet();
        fleetColOutOfBounds.set(4, new Ship(ShipType.DESTROYER, List.of(
                new Position(9, 9), new Position(9, 10)
        ), false));
        assertThrows(Exception.class, () -> ShipPlacementValidator.validate(fleetColOutOfBounds));

        // row >= 10
        List<Ship> fleetRowOutOfBounds = buildValidFleet();
        fleetRowOutOfBounds.set(4, new Ship(ShipType.DESTROYER, List.of(
                new Position(10, 0), new Position(10, 1)
        ), false));
        assertThrows(Exception.class, () -> ShipPlacementValidator.validate(fleetRowOutOfBounds));

        // col negativo
        List<Ship> fleetNegativeCol = buildValidFleet();
        fleetNegativeCol.set(4, new Ship(ShipType.DESTROYER, List.of(
                new Position(5, -1), new Position(5, 0)
        ), false));
        assertThrows(Exception.class, () -> ShipPlacementValidator.validate(fleetNegativeCol));
    }

    @Test
    @DisplayName("validate - navio não linear (em L) deve lançar exceção")
    void validate_shipNotLinear_shouldThrow() {
        List<Ship> fleet = buildValidFleet();

        // CRUISER em formato L: (2,0), (2,1), (3,1)
        fleet.set(2, new Ship(ShipType.CRUISER, List.of(
                new Position(2, 0), new Position(2, 1), new Position(3, 1)
        ), false));

        assertThrows(Exception.class, () -> ShipPlacementValidator.validate(fleet));
    }

    @Test
    @DisplayName("validate - navio com gap nas posições deve lançar exceção")
    void validate_shipNotContiguous_shouldThrow() {
        List<Ship> fleet = buildValidFleet();

        // CRUISER com gap: (2,0), (2,1), (2,3) — falta (2,2)
        fleet.set(2, new Ship(ShipType.CRUISER, List.of(
                new Position(2, 0), new Position(2, 1), new Position(2, 3)
        ), false));

        assertThrows(Exception.class, () -> ShipPlacementValidator.validate(fleet));
    }

    @Test
    @DisplayName("validate - navios sobrepostos devem lançar exceção")
    void validate_overlappingShips_shouldThrow() {
        List<Ship> fleet = buildValidFleet();

        // Colocar DESTROYER na mesma posição que o CARRIER (0,0)-(0,1)
        fleet.set(4, new Ship(ShipType.DESTROYER, List.of(
                new Position(0, 0), new Position(0, 1)
        ), false));

        assertThrows(Exception.class, () -> ShipPlacementValidator.validate(fleet));
    }

    @Test
    @DisplayName("validate - navio horizontal válido não deve lançar exceção")
    void validate_horizontalShip_shouldNotThrow() {
        List<Ship> fleet = buildValidFleet();

        // Garantir que BATTLESHIP está horizontal
        fleet.set(1, new Ship(ShipType.BATTLESHIP, List.of(
                new Position(1, 0), new Position(1, 1),
                new Position(1, 2), new Position(1, 3)
        ), false));

        assertDoesNotThrow(() -> ShipPlacementValidator.validate(fleet));
    }

    @Test
    @DisplayName("validate - navio vertical válido não deve lançar exceção")
    void validate_verticalShip_shouldNotThrow() {
        List<Ship> fleet = buildValidFleet();

        // Colocar BATTLESHIP vertical
        fleet.set(1, new Ship(ShipType.BATTLESHIP, List.of(
                new Position(5, 5), new Position(6, 5),
                new Position(7, 5), new Position(8, 5)
        ), false));

        assertDoesNotThrow(() -> ShipPlacementValidator.validate(fleet));
    }

    // ========================
    // Helper Methods
    // ========================

    private List<Ship> buildValidFleet() {
        List<Ship> fleet = new ArrayList<>();

        // CARRIER (size 5) - horizontal na linha 0
        fleet.add(new Ship(ShipType.CARRIER, List.of(
                new Position(0, 0), new Position(0, 1), new Position(0, 2),
                new Position(0, 3), new Position(0, 4)
        ), false));

        // BATTLESHIP (size 4) - horizontal na linha 1
        fleet.add(new Ship(ShipType.BATTLESHIP, List.of(
                new Position(1, 0), new Position(1, 1),
                new Position(1, 2), new Position(1, 3)
        ), false));

        // CRUISER (size 3) - horizontal na linha 2
        fleet.add(new Ship(ShipType.CRUISER, List.of(
                new Position(2, 0), new Position(2, 1), new Position(2, 2)
        ), false));

        // SUBMARINE (size 3) - horizontal na linha 3
        fleet.add(new Ship(ShipType.SUBMARINE, List.of(
                new Position(3, 0), new Position(3, 1), new Position(3, 2)
        ), false));

        // DESTROYER (size 2) - horizontal na linha 4
        fleet.add(new Ship(ShipType.DESTROYER, List.of(
                new Position(4, 0), new Position(4, 1)
        ), false));

        return fleet;
    }
}
