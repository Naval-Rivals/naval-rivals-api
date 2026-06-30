package com.navalrivals.domain.game.service;

import com.navalrivals.domain.game.entity.Game;
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

    public Game createGame(User player){
        var game = new Game(player);
        storage.save(game);
        return game;
    }

    public Game joinGame(UUID gameId, User player){
        var game = storage.findById(gameId)
                .orElseThrow(() -> new NotFoundException("Partida não encontrada"));
        game.join(player);
        return game;
    }

    public Game placeShips(UUID gameId, User player, List<Ship> ships){
        var game = storage.findById(gameId)
                .orElseThrow(() -> new NotFoundException("Partida não encontrada"));
        if (!game.hasPlayer(player.getId())){
            throw new PlayerWithoutPermissionException("Jogador não pertence a essa partida");
        }

        game.placeShips(player.getId(), ships);
        return game;
    }

    public Shot shoot(UUID gameId, User player, Position positionShot){
        var game = storage.findById(gameId)
                .orElseThrow(() -> new NotFoundException("Partida não encontrada"));

        if (!game.hasPlayer(player.getId())){
            throw new PlayerWithoutPermissionException("Jogador não pertence a essa partida");
        }

        return game.shoot(player.getId(), positionShot);
    }
}
