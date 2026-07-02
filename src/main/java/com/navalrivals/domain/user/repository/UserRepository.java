package com.navalrivals.domain.user.repository;

import com.navalrivals.domain.ranking.dto.RankingProjection;
import com.navalrivals.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<UserDetails> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);

    @Query(value = """
            SELECT
                ROW_NUMBER() OVER (ORDER BY s.victories DESC, s.total_games ASC) AS position,
                u.id AS userId,
                u.nickname AS nickname,
                s.victories AS victories,
                s.total_games AS totalGames,
                CASE
                    WHEN s.total_games = 0 THEN '0%'
                    ELSE CONCAT(ROUND((s.victories * 100.0) / s.total_games), '%')
                END AS winRate
            FROM users u
            JOIN stats s ON s.user_id = u.id
            ORDER BY s.victories DESC, s.total_games ASC
            """,
            countQuery = "SELECT COUNT(*) FROM users u JOIN stats s ON s.user_id = u.id",
            nativeQuery = true)
    Page<RankingProjection> findRanking(Pageable pageable);
}
