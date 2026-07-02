package com.navalrivals.domain.room.entity;

import com.navalrivals.domain.room.enums.RoomStatus;
import com.navalrivals.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rooms")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(of = "id")
public class Room {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private User host;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opponent_id")
    private User opponent;

    @Column(name = "game_id")
    private UUID gameId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public Room(User host, String code) {
        this.host = host;
        this.code = code;
        this.status = RoomStatus.WAITING;
        this.createdAt = Instant.now();
    }

    public boolean isFull() {
        return this.opponent != null;
    }

    public boolean isHost(UUID userId) {
        return this.host.getId().equals(userId);
    }

    public boolean isParticipant(UUID userId) {
        if (host.getId().equals(userId)) return true;
        return opponent != null && opponent.getId().equals(userId);
    }
}
