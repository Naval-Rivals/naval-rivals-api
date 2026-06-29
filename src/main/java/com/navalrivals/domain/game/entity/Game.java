package com.navalrivals.domain.game.entity;

import com.navalrivals.domain.board.entity.Board;
import com.navalrivals.domain.game.enums.GameStatus;
import com.navalrivals.domain.user.entity.User;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class Game {

    private final UUID id;
    private final Board player1;
    private final Board player2;
    private UUID currentTurn;
    private GameStatus status;
    private User winner;
    private final Instant createdAt;

    public Game(User player1, User player2){
        this.id = UUID.randomUUID();
        this.player1 = new Board(player1);
        this.player2 = new Board(player2);
        this.currentTurn = generateCurrentTurn();
        this.status = GameStatus.PLACING_SHIPS;
        this.createdAt = Instant.now();
    }

    public Board getBoardOf(UUID playerId) {
        if (player1.getPlayerId().equals(playerId)) return player1;
        if (player2.getPlayerId().equals(playerId)) return player2;
        throw new IllegalArgumentException("Jogador não está nessa partida");
    }

    public UUID generateCurrentTurn(){

    }

}
