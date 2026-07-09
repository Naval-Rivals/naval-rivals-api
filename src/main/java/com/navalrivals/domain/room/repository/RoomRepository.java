package com.navalrivals.domain.room.repository;

import com.navalrivals.domain.room.entity.Room;
import com.navalrivals.domain.room.enums.RoomStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {
    Optional<Room> findByCode(String code);
    boolean existsByCode(String code);
    Optional<Room> findByHostIdAndStatus(UUID hostId, RoomStatus status);
    Optional<Room> findByGameId(UUID gameId);
    List<Room> findByStatusOrderByCreatedAtDesc(RoomStatus status);
    List<Room> findByStatusAndCreatedAtBefore(RoomStatus status, Instant threshold);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.code = :code")
    Optional<Room> findByCodeForUpdate(@Param("code") String code);
}
