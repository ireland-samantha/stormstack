package com.lightningfirefly.engine.core.match;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Match persistence operations.
 *
 * <p>Follows the Repository pattern - provides pure CRUD operations
 * without business logic. Business rules belong in {@link MatchService}.
 *
 * <p>SOLID compliance:
 * <ul>
 *   <li>SRP: Only handles data persistence, no business logic</li>
 *   <li>ISP: Separate from MatchService with distinct responsibilities</li>
 * </ul>
 */
public interface MatchRepository {

    /**
     * Save a match to the repository.
     *
     * @param match the match to save (must not be null)
     * @return the saved match
     */
    Match save(Match match);

    /**
     * Delete a match by its ID.
     *
     * @param id the match ID
     */
    void deleteById(long id);

    /**
     * Find a match by its ID.
     *
     * @param id the match ID
     * @return an Optional containing the match if found, empty otherwise
     */
    Optional<Match> findById(long id);

    /**
     * Find all matches.
     *
     * @return a list of all matches (never null)
     */
    List<Match> findAll();

    /**
     * Check if a match exists by ID.
     *
     * @param id the match ID
     * @return true if the match exists
     */
    boolean existsById(long id);

    /**
     * Count the number of matches.
     *
     * @return the total count
     */
    long count();
}
