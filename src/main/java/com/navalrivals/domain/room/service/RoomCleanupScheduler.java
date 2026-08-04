package com.navalrivals.domain.room.service;

import com.navalrivals.domain.room.entity.Room;
import com.navalrivals.domain.room.enums.RoomStatus;
import com.navalrivals.domain.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Scheduler de fallback que remove salas WAITING com mais de 5 minutos.
 *
 * Usa distributed lock para garantir que apenas UMA instância executa o cleanup por vez.
 *
 * Roda a cada 1 minuto.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomCleanupScheduler {

    private static final int MAX_WAITING_MINUTES = 5;
    private static final String LOCK_KEY = "scheduler-lock:room-cleanup";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private final RoomRepository roomRepository;
    private final RoomSessionService roomSessionService;
    private final LobbySSEService lobbySSEService;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void cleanupExpiredWaitingRooms() {
        // Distributed lock — apenas uma instância executa
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            return;
        }

        try {
            Instant threshold = Instant.now().minusSeconds(MAX_WAITING_MINUTES * 60L);
            List<Room> expiredRooms = roomRepository.findByStatusAndCreatedAtBefore(RoomStatus.WAITING, threshold);

            if (expiredRooms.isEmpty()) {
                return;
            }

            for (Room room : expiredRooms) {
                roomSessionService.unregisterRoom(room.getId());
                roomRepository.delete(room);
                log.info("Cleanup: sala {} (code={}) removida por expiração (criada em {})",
                        room.getId(), room.getCode(), room.getCreatedAt());
            }

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        lobbySSEService.notifyLobbyUpdated();
                    }
                });
            } else {
                lobbySSEService.notifyLobbyUpdated();
            }
            log.info("Cleanup: {} salas WAITING expiradas removidas", expiredRooms.size());
        } finally {
            redisTemplate.delete(LOCK_KEY);
        }
    }
}
