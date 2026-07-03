package com.navalrivals.domain.game.service;

import com.navalrivals.domain.board.entity.Board;
import com.navalrivals.domain.game.dto.GameResultResponse;
import com.navalrivals.domain.game.dto.GameStateResponse;
import com.navalrivals.domain.game.entity.Game;
import com.navalrivals.domain.game.entity.GameResult;
import com.navalrivals.domain.stats.entity.Stats;
import com.navalrivals.domain.game.enums.GameStatus;
import com.navalrivals.domain.game.repository.GameResultRepository;
import com.navalrivals.domain.game.storage.GameStorage;
import com.navalrivals.domain.position.entity.Position;
import com.navalrivals.domain.room.entity.Room;
import com.navalrivals.domain.room.repository.RoomRepository;
import com.navalrivals.domain.ship.entity.Ship;
import com.navalrivals.domain.shot.entity.Shot;
import com.navalrivals.domain.user.entity.User;
import com.navalrivals.domain.user.repository.UserRepository;
import com.navalrivals.infra.exception.exceptions.NotFoundException;
import com.navalrivals.infra.exception.exceptions.PlayerWithoutPermissionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GameService {

    private final GameStorage storage;
    private final GameResultRepository gameResultRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final GameWebSocketService gameWebSocketService;
    private final TurnTimerService turnTimerService;

    public Game createGame(User player) {
        var game = new Game(player);
        storage.save(game);
        return game;
    }

    public Game joinGame(UUID gameId, User player) {
        var game = storage.findById(gameId)
                .orElseThrow(() -> new NotFoundException("Partida não encontrada"));
        game.join(player);
        return game;
    }

    public Game placeShips(UUID gameId, User player, List<Ship> ships) {
        var game = storage.findById(gameId)
                .orElseThrow(() -> new NotFoundException("Partida não encontrada"));
        if (!game.hasPlayer(player.getId())) {
            throw new PlayerWithoutPermissionException("Jogador não pertence a essa partida");
        }

        game.placeShips(player.getId(), ships);

        if (game.getStatus() == GameStatus.IN_PROGRESS){
            gameWebSocketService.notifyGameStarted(gameId, player.getId(), game.getCurrentTurn());
            turnTimerService.startTimer(gameId);
        }else{
            gameWebSocketService.notifyOpponentReady(gameId, player.getId());
        }
        return game;
    }

    public Shot shoot(UUID gameId, User player, Position positionShot) {
        var game = storage.findById(gameId)
                .orElseThrow(() -> new NotFoundException("Partida não encontrada"));

        if (!game.hasPlayer(player.getId())) {
            throw new PlayerWithoutPermissionException("Jogador não pertence a essa partida");
        }

        Shot shot = game.shoot(player.getId(), positionShot);

        if (game.getStatus() == GameStatus.FINISHED) {
            persistGameResult(game);
        }

        return shot;
    }

    public GameResultResponse getGameResult(UUID gameId) {
        var result = gameResultRepository.findById(gameId)
                .orElseThrow(() -> new NotFoundException("Resultado da partida não encontrado"));
        return new GameResultResponse(result);
    }

    @Transactional
    public void persistGameResult(Game game) {
        if (gameResultRepository.existsById(game.getId())) {
            return;
        }

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

        // Update stats
        Stats winnerStats = winner.getStats();
        if (winnerStats != null) {
            winnerStats.registerVictory();
        }
        Stats loserStats = loser.getStats();
        if (loserStats != null) {
            loserStats.registerDefeat();
        }
    }

    private UUID getLoserIdFrom(Game game, UUID winnerId) {
        if (game.getPlayer1().getPlayerId().equals(winnerId)) {
            return game.getPlayer2().getPlayerId();
        }
        return game.getPlayer1().getPlayerId();
    }

    public GameStateResponse getGameState(UUID gameId, User player) {
        var game = storage.findById(gameId)
                .orElseThrow(() -> new NotFoundException("Partida não encontrada"));
        if (!game.hasPlayer(player.getId())) {
            throw new PlayerWithoutPermissionException("Jogador não pertence a essa partida");
        }

        var myBoard = game.getBoardOf(player.getId());
        var opponentBoard = game.getOpponentBoardOf(player.getId());

        var myShips = myBoard.getShips().stream()
                .map(GameStateResponse.ShipInfo::new)
                .toList();
        var myShotsReceived = myBoard.getShots().stream()
                .map(GameStateResponse.ShotInfo::new)
                .toList();
        var myShotsMade = opponentBoard != null
                ? opponentBoard.getShots().stream().map(GameStateResponse.ShotInfo::new).toList()
                : List.<GameStateResponse.ShotInfo>of();

        return new GameStateResponse(
                game.getId(),
                game.getStatus(),
                game.getCurrentTurn(),
                player.getId(),
                myShips,
                myShotsReceived,
                myShotsMade
        );
    }

    public Game findById(UUID gameId){
        return storage.findById(gameId)
                .orElseThrow(() -> new NotFoundException("Partida não encontrada"));
    }

    public void removeGame(UUID gameId) {
        storage.remove(gameId);
    }
}
