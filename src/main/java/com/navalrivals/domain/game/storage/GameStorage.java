package com.navalrivals.domain.game.storage;

import com.navalrivals.domain.game.entity.Game;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@Slf4j
@Component
public class GameStorage {

    private final Map<UUID, Game> games = new ConcurrentHashMap<>();

    public void save(Game game) {
        games.put(game.getId(), game);
        log.debug("[STORAGE] Game salvo em memória — gameId={}, totalGames={}", game.getId(), games.size());
    }

    public Optional<Game> findById(UUID gameId) {
        return Optional.ofNullable(games.get(gameId));
    }

    public void remove(UUID gameId) {
        var removed = games.remove(gameId);
        if (removed != null) {
            log.debug("[STORAGE] Game removido da memória — gameId={}, totalGames={}", gameId, games.size());
        }
    }

    /**
     * Remove todos os jogos que satisfazem o predicado.
     * Retorna a quantidade removida.
     */
    public int removeIf(Predicate<Game> condition) {
        int count = 0;
        var iterator = games.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (condition.test(entry.getValue())) {
                iterator.remove();
                count++;
            }
        }
        if (count > 0) {
            log.info("[STORAGE] Cleanup — {} games removidos, totalGames={}", count, games.size());
        }
        return count;
    }
}
