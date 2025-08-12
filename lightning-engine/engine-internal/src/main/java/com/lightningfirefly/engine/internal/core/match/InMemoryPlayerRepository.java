package com.lightningfirefly.engine.internal.core.match;

import com.lightningfirefly.engine.core.match.Player;
import com.lightningfirefly.engine.core.match.PlayerRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link PlayerRepository}.
 *
 * <p>This implementation stores players in a thread-safe ConcurrentHashMap,
 * keyed by their ID. Provides pure CRUD operations without business logic.
 *
 * <p>Thread-safe: All operations use ConcurrentHashMap for thread safety.
 */
@Slf4j
public class InMemoryPlayerRepository implements PlayerRepository {

    private final Map<Long, Player> players = new ConcurrentHashMap<>();

    @Override
    public Player save(Player player) {
        Objects.requireNonNull(player, "player must not be null");
        log.debug("Saving player: {}", player.id());
        players.put(player.id(), player);
        return player;
    }

    @Override
    public void deleteById(long id) {
        log.debug("Deleting player: {}", id);
        players.remove(id);
    }

    @Override
    public Optional<Player> findById(long id) {
        Player value = players.get(id);
        log.debug("Find player by id {}: {}", id, value != null ? "found" : "not found");
        return Optional.ofNullable(value);
    }

    @Override
    public List<Player> findAll() {
        return new ArrayList<>(players.values());
    }

    @Override
    public boolean existsById(long id) {
        return players.containsKey(id);
    }

    @Override
    public long count() {
        return players.size();
    }

    /**
     * Clear all players from the repository.
     * For testing purposes.
     */
    public void clear() {
        players.clear();
    }
}
