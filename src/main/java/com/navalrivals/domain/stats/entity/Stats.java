package com.navalrivals.domain.stats.entity;

import com.navalrivals.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "stats")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(of = "id")
public class Stats {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private int totalGames;

    @Column(nullable = false)
    private int victories;

    @Column(nullable = false)
    private int defeats;

    public Stats(User user) {
        this.user = user;
        this.totalGames = 0;
        this.victories = 0;
        this.defeats = 0;
    }

    public String getWinRate() {
        if (totalGames == 0) return "0%";
        int rate = (int) Math.round((victories * 100.0) / totalGames);
        return rate + "%";
    }

    public void registerVictory() {
        this.victories++;
        this.totalGames++;
    }

    public void registerDefeat() {
        this.defeats++;
        this.totalGames++;
    }
}
