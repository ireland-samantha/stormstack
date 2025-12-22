package com.lightningfirefly.engine.internal.core.match;

import com.lightningfirefly.engine.core.match.Match;
import com.lightningfirefly.engine.core.match.MatchRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory implementation of {@link MatchRepository}.
 *
 * <p>This implementation stores matches in a thread-safe ConcurrentHashMap,
 * keyed by their ID. Provides pure CRUD operations without business logic.
 *
 * <p>Thread-safe: All operations use ConcurrentHashMap for thread safety.
 */
@Slf4j
public class InMemoryMatchRepository implements MatchRepository {

    private final Map<Long, Match> matches = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Match save(Match match) {
        Objects.requireNonNull(match, "match must not be null");
        // If id is 0 or negative, generate a new ID
        Match matchToSave = match;
        if (match.id() <= 0) {
            long newId = idGenerator.getAndIncrement();
            matchToSave = new Match(newId, match.enabledModules(), match.enabledGameMasters());
            log.debug("Generated new match ID: {}", newId);
        }
        log.debug("Saving match: {}", matchToSave.id());
        matches.put(matchToSave.id(), matchToSave);
        return matchToSave;
    }

    @Override
    public void deleteById(long id) {
        log.debug("Deleting match: {}", id);
        matches.remove(id);
    }

    @Override
    public Optional<Match> findById(long id) {
        Match value = matches.get(id);
        log.debug("Find match by id {}: {}", id, value != null ? "found" : "not found");
        return Optional.ofNullable(value);
    }

    @Override
    public List<Match> findAll() {
        return new ArrayList<>(matches.values());
    }

    @Override
    public boolean existsById(long id) {
        return matches.containsKey(id);
    }

    @Override
    public long count() {
        return matches.size();
    }

    /**
     * Clear all matches from the repository.
     * For testing purposes.
     */
    public void clear() {
        matches.clear();
    }
}
