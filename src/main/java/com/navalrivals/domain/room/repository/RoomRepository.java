package com.navalrivals.domain.room.repository;

import com.navalrivals.domain.room.entity.Room;
import com.navalrivals.domain.room.enums.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {
    Optional<Room> findByCode(String code);
    boolean existsByCode(String code);
    Optional<Room> findByHostIdAndStatus(UUID hostId, RoomStatus status);
    Optional<Room> findByGameId(UUID gameId);
}
