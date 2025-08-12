package com.lightningfirefly.engine.core.match;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for Match business operations.
 *
 * <p>Provides business-level operations with validation, authorization,
 * and domain logic. Uses {@link MatchRepository} for persistence.
 *
 * <p>SOLID compliance:
 * <ul>
 *   <li>SRP: Handles business logic only, delegates persistence to repository</li>
 *   <li>ISP: Separate from MatchRepository with distinct responsibilities</li>
 *   <li>DIP: Depends on MatchRepository abstraction</li>
 * </ul>
 */
public interface MatchService {

    /**
     * Create a new match with validation.
     *
     * <p>Validates that all enabled modules exist before creating.
     *
     * @param match the match to create (must not be null)
     * @return the created match with server-generated ID
     * @throws com.lightningfirefly.engine.core.exception.EntityNotFoundException if any module doesn't exist
     */
    Match createMatch(Match match);

    /**
     * Create a new match with server-generated ID.
     *
     * <p>Validates that all enabled modules exist before creating.
     * The match ID is generated server-side.
     *
     * @param enabledModules the list of module names to enable
     * @return the created match with server-generated ID
     * @throws com.lightningfirefly.engine.core.exception.EntityNotFoundException if any module doesn't exist
     */
    default Match createMatch(List<String> enabledModules) {
        return createMatch(new Match(0, enabledModules, List.of()));
    }

    /**
     * Delete a match by ID.
     *
     * @param matchId the match ID
     * @throws com.lightningfirefly.engine.core.exception.EntityNotFoundException if match doesn't exist
     */
    void deleteMatch(long matchId);

    /**
     * Get a match by ID.
     *
     * @param matchId the match ID
     * @return an Optional containing the match if found
     */
    Optional<Match> getMatch(long matchId);

    /**
     * Get a match by ID, throwing if not found.
     *
     * @param matchId the match ID
     * @return the match
     * @throws com.lightningfirefly.engine.core.exception.EntityNotFoundException if not found
     */
    Match getMatchOrThrow(long matchId);

    /**
     * Get all matches.
     *
     * @return list of all matches
     */
    List<Match> getAllMatches();

    /**
     * Check if a match exists.
     *
     * @param matchId the match ID
     * @return true if the match exists
     */
    boolean matchExists(long matchId);
}
