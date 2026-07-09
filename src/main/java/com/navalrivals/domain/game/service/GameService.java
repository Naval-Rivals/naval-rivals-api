package com.navalrivals.domain.game.service;

import com.navalrivals.domain.game.dto.GameResultResponse;
import com.navalrivals.domain.game.dto.GameStateResponse;
import com.navalrivals.domain.game.entity.Game;
import com.navalrivals.domain.game.enums.AbilityType;
import com.navalrivals.domain.game.enums.GameMode;
import com.navalrivals.domain.game.enums.GameStatus;
import com.navalrivals.domain.game.repository.GameResultRepository;
import com.navalrivals.domain.game.storage.GameStorage;
import com.navalrivals.domain.position.entity.Position;
import com.navalrivals.domain.ship.entity.Ship;
import com.navalrivals.domain.shot.entity.Shot;
import com.navalrivals.domain.user.entity.User;
import com.navalrivals.infra.exception.exceptions.NotFoundException;
import com.navalrivals.infra.exception.exceptions.PlayerWithoutPermissionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GameService {

    private final GameStorage storage;
    private final GameResultRepository gameResultRepository;
    private final GameWebSocketService gameWebSocketService;
    private final TurnTimerService turnTimerService;

    public Game createGame(User player, GameMode gameMode) {
        var game = new Game(player, gameMode);
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

    public Shot shoot(UUID gameId, User player, Position positionShot, String attackType) {
        var game = storage.findById(gameId)
                .orElseThrow(() -> new NotFoundException("Partida não encontrada"));

        if (!game.hasPlayer(player.getId())) {
            throw new PlayerWithoutPermissionException("Jogador não pertence a essa partida");
        }

        Shot shot = game.shoot(player.getId(), positionShot, attackType);

        return shot;
    }

    /**
     * Usa uma habilidade no modo tático.
     * Delega para Game.useAbility() que valida regras e executa.
     */
    public List<Position> useAbility(UUID gameId, User player, AbilityType ability, Position target) {
        var game = storage.findById(gameId)
                .orElseThrow(() -> new NotFoundException("Partida não encontrada"));

        if (!game.hasPlayer(player.getId())) {
            throw new PlayerWithoutPermissionException("Jogador não pertence a essa partida");
        }

        return game.useAbility(player.getId(), ability, target);
    }

    public GameResultResponse getGameResult(UUID gameId) {
        var result = gameResultRepository.findById(gameId)
                .orElseThrow(() -> new NotFoundException("Resultado da partida não encontrado"));
        return new GameResultResponse(result);
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

        // Habilidades (null se modo clássico)
        GameStateResponse.AbilitiesInfo abilities = null;
        if (game.getGameMode() == GameMode.TACTICAL) {
            abilities = new GameStateResponse.AbilitiesInfo(
                    myBoard.isRadarAvailable(),
                    myBoard.getShieldCharges(),
                    myBoard.isShieldActive(),
                    myBoard.isEmpNavalAvailable(),
                    myBoard.getEmpDisabledTurns()
            );
        }

        return new GameStateResponse(
                game.getId(),
                game.getStatus(),
                game.getGameMode(),
                game.getCurrentTurn(),
                player.getId(),
                myShips,
                myShotsReceived,
                myShotsMade,
                myBoard.isTorpedoAvailable(),
                abilities
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
