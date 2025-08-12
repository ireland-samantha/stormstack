package com.lightningfirefly.engine.internal.core.match;

import com.lightningfirefly.engine.core.match.PlayerMatch;
import com.lightningfirefly.engine.core.match.PlayerMatchRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link PlayerMatchRepository}.
 *
 * <p>This implementation stores player-match associations in a thread-safe ConcurrentHashMap,
 * using a composite key of (playerId, matchId).
 *
 * <p>Thread-safe: All operations use ConcurrentHashMap for thread safety.
 */
@Slf4j
public class InMemoryPlayerMatchRepository implements PlayerMatchRepository {

    private final Map<CompositeKey, PlayerMatch> playerMatches = new ConcurrentHashMap<>();

    private record CompositeKey(long playerId, long matchId) {}

    @Override
    public PlayerMatch save(PlayerMatch playerMatch) {
        Objects.requireNonNull(playerMatch, "playerMatch must not be null");
        CompositeKey key = new CompositeKey(playerMatch.playerId(), playerMatch.matchId());
        log.debug("Saving player-match: playerId={}, matchId={}", playerMatch.playerId(), playerMatch.matchId());
        playerMatches.put(key, playerMatch);
        return playerMatch;
    }

    @Override
    public void deleteByPlayerAndMatch(long playerId, long matchId) {
        CompositeKey key = new CompositeKey(playerId, matchId);
        log.debug("Deleting player-match: playerId={}, matchId={}", playerId, matchId);
        playerMatches.remove(key);
    }

    @Override
    public Optional<PlayerMatch> findByPlayerAndMatch(long playerId, long matchId) {
        CompositeKey key = new CompositeKey(playerId, matchId);
        PlayerMatch value = playerMatches.get(key);
        log.debug("Find player-match by playerId={}, matchId={}: {}",
                playerId, matchId, value != null ? "found" : "not found");
        return Optional.ofNullable(value);
    }

    @Override
    public List<PlayerMatch> findByMatchId(long matchId) {
        List<PlayerMatch> result = new ArrayList<>();
        for (PlayerMatch pm : playerMatches.values()) {
            if (pm.matchId() == matchId) {
                result.add(pm);
            }
        }
        return result;
    }

    @Override
    public List<PlayerMatch> findByPlayerId(long playerId) {
        List<PlayerMatch> result = new ArrayList<>();
        for (PlayerMatch pm : playerMatches.values()) {
            if (pm.playerId() == playerId) {
                result.add(pm);
            }
        }
        return result;
    }

    @Override
    public boolean existsByPlayerAndMatch(long playerId, long matchId) {
        CompositeKey key = new CompositeKey(playerId, matchId);
        return playerMatches.containsKey(key);
    }

    @Override
    public long count() {
        return playerMatches.size();
    }

    /**
     * Clear all player-match associations from the repository.
     * For testing purposes.
     */
    public void clear() {
        playerMatches.clear();
    }
}
