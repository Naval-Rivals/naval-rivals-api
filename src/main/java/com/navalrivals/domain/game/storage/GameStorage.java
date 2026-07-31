package com.navalrivals.domain.game.storage;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.navalrivals.domain.game.entity.Game;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

@Slf4j
@Component
public class GameStorage {

    private static final String KEY_PREFIX = "game:";
    private static final Duration GAME_TTL = Duration.ofMinutes(25);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public GameStorage(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        this.objectMapper.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE);
        this.objectMapper.setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);
        this.objectMapper.setVisibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.NONE);
    }

    public void save(Game game) {
        try {
            String json = objectMapper.writeValueAsString(game);
            redisTemplate.opsForValue().set(KEY_PREFIX + game.getId(), json, GAME_TTL);
            log.debug("[STORAGE] Game salvo no Redis — gameId={}", game.getId());
        } catch (Exception e) {
            throw new RuntimeException("Erro ao serializar Game para Redis", e);
        }
    }

    public Optional<Game> findById(UUID gameId) {
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + gameId);
            if (json == null) return Optional.empty();
            log.info("[STORAGE DEBUG] JSON do Redis: {}", json);
            Game game = objectMapper.readValue(json, Game.class);
            log.info("[STORAGE DEBUG] Game desserializado — player1.playerId={}", 
                    game.getPlayer1() != null ? game.getPlayer1().getPlayerId() : "null");
            return Optional.of(game);
        } catch (Exception e) {
            log.error("[STORAGE] Erro ao deserializar Game do Redis — gameId={}", gameId, e);
            return Optional.empty();
        }
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

        try (var cursor = redisTemplate.scan(scanOptions)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                String json = redisTemplate.opsForValue().get(key);
                if (json == null) continue;

                try {
                    Game game = objectMapper.readValue(json, Game.class);
                    if (condition.test(game)) {
                        redisTemplate.delete(key);
                        count++;
                    }
                } catch (Exception e) {
                    log.warn("[STORAGE] Erro ao deserializar game na key {}, removendo", key);
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
