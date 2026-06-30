package com.navalrivals.domain.game.entity;

import com.navalrivals.domain.board.entity.Board;
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

    public Game(User player1) {
        this.id = UUID.randomUUID();
        this.player1 = new Board(player1);
        this.status = GameStatus.WAITING_OPPONENT;
        this.createdAt = Instant.now();
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
    }

    public void placeShips(UUID playerId, List<Ship> ships) {
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
    }

    public Shot shoot(UUID shooterId, Position position) {
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

        Shot shot = opponentBoard.receiveShot(position);

        if (opponentBoard.allShipsSunk()) {
            finish(shooterId);
        } else {
            switchTurn();
        }

        return shot;
    }

    public void finish(UUID winnerId) {
        this.status = GameStatus.FINISHED;
        this.winnerId = winnerId;
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
