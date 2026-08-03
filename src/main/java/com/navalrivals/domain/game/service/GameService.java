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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private final GameStorage storage;
    private final GameResultRepository gameResultRepository;
    private final GameWebSocketService gameWebSocketService;
    private final TurnTimerService turnTimerService;
    private final GameResultService gameResultService;
    private final GameEventPublisher gameEventPublisher;

    public Game createGame(User player, GameMode gameMode) {
        var game = new Game(player, gameMode);
        storage.save(game);
        log.info("[GAME] Partida criada — gameId={}, hostId={}, mode={}", game.getId(), player.getId(), gameMode);
        return game;
    }

    public Game joinGame(UUID gameId, User player) {
        var game = storage.findById(gameId)
                .orElseThrow(() -> new NotFoundException("Partida não encontrada"));
        game.join(player);
        storage.save(game);
        log.info("[GAME] Jogador entrou na partida — gameId={}, playerId={}", gameId, player.getId());
        return game;
    }

    public Game placeShips(UUID gameId, User player, List<Ship> ships) {
        var game = storage.findById(gameId)
                .orElseThrow(() -> new NotFoundException("Partida não encontrada"));
        if (!game.hasPlayer(player.getId())) {
            throw new PlayerWithoutPermissionException("Jogador não pertence a essa partida");
        }

        game.placeShips(player.getId(), ships);
        storage.save(game);
        log.info("[GAME] Navios posicionados — gameId={}, playerId={}, shipsCount={}", gameId, player.getId(), ships.size());

        if (game.getStatus() == GameStatus.IN_PROGRESS){
            log.info("[GAME] Ambos posicionaram, partida iniciada — gameId={}, firstTurn={}", gameId, game.getCurrentTurn());
            gameWebSocketService.notifyGameStarted(gameId, player.getId(), game.getCurrentTurn());
            turnTimerService.startTimer(gameId);
        }else{
            gameWebSocketService.notifyOpponentReady(gameId, player.getId());
        }
        return game;
    }

    public record ShootResult(Game game, Shot shot){}

    public ShootResult shoot(UUID gameId, User player, Position positionShot, String attackType) {
        var game = storage.findById(gameId)
                .orElseThrow(() -> new NotFoundException("Partida não encontrada"));

        if (!game.hasPlayer(player.getId())) {
            throw new PlayerWithoutPermissionException("Jogador não pertence a essa partida");
        }

        Shot shot = game.shoot(player.getId(), positionShot, attackType);
        storage.save(game);
        log.info("[GAME] Ataque executado — gameId={}, playerId={}, pos=({},{}), type={}, hit={}",
                gameId, player.getId(), positionShot.getRow(), positionShot.getCol(), attackType, shot.isHit());

        return new ShootResult(game, shot);
    }

    public record AbilityResult(List<Position> positions, Game game){};
    /**
     * Usa uma habilidade no modo tático.
     * Delega para Game.useAbility() que valida regras e executa.
     */
    public AbilityResult useAbility(UUID gameId, User player, AbilityType ability, Position target) {
        var game = storage.findById(gameId)
                .orElseThrow(() -> new NotFoundException("Partida não encontrada"));

        if (!game.hasPlayer(player.getId())) {
            throw new PlayerWithoutPermissionException("Jogador não pertence a essa partida");
        }

        List<Position> result = game.useAbility(player.getId(), ability, target);
        storage.save(game);
        log.info("[GAME] Habilidade usada — gameId={}, playerId={}, ability={}, target={}",
                gameId, player.getId(), ability, target != null ? "(" + target.getRow() + "," + target.getCol() + ")" : "null");

        return new AbilityResult(result, game);
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

        log.debug("[GAME] Estado consultado — gameId={}, playerId={}, status={}", gameId, player.getId(), game.getStatus());

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

    /**
     * Verifica se um game existe em memória (não foi destruído/removido).
     */
    public boolean exists(UUID gameId) {
        return storage.findById(gameId).isPresent();
    }

    /**
     * Finaliza a partida por desistência/saída de um jogador durante IN_PROGRESS.
     * Persiste o resultado, publica GAME_OVER, atualiza stats e remove da memória.
     */
    public boolean forfeitGame(UUID gameId, UUID leavingPlayerId) {
        var game = storage.findById(gameId).orElse(null);
        if (game == null) return false;

        if (game.getStatus() != GameStatus.IN_PROGRESS) {
            return false;
        }

        // Determina o vencedor (quem ficou)
        UUID winnerId;
        if (game.getPlayer1().getPlayerId().equals(leavingPlayerId)) {
            winnerId = game.getPlayer2().getPlayerId();
        } else {
            winnerId = game.getPlayer1().getPlayerId();
        }

        // Finaliza o jogo
        if (!game.finish(winnerId)) {
            return false;
        }

        log.info("[GAME] Forfeit — gameId={}, leavingPlayerId={}, winnerId={}", gameId, leavingPlayerId, winnerId);

        // Persiste resultado ANTES de publicar (frontend busca logo após GAME_OVER)
        gameResultService.persistGameResult(game);

        // Publica GAME_OVER
        gameEventPublisher.publishGameOver(gameId, winnerId, leavingPlayerId, "OPPONENT_SURRENDERED");

        // Atualiza stats dos jogadores de forma assíncrona
        gameResultService.updatePlayerStatsAsync(winnerId, leavingPlayerId);

        // Limpa timer e game da memória
        turnTimerService.cancelTimer(gameId);
        storage.remove(gameId);

        return true;
    }

    public void removeGame(UUID gameId) {
        log.debug("[GAME] Removendo partida da memória — gameId={}", gameId);
        storage.remove(gameId);
    }
}
