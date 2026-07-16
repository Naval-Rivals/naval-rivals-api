package com.navalrivals.domain.room.service;

import com.navalrivals.domain.room.entity.Room;
import com.navalrivals.domain.room.enums.RoomStatus;
import com.navalrivals.domain.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Scheduler de fallback que remove salas WAITING com mais de 5 minutos.
 *
 * Cenários onde é necessário:
 * - Host não se registrou via WS (frontend não implementou /app/room/{roomId}/register)
 * - Desconexão não foi detectada por erro de rede
 * - Qualquer outro caso onde a sala ficou órfã no banco
 *
 * Roda a cada 1 minuto.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomCleanupScheduler {

    private static final int MAX_WAITING_MINUTES = 5;

    private final RoomRepository roomRepository;
    private final RoomSessionService roomSessionService;
    private final LobbySSEService lobbySSEService;

    @Scheduled(fixedRate = 60_000) // A cada 1 minuto
    @Transactional
    public void cleanupExpiredWaitingRooms() {
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

        lobbySSEService.notifyLobbyUpdated();
        log.info("Cleanup: {} salas WAITING expiradas removidas", expiredRooms.size());
    }
}
