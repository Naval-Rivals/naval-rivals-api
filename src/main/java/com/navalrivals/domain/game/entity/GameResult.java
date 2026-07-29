package com.navalrivals.domain.game.entity;

import com.navalrivals.domain.game.enums.GameMode;
import com.navalrivals.domain.game.enums.GameStatus;
import com.navalrivals.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "game_results")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(of = "id")
public class GameResult {

    @Id
    private UUID id;

    @Column(name = "room_code", nullable = false, length = 10)
    private String roomCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "game_mode", nullable = false, length = 10)
    private GameMode gameMode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private User winner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loser_id")
    private User loser;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "winner_shots", nullable = false)
    private int winnerShots;

    @Column(name = "winner_hits", nullable = false)
    private int winnerHits;

    @Column(name = "winner_misses", nullable = false)
    private int winnerMisses;

    @Column(name = "winner_ships_destroyed", nullable = false)
    private int winnerShipsDestroyed;

    @Column(name = "loser_shots", nullable = false)
    private int loserShots;

    @Column(name = "loser_hits", nullable = false)
    private int loserHits;

    @Column(name = "loser_misses", nullable = false)
    private int loserMisses;

    @Column(name = "loser_ships_destroyed", nullable = false)
    private int loserShipsDestroyed;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;
}
