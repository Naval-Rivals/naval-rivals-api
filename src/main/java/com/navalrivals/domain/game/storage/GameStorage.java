package com.navalrivals.domain.game.storage;

import com.navalrivals.domain.game.entity.Game;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameStorage {

    private static final String KEY_PREFIX = "game:";
    private static final Duration GAME_TTL = Duration.ofMinutes(25);

    private final RedisTemplate<String, Game> redisTemplate;

    public void save(Game game) {
        redisTemplate.opsForValue().set(KEY_PREFIX + game.getId(), game, GAME_TTL);
        log.debug("[STORAGE] Game salvo no Redis — gameId={}", game.getId());
    }

    public Optional<Game> findById(UUID gameId) {
        Game game = redisTemplate.opsForValue().get(KEY_PREFIX + gameId);
        return Optional.ofNullable(game);
    }

    public void remove(UUID gameId) {
        Boolean removed = redisTemplate.delete(KEY_PREFIX + gameId);
        if (Boolean.TRUE.equals(removed)) {
            log.debug("[STORAGE] Game removido do Redis — gameId={}", gameId);
        }
    }

    /**
     * Remove todos os jogos que satisfazem o predicado.
     * Retorna a quantidade removida.
     */
    public int removeIf(Predicate<Game> condition) {
        int count = 0;
        var scanOptions = ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(100).build();

        try(var cursor = redisTemplate.scan(scanOptions)){
            while (cursor.hasNext()){
                String key = (String) cursor.next();
                Game game = redisTemplate.opsForValue().get(key);
                if(game != null && condition.test(game)){
                    redisTemplate.delete(key);
                    count++;
                }
            }
        }

        if (count > 0) {
            log.info("[STORAGE] Cleanup — {} games removidos", count);
        }
        return count;
    }
}
