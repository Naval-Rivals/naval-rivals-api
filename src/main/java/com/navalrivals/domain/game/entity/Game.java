package com.navalrivals.domain.game.entity;

import com.navalrivals.domain.board.entity.Board;
import com.navalrivals.domain.game.enums.AbilityType;
import com.navalrivals.domain.game.enums.GameMode;
import com.navalrivals.domain.game.enums.GameStatus;
import com.navalrivals.domain.position.entity.Position;
import com.navalrivals.domain.ship.entity.Ship;
import com.navalrivals.domain.shot.entity.Shot;
import com.navalrivals.domain.user.entity.User;
import com.navalrivals.infra.exception.exceptions.MatchStatusException;
import com.navalrivals.infra.exception.exceptions.PlayerWithoutPermissionException;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Getter
public class Game {

    private final UUID id;
    private final Board player1;
    private Board player2;
    private UUID currentTurn;
    private GameStatus status;
    private UUID winnerId;
    private final Instant createdAt;
    private final GameMode gameMode;
    private Instant lastActivityAt;

    public Game(User player1, GameMode gameMode) {
        this.id = UUID.randomUUID();
        this.player1 = new Board(player1);
        this.status = GameStatus.WAITING_OPPONENT;
        this.createdAt = Instant.now();
        this.lastActivityAt = Instant.now();
        this.gameMode = gameMode;
    }

    public Board getBoardOf(UUID playerId) {
        if (player1.getPlayerId().equals(playerId)) return player1;
        if (player2 != null && player2.getPlayerId().equals(playerId)) return player2;
        throw new PlayerWithoutPermissionException("Jogador não está nessa partida");
    }

    public Board getOpponentBoardOf(UUID playerId) {
        if (player1.getPlayerId().equals(playerId)) return player2;
        if (player2 != null && player2.getPlayerId().equals(playerId)) return player1;
        throw new PlayerWithoutPermissionException("Jogador não está nessa partida");
    }

    public boolean hasPlayer(UUID playerId) {
        if (player1.getPlayerId().equals(playerId)) return true;
        return player2 != null && player2.getPlayerId().equals(playerId);
    }

    public boolean areBothPlayersReady() {
        return player1.isReady() && player2 != null && player2.isReady();
    }

    public boolean isPlayerTurn(UUID playerId) {
        return currentTurn != null && currentTurn.equals(playerId);
    }

    public void join(User player2) {
        if (this.status != GameStatus.WAITING_OPPONENT) {
            throw new MatchStatusException("Partida não está aguardando oponente");
        }
        if (this.player1.getPlayerId().equals(player2.getId())) {
            throw new PlayerWithoutPermissionException("Jogador não pode entrar na própria sala");
        }

        this.player2 = new Board(player2);
        this.status = GameStatus.PLACING_SHIPS;
        this.currentTurn = randomizeTurn();
        this.lastActivityAt = Instant.now();
    }

    public synchronized void placeShips(UUID playerId, List<Ship> ships) {
        if (this.status != GameStatus.PLACING_SHIPS) {
            throw new MatchStatusException("Partida não está na fase de posicionamento");
        }

        Board board = getBoardOf(playerId);

        if (board.isReady()) {
            throw new PlayerWithoutPermissionException("Jogador já posicionou seus navios");
        }

        board.setShip(ships);

        if (areBothPlayersReady()) {
            this.status = GameStatus.IN_PROGRESS;
        }

        this.lastActivityAt = Instant.now();
    }

    public synchronized Shot shoot(UUID shooterId, Position position, String attackType) {
        if (this.status != GameStatus.IN_PROGRESS) {
            throw new MatchStatusException("Partida não está em andamento");
        }
        if (!isPlayerTurn(shooterId)) {
            throw new PlayerWithoutPermissionException("Não é o turno desse jogador");
        }

        Board opponentBoard = getOpponentBoardOf(shooterId);

        boolean alreadyShot = opponentBoard.getShots().stream()
                .anyMatch(s -> s.getPosition().getRow() == position.getRow()
                        && s.getPosition().getCol() == position.getCol());

        if (alreadyShot) {
            throw new PlayerWithoutPermissionException("Jogador já atirou nessa posição");
        }

        Shot shot;

        if ("TORPEDO".equalsIgnoreCase(attackType)) {
            Board shooterBoard = getBoardOf(shooterId);
            if (!shooterBoard.isTorpedoAvailable()) {
                throw new PlayerWithoutPermissionException("Torpedo já foi utilizado nesta partida");
            }
            if (gameMode == GameMode.TACTICAL && shooterBoard.isEmpDisabled()) {
                throw new PlayerWithoutPermissionException("Habilidades desativadas por EMP");
            }
            shooterBoard.markTorpedoUsed();
            shot = opponentBoard.receiveTorpedo(position);
        } else {
            shot = opponentBoard.receiveShot(position);
        }

        // Decrementa EMP do jogador que atacou (conta cada ataque como 1 turno de EMP)
        if (gameMode == GameMode.TACTICAL) {
            Board shooterBoard = getBoardOf(shooterId);
            shooterBoard.decrementEmpDisabledTurns();
        }

        if (opponentBoard.allShipsSunk()) {
            finish(shooterId);
        } else if (!shot.isHit()) {
            switchTurn();
        }

        this.lastActivityAt = Instant.now();
        return shot;
    }

