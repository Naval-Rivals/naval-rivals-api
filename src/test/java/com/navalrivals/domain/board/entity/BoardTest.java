package com.navalrivals.domain.board.entity;

import com.navalrivals.domain.position.entity.Position;
import com.navalrivals.domain.ship.entity.Ship;
import com.navalrivals.domain.ship.enums.ShipType;
import com.navalrivals.domain.shot.entity.Shot;
import com.navalrivals.domain.user.entity.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BoardTest {

    private Board board;
    private User player;

    @BeforeEach
    void setUp() {
        player = new User();
        player.setId(UUID.randomUUID());
        board = new Board(player);
    }

    // ==================== Helper ====================

    private List<Ship> buildValidFleet() {
        List<Ship> fleet = new ArrayList<>();

        // CARRIER (5) - row 0, cols 0-4
        fleet.add(new Ship(ShipType.CARRIER, List.of(
                new Position(0, 0), new Position(0, 1), new Position(0, 2),
                new Position(0, 3), new Position(0, 4)
        ), false));

        // BATTLESHIP (4) - row 2, cols 0-3
        fleet.add(new Ship(ShipType.BATTLESHIP, List.of(
                new Position(2, 0), new Position(2, 1), new Position(2, 2),
                new Position(2, 3)
        ), false));

        // CRUISER (3) - row 4, cols 0-2
        fleet.add(new Ship(ShipType.CRUISER, List.of(
                new Position(4, 0), new Position(4, 1), new Position(4, 2)
        ), false));

        // SUBMARINE (3) - row 6, cols 0-2
        fleet.add(new Ship(ShipType.SUBMARINE, List.of(
                new Position(6, 0), new Position(6, 1), new Position(6, 2)
        ), false));

        // DESTROYER (2) - row 8, cols 0-1
        fleet.add(new Ship(ShipType.DESTROYER, List.of(
                new Position(8, 0), new Position(8, 1)
        ), false));

        return fleet;
    }

    // ==================== Constructor ====================

    @Test
    @DisplayName("Constructor - deve inicializar board com playerId e valores padrão")
    void constructor_shouldInitializeWithDefaults() {
        assertEquals(player.getId(), board.getPlayerId());
        assertFalse(board.isReady());
        assertTrue(board.getShots().isEmpty());
        assertTrue(board.getShips().isEmpty());
        assertFalse(board.isTorpedoUsed());
        assertFalse(board.isRadarUsed());
        assertFalse(board.isEmpNavalUsed());
        assertEquals(2, board.getShieldCharges());
        assertFalse(board.isShieldActive());
        assertEquals(0, board.getEmpDisabledTurns());
    }

    // ==================== setShip ====================

    @Nested
    @DisplayName("setShip")
    class SetShipTests {

        @Test
        @DisplayName("deve setar navios e marcar ready=true com frota válida")
        void setShip_validFleet_shouldSetShipsAndMarkReady() {
            List<Ship> fleet = buildValidFleet();

            board.setShip(fleet);

            assertTrue(board.isReady());
            assertEquals(5, board.getShips().size());
        }

        @Test
        @DisplayName("deve lançar exceção com frota inválida")
        void setShip_invalidFleet_shouldThrow() {
            List<Ship> invalidFleet = List.of(
                    new Ship(ShipType.DESTROYER, List.of(new Position(0, 0), new Position(0, 1)), false)
            );

            assertThrows(IllegalArgumentException.class, () -> board.setShip(invalidFleet));
            assertFalse(board.isReady());
        }
    }

    // ==================== receiveShot ====================

    @Nested
    @DisplayName("receiveShot")
    class ReceiveShotTests {

        @BeforeEach
        void setUpFleet() {
            board.setShip(buildValidFleet());
        }

        @Test
        @DisplayName("deve retornar hit=true quando posição tem navio")
        void receiveShot_hit_shouldReturnHitTrue() {
            Shot shot = board.receiveShot(new Position(0, 0));

            assertTrue(shot.isHit());
            assertNull(shot.getBlockedBy());
            assertEquals(1, board.getShots().size());
        }

        @Test
        @DisplayName("deve retornar hit=false quando posição não tem navio")
        void receiveShot_miss_shouldReturnHitFalse() {
            Shot shot = board.receiveShot(new Position(9, 9));

            assertFalse(shot.isHit());
            assertNull(shot.getBlockedBy());
            assertEquals(1, board.getShots().size());
        }

        @Test
        @DisplayName("deve marcar navio como sunken quando todas posições acertadas")
        void receiveShot_allPositionsHit_shouldMarkShipSunken() {
            // DESTROYER está em (8,0) e (8,1)
            board.receiveShot(new Position(8, 0));
            board.receiveShot(new Position(8, 1));

            Ship destroyer = board.getShips().stream()
                    .filter(s -> s.getType() == ShipType.DESTROYER)
                    .findFirst().orElseThrow();

            assertTrue(destroyer.isSunken());
        }

        @Test
        @DisplayName("não deve marcar navio como sunken com posições parciais acertadas")
        void receiveShot_partialHit_shouldNotMarkSunken() {
            // DESTROYER está em (8,0) e (8,1) - acertar apenas uma posição
            board.receiveShot(new Position(8, 0));

            Ship destroyer = board.getShips().stream()
                    .filter(s -> s.getType() == ShipType.DESTROYER)
                    .findFirst().orElseThrow();

            assertFalse(destroyer.isSunken());
        }

        @Test
        @DisplayName("deve bloquear tiro quando shield está ativo")
        void receiveShot_shieldActive_shouldBlock() {
            board.activateShield();

            Shot shot = board.receiveShot(new Position(0, 0));

            assertFalse(shot.isHit());
            assertEquals(Shot.BlockedBy.SHIELD, shot.getBlockedBy());
            assertTrue(shot.isBlocked());
            // Tiro bloqueado NÃO é adicionado à lista de shots
            assertEquals(0, board.getShots().size());
            // Shield deve ser desativado após bloquear
            assertFalse(board.isShieldActive());
        }

        @Test
        @DisplayName("shield bloqueia miss também - posição fica livre para novo ataque")
        void receiveShot_shieldActive_missPosition_shouldStillBlock() {
            board.activateShield();

            Shot shot = board.receiveShot(new Position(9, 9));

            assertFalse(shot.isHit());
            assertEquals(Shot.BlockedBy.SHIELD, shot.getBlockedBy());
            assertEquals(0, board.getShots().size());
        }
    }

    // ==================== receiveTorpedo ====================

    @Nested
    @DisplayName("receiveTorpedo")
    class ReceiveTorpedoTests {

        @BeforeEach
        void setUpFleet() {
            board.setShip(buildValidFleet());
        }

        @Test
        @DisplayName("deve afundar navio inteiro quando torpedo acerta")
        void receiveTorpedo_hit_shouldSinkEntireShip() {
            // DESTROYER está em (8,0) e (8,1) - torpedo em qualquer posição afunda tudo
            Shot shot = board.receiveTorpedo(new Position(8, 0));

            assertTrue(shot.isHit());

            Ship destroyer = board.getShips().stream()
                    .filter(s -> s.getType() == ShipType.DESTROYER)
                    .findFirst().orElseThrow();

            assertTrue(destroyer.isSunken());
            // Todas as posições do navio devem ter sido registradas como shots
            assertEquals(2, board.getShots().size());
        }

        @Test
        @DisplayName("deve registrar miss normalmente quando torpedo erra")
        void receiveTorpedo_miss_shouldRegisterNormally() {
            Shot shot = board.receiveTorpedo(new Position(9, 9));

            assertFalse(shot.isHit());
            assertEquals(1, board.getShots().size());
        }

        @Test
        @DisplayName("deve bloquear torpedo quando shield está ativo")
        void receiveTorpedo_shieldActive_shouldBlock() {
            board.activateShield();

            Shot shot = board.receiveTorpedo(new Position(0, 0));

            assertFalse(shot.isHit());
            assertEquals(Shot.BlockedBy.SHIELD, shot.getBlockedBy());
            assertTrue(shot.isBlocked());
            assertEquals(0, board.getShots().size());
            assertFalse(board.isShieldActive());
        }

        @Test
        @DisplayName("torpedo hit deve registrar shots para todas posições do navio")
        void receiveTorpedo_hit_shouldRegisterAllShipPositionsAsShots() {
            // CARRIER está em (0,0) a (0,4) - 5 posições
            Shot shot = board.receiveTorpedo(new Position(0, 2));

            assertTrue(shot.isHit());

            Ship carrier = board.getShips().stream()
                    .filter(s -> s.getType() == ShipType.CARRIER)
                    .findFirst().orElseThrow();

            assertTrue(carrier.isSunken());
            assertEquals(5, board.getShots().size());
        }
    }

    // ==================== allShipsSunk ====================

    @Nested
    @DisplayName("allShipsSunk")
    class AllShipsSunkTests {

        @Test
        @DisplayName("deve retornar false quando nem todos navios estão afundados")
        void allShipsSunk_notAllSunk_shouldReturnFalse() {
            board.setShip(buildValidFleet());

            // Afundar apenas o DESTROYER
            board.receiveTorpedo(new Position(8, 0));

            assertFalse(board.allShipsSunk());
        }

        @Test
        @DisplayName("deve retornar true quando todos navios estão afundados")
        void allShipsSunk_allSunk_shouldReturnTrue() {
            board.setShip(buildValidFleet());

            // Afundar todos os navios com torpedos
            board.receiveTorpedo(new Position(0, 0)); // CARRIER
            board.receiveTorpedo(new Position(2, 0)); // BATTLESHIP
            board.receiveTorpedo(new Position(4, 0)); // CRUISER
            board.receiveTorpedo(new Position(6, 0)); // SUBMARINE
            board.receiveTorpedo(new Position(8, 0)); // DESTROYER

            assertTrue(board.allShipsSunk());
        }
    }

    // ==================== executeRadar ====================

    @Nested
    @DisplayName("executeRadar")
    class ExecuteRadarTests {

        @BeforeEach
        void setUpFleet() {
            board.setShip(buildValidFleet());
        }

        @Test
        @DisplayName("deve revelar posições com navio em bloco 3x3")
        void executeRadar_shouldRevealShipPositions() {
            // Centro em (0,1) - bloco 3x3 cobre row -1 a 1, col 0 a 2
            // CARRIER está em (0,0), (0,1), (0,2), (0,3), (0,4)
            // Posições dentro do bloco: (0,0), (0,1), (0,2)
            List<Position> revealed = board.executeRadar(new Position(0, 1));

            assertEquals(3, revealed.size());
            assertTrue(revealed.stream().anyMatch(p -> p.getRow() == 0 && p.getCol() == 0));
            assertTrue(revealed.stream().anyMatch(p -> p.getRow() == 0 && p.getCol() == 1));
            assertTrue(revealed.stream().anyMatch(p -> p.getRow() == 0 && p.getCol() == 2));
        }

        @Test
        @DisplayName("deve retornar lista vazia quando não há navios no bloco 3x3")
        void executeRadar_noShipsInArea_shouldReturnEmpty() {
            // Centro em (9,9) - nenhum navio nessa área
            List<Position> revealed = board.executeRadar(new Position(9, 9));

            assertTrue(revealed.isEmpty());
        }

        @Test
        @DisplayName("deve respeitar limites do tabuleiro (10x10)")
        void executeRadar_atCorner_shouldRespectBounds() {
            // Centro em (0,0) - bloco cobre row -1 a 1, col -1 a 1
            // Posições válidas: (0,0), (0,1), (1,0), (1,1)
            // CARRIER está em (0,0), (0,1) - duas posições reveladas
            List<Position> revealed = board.executeRadar(new Position(0, 0));

            assertEquals(2, revealed.size());
            assertTrue(revealed.stream().anyMatch(p -> p.getRow() == 0 && p.getCol() == 0));
            assertTrue(revealed.stream().anyMatch(p -> p.getRow() == 0 && p.getCol() == 1));
        }

        @Test
        @DisplayName("não deve revelar navios já afundados")
        void executeRadar_sunkenShip_shouldNotReveal() {
            // Afundar DESTROYER em (8,0) e (8,1)
            board.receiveTorpedo(new Position(8, 0));

            // Radar centrado em (8,0) - DESTROYER já está sunken
            List<Position> revealed = board.executeRadar(new Position(8, 0));

            // Nenhuma posição do DESTROYER deve ser revelada
            assertFalse(revealed.stream().anyMatch(p -> p.getRow() == 8 && p.getCol() == 0));
            assertFalse(revealed.stream().anyMatch(p -> p.getRow() == 8 && p.getCol() == 1));
        }
    }

    // ==================== activateShield ====================

    @Nested
    @DisplayName("activateShield")
    class ActivateShieldTests {

        @Test
        @DisplayName("deve decrementar shieldCharges e ativar shieldActive")
        void activateShield_shouldDecrementAndActivate() {
            assertEquals(2, board.getShieldCharges());
            assertFalse(board.isShieldActive());

            board.activateShield();

            assertEquals(1, board.getShieldCharges());
            assertTrue(board.isShieldActive());
        }

        @Test
        @DisplayName("segunda ativação deve decrementar novamente")
        void activateShield_secondActivation_shouldDecrementAgain() {
            board.activateShield();
            // Desativa shield simulando receber um tiro
            board.setShip(buildValidFleet());
            board.receiveShot(new Position(9, 9)); // consome o shield

            board.activateShield();

            assertEquals(0, board.getShieldCharges());
        }
    }

    // ==================== applyEmp ====================

    @Nested
    @DisplayName("applyEmp")
    class ApplyEmpTests {

        @Test
        @DisplayName("deve setar empDisabledTurns")
        void applyEmp_shouldSetEmpDisabledTurns() {
            board.applyEmp(3);

            assertEquals(3, board.getEmpDisabledTurns());
            assertTrue(board.isEmpDisabled());
        }

        @Test
        @DisplayName("applyEmp com 0 não deve desabilitar")
        void applyEmp_zero_shouldNotDisable() {
            board.applyEmp(0);

            assertEquals(0, board.getEmpDisabledTurns());
            assertFalse(board.isEmpDisabled());
        }
    }

    // ==================== decrementEmpDisabledTurns ====================

    @Nested
    @DisplayName("decrementEmpDisabledTurns")
    class DecrementEmpTests {

        @Test
        @DisplayName("deve decrementar empDisabledTurns")
        void decrementEmpDisabledTurns_shouldDecrement() {
            board.applyEmp(3);

            board.decrementEmpDisabledTurns();

            assertEquals(2, board.getEmpDisabledTurns());
        }

        @Test
        @DisplayName("não deve ir abaixo de 0")
        void decrementEmpDisabledTurns_atZero_shouldNotGoBelowZero() {
            assertEquals(0, board.getEmpDisabledTurns());

            board.decrementEmpDisabledTurns();

            assertEquals(0, board.getEmpDisabledTurns());
        }

        @Test
        @DisplayName("deve chegar a 0 e parar")
        void decrementEmpDisabledTurns_multipleDecrements_shouldStopAtZero() {
            board.applyEmp(2);

            board.decrementEmpDisabledTurns();
            board.decrementEmpDisabledTurns();
            board.decrementEmpDisabledTurns(); // já em 0

            assertEquals(0, board.getEmpDisabledTurns());
            assertFalse(board.isEmpDisabled());
        }
    }

    // ==================== Torpedo availability ====================

    @Nested
    @DisplayName("isTorpedoAvailable / markTorpedoUsed")
    class TorpedoAvailabilityTests {

        @Test
        @DisplayName("torpedo deve estar disponível inicialmente")
        void isTorpedoAvailable_initially_shouldBeTrue() {
            assertTrue(board.isTorpedoAvailable());
        }

        @Test
        @DisplayName("markTorpedoUsed deve tornar torpedo indisponível")
        void markTorpedoUsed_shouldMakeUnavailable() {
            board.markTorpedoUsed();

            assertFalse(board.isTorpedoAvailable());
        }
    }

    // ==================== Radar availability ====================

    @Nested
    @DisplayName("isRadarAvailable / markRadarUsed")
    class RadarAvailabilityTests {

        @Test
        @DisplayName("radar deve estar disponível inicialmente")
        void isRadarAvailable_initially_shouldBeTrue() {
            assertTrue(board.isRadarAvailable());
        }

        @Test
        @DisplayName("markRadarUsed deve tornar radar indisponível")
        void markRadarUsed_shouldMakeUnavailable() {
            board.markRadarUsed();

            assertFalse(board.isRadarAvailable());
        }
    }

    // ==================== EMP Naval availability ====================

    @Nested
    @DisplayName("isEmpNavalAvailable / markEmpNavalUsed")
    class EmpNavalAvailabilityTests {

        @Test
        @DisplayName("EMP naval deve estar disponível inicialmente")
        void isEmpNavalAvailable_initially_shouldBeTrue() {
            assertTrue(board.isEmpNavalAvailable());
        }

        @Test
        @DisplayName("markEmpNavalUsed deve tornar EMP indisponível")
        void markEmpNavalUsed_shouldMakeUnavailable() {
            board.markEmpNavalUsed();

            assertFalse(board.isEmpNavalAvailable());
        }
    }

    // ==================== Shield availability ====================

    @Nested
    @DisplayName("isShieldAvailable")
    class ShieldAvailabilityTests {

        @Test
        @DisplayName("shield deve estar disponível inicialmente (2 cargas)")
        void isShieldAvailable_initially_shouldBeTrue() {
            assertTrue(board.isShieldAvailable());
            assertEquals(2, board.getShieldCharges());
        }

        @Test
        @DisplayName("shield deve ficar indisponível quando cargas acabam")
        void isShieldAvailable_noCharges_shouldBeFalse() {
            board.activateShield();
            board.activateShield();

            assertFalse(board.isShieldAvailable());
            assertEquals(0, board.getShieldCharges());
        }

        @Test
        @DisplayName("shield deve continuar disponível com 1 carga restante")
        void isShieldAvailable_oneCharge_shouldBeTrue() {
            board.activateShield();

            assertTrue(board.isShieldAvailable());
            assertEquals(1, board.getShieldCharges());
        }
    }
}
