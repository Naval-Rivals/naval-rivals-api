package com.navalrivals.domain.game.entity;

import com.navalrivals.domain.board.entity.Board;
import com.navalrivals.domain.user.entity.User;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "games")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class Game {

    private UUID id;
    private User player1;
    private User player2;
    private Board board1;
    private Board board2;

}