    /**
     * Usa uma habilidade no modo tático.
     * Retorna resultado contextual dependendo da habilidade.
     *
     * Regras de turno:
     * - SHIELD: não consome turno de ataque (ação defensiva no próprio turno)
     * - RADAR: consome o turno (equivale ao ataque daquele turno)
     * - EMP_NAVAL: consome o turno (equivale ao ataque daquele turno)
     */
    public synchronized List<Position> useAbility(UUID playerId, AbilityType ability, Position target) {
        if (this.status != GameStatus.IN_PROGRESS) {
            throw new MatchStatusException("Partida não está em andamento");
        }
        if (this.gameMode != GameMode.TACTICAL) {
            throw new MatchStatusException("Habilidades só estão disponíveis no modo tático");
        }
        if (!isPlayerTurn(playerId)) {
            throw new PlayerWithoutPermissionException("Não é o turno desse jogador");
        }

        Board myBoard = getBoardOf(playerId);
        Board opponentBoard = getOpponentBoardOf(playerId);

        // Verifica EMP
        if (myBoard.isEmpDisabled()) {
            throw new PlayerWithoutPermissionException("Habilidades desativadas por EMP");
        }

        this.lastActivityAt = Instant.now();

        switch (ability) {
            case SHIELD -> {
                if (!myBoard.isShieldAvailable()) {
                    throw new PlayerWithoutPermissionException("Escudo não disponível");
                }
                myBoard.activateShield();
                // Não consome turno — jogador ainda pode atacar
                return List.of();
            }
            case RADAR -> {
                if (!myBoard.isRadarAvailable()) {
                    throw new PlayerWithoutPermissionException("Radar já foi utilizado");
                }
                if (target == null) {
                    throw new PlayerWithoutPermissionException("Posição central do radar é obrigatória");
                }
                myBoard.markRadarUsed();
                List<Position> revealed = opponentBoard.executeRadar(target);
                // Consome turno
                switchTurn();
                return revealed;
            }
            case EMP_NAVAL -> {
                if (!myBoard.isEmpNavalAvailable()) {
                    throw new PlayerWithoutPermissionException("EMP Naval já foi utilizado");
                }
                myBoard.markEmpNavalUsed();
                opponentBoard.applyEmp(2);
                // Consome turno
                switchTurn();
                return List.of();
            }
            default -> throw new MatchStatusException("Habilidade inválida");
        }
    }

    public synchronized void forceSwapTurn() {
        // Decrementa EMP do jogador que deu timeout (timeout conta como turno para EMP)
        if (gameMode == GameMode.TACTICAL) {
            Board currentPlayerBoard = getBoardOf(currentTurn);
            currentPlayerBoard.decrementEmpDisabledTurns();
        }

        if (currentTurn.equals(player1.getPlayerId())) {
            this.currentTurn = player2.getPlayerId();
        } else {
            this.currentTurn = player1.getPlayerId();
        }
        this.lastActivityAt = Instant.now();
    }

    public synchronized boolean finish(UUID winnerId) {
        if (this.status == GameStatus.FINISHED) return false;
        this.status = GameStatus.FINISHED;
        this.winnerId = winnerId;
        return true;
    }

    private void switchTurn() {
        if (currentTurn.equals(player1.getPlayerId())) {
            this.currentTurn = player2.getPlayerId();
        } else {
            this.currentTurn = player1.getPlayerId();
        }
    }

    private UUID randomizeTurn() {
        Random random = new Random();
        boolean first = random.nextBoolean();
        return first ? player1.getPlayerId() : player2.getPlayerId();
    }
}
