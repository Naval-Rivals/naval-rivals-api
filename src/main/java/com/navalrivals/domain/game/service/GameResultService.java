package com.navalrivals.domain.game.service;

import com.navalrivals.domain.board.entity.Board;
import com.navalrivals.domain.game.entity.Game;
import com.navalrivals.domain.game.entity.GameResult;
import com.navalrivals.domain.game.repository.GameResultRepository;
import com.navalrivals.domain.room.entity.Room;
import com.navalrivals.domain.room.repository.RoomRepository;
import com.navalrivals.domain.ship.entity.Ship;
import com.navalrivals.domain.shot.entity.Shot;
import com.navalrivals.domain.stats.entity.Stats;
import com.navalrivals.domain.user.entity.User;
import com.navalrivals.domain.user.repository.UserRepository;
import com.navalrivals.infra.exception.exceptions.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameResultService {

    private final GameResultRepository gameResultRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    /**
     * Persiste o GameResult no banco de forma síncrona.
     * Deve ser chamado ANTES de publicar GAME_OVER para garantir que o frontend
     * consiga buscar o resultado via GET /games/{id}/result.
     */
    @Transactional
    public void persistGameResult(Game game) {
        if (gameResultRepository.existsById(game.getId())) {
            return;
        }

        try {
            doSaveGameResult(game);
            log.info("Resultado do jogo {} persistido com sucesso", game.getId());
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.debug("GameResult {} já persistido por outra thread", game.getId());
        }
    }

    /**
     * Atualiza stats dos jogadores (vitórias/derrotas) de forma assíncrona.
     * Não bloqueia o fluxo principal, pois o frontend não depende disso imediatamente.
     */
    @Async("taskExecutor")
    @Transactional
    public void updatePlayerStatsAsync(UUID winnerId, UUID loserId) {
        try {
            User winner = userRepository.findById(winnerId)
                    .orElseThrow(() -> new NotFoundException("Vencedor não encontrado"));
            User loser = userRepository.findById(loserId)
                    .orElseThrow(() -> new NotFoundException("Perdedor não encontrado"));

            Stats winnerStats = winner.getStats();
            if (winnerStats != null) {
                winnerStats.registerVictory();
                userRepository.save(winner);
            }
            Stats loserStats = loser.getStats();
            if (loserStats != null) {
                loserStats.registerDefeat();
                userRepository.save(loser);
            }
            log.debug("Stats atualizadas: winner={}, loser={}", winnerId, loserId);
        } catch (Exception e) {
            log.error("Erro ao atualizar stats dos jogadores: {}", e.getMessage(), e);
        }
    }

    private void doSaveGameResult(Game game) {
        UUID winnerId = game.getWinnerId();
        UUID loserId = getLoserIdFrom(game, winnerId);

        User winner = userRepository.findById(winnerId)
                .orElseThrow(() -> new NotFoundException("Vencedor não encontrado"));
        User loser = userRepository.findById(loserId)
                .orElseThrow(() -> new NotFoundException("Perdedor não encontrado"));

        String roomCode = roomRepository.findByGameId(game.getId())
                .map(Room::getCode)
                .orElse("N/A");

        Board winnerBoard = game.getOpponentBoardOf(loserId);
        Board loserBoard = game.getOpponentBoardOf(winnerId);

        long durationSeconds = Duration.between(game.getCreatedAt(), Instant.now()).getSeconds();

        int winnerShots = loserBoard.getShots().size();
        int winnerHits = (int) loserBoard.getShots().stream().filter(Shot::isHit).count();
        int winnerMisses = winnerShots - winnerHits;
        int winnerShipsDestroyed = (int) loserBoard.getShips().stream().filter(Ship::isSunken).count();

        int loserShots = winnerBoard.getShots().size();
        int loserHits = (int) winnerBoard.getShots().stream().filter(Shot::isHit).count();
        int loserMisses = loserShots - loserHits;
        int loserShipsDestroyed = (int) winnerBoard.getShips().stream().filter(Ship::isSunken).count();

        GameResult result = new GameResult();
        result.setId(game.getId());
        result.setRoomCode(roomCode);
        result.setStatus(game.getStatus());
        result.setGameMode(game.getGameMode());
        result.setWinner(winner);
        result.setLoser(loser);
        result.setDurationSeconds(durationSeconds);
        result.setWinnerShots(winnerShots);
        result.setWinnerHits(winnerHits);
        result.setWinnerMisses(winnerMisses);
        result.setWinnerShipsDestroyed(winnerShipsDestroyed);
        result.setLoserShots(loserShots);
        result.setLoserHits(loserHits);
        result.setLoserMisses(loserMisses);
        result.setLoserShipsDestroyed(loserShipsDestroyed);
        result.setFinishedAt(Instant.now());

        gameResultRepository.save(result);
    }

    private UUID getLoserIdFrom(Game game, UUID winnerId) {
        if (game.getPlayer1().getPlayerId().equals(winnerId)) {
            return game.getPlayer2().getPlayerId();
        }
        return game.getPlayer1().getPlayerId();
    }
}
