package com.lightningfirefly.engine.internal.core.match;

import com.lightningfirefly.engine.core.exception.EntityNotFoundException;
import com.lightningfirefly.engine.core.match.Match;
import com.lightningfirefly.engine.core.match.MatchRepository;
import com.lightningfirefly.engine.core.match.MatchService;
import com.lightningfirefly.engine.ext.module.ModuleResolver;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * In-memory implementation of {@link MatchService}.
 *
 * <p>This implementation provides business logic for match operations,
 * delegating persistence to {@link MatchRepository}.
 *
 * <p>Business rules enforced:
 * <ul>
 *   <li>All enabled modules must exist before match creation</li>
 *   <li>Match must exist before deletion</li>
 * </ul>
 *
 * <p>SOLID compliance:
 * <ul>
 *   <li>SRP: Business logic only, persistence delegated to repository</li>
 *   <li>DIP: Depends on MatchRepository and ModuleResolver abstractions</li>
 * </ul>
 */
@Slf4j
public class InMemoryMatchService implements MatchService {

    private final MatchRepository matchRepository;
    private final ModuleResolver moduleResolver;

    public InMemoryMatchService(MatchRepository matchRepository, ModuleResolver moduleResolver) {
        this.matchRepository = Objects.requireNonNull(matchRepository, "matchRepository must not be null");
        this.moduleResolver = Objects.requireNonNull(moduleResolver, "moduleResolver must not be null");
    }

    @Override
    public Match createMatch(Match match) {
        Objects.requireNonNull(match, "match must not be null");
        log.info("Creating match with id: {}", match.id());

        // Validate all enabled modules exist
        for (String module : match.enabledModules()) {
            if (!moduleResolver.hasModule(module)) {
                log.warn("Module not found: {}", module);
                throw new EntityNotFoundException(String.format("Module %s not found.", module));
            }
        }

        return matchRepository.save(match);
    }

    @Override
    public void deleteMatch(long matchId) {
        log.info("Deleting match: {}", matchId);
        if (!matchRepository.existsById(matchId)) {
            throw new EntityNotFoundException(String.format("Match %d not found.", matchId));
        }
        matchRepository.deleteById(matchId);
    }

    @Override
    public Optional<Match> getMatch(long matchId) {
        return matchRepository.findById(matchId);
    }

    @Override
    public Match getMatchOrThrow(long matchId) {
        return matchRepository.findById(matchId)
                .orElseThrow(() -> new EntityNotFoundException(
                        String.format("Match %d not found.", matchId)));
    }

    @Override
    public List<Match> getAllMatches() {
        return matchRepository.findAll();
    }

    @Override
    public boolean matchExists(long matchId) {
        return matchRepository.existsById(matchId);
    }
}
