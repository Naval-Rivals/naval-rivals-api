package com.navalrivals.domain.game.repository;

import com.navalrivals.domain.game.entity.GameResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface GameResultRepository extends JpaRepository<GameResult, UUID> {

    @Query(value = "SELECT gr FROM GameResult gr " +
            "JOIN FETCH gr.winner " +
            "JOIN FETCH gr.loser " +
            "WHERE gr.winner.id = :userId OR gr.loser.id = :userId " +
            "ORDER BY gr.finishedAt DESC",
            countQuery = "SELECT COUNT(gr) FROM GameResult gr " +
                    "WHERE gr.winner.id = :userId OR gr.loser.id = :userId")
    Page<GameResult> findByUserId(@Param("userId") UUID userId, Pageable pageable);
}
