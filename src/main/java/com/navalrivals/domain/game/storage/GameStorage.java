package com.navalrivals.domain.game.storage;

import com.navalrivals.domain.game.entity.Game;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GameStorage {

    private final Map<UUID, Game> games = new ConcurrentHashMap<>();

    public void save(Game game){
        games.put(game.getId(), game);
    }

    public Optional<Game> findById(UUID gameId){
        return Optional.ofNullable(games.get(gameId));
    }

    public void remove(UUID gameId){
        games.remove((gameId));
    }
}
