package com.lightningfirefly.engine.core.match;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for PlayerMatch persistence operations.
 *
 * <p>Follows the Repository pattern - provides pure CRUD operations
 * without business logic. Business rules belong in {@link PlayerMatchService}.
 *
 * <p>SOLID compliance:
 * <ul>
 *   <li>SRP: Only handles data persistence, no business logic</li>
 *   <li>ISP: Separate from PlayerMatchService with distinct responsibilities</li>
 * </ul>
 */
public interface PlayerMatchRepository {

    /**
     * Save a player-match association to the repository.
     *
     * @param playerMatch the player-match to save (must not be null)
     * @return the saved player-match
     */
    PlayerMatch save(PlayerMatch playerMatch);

    /**
     * Delete a player-match by player ID and match ID.
     *
     * @param playerId the player ID
     * @param matchId the match ID
     */
    void deleteByPlayerAndMatch(long playerId, long matchId);

    /**
     * Find a player-match association by player ID and match ID.
     *
     * @param playerId the player ID
     * @param matchId the match ID
     * @return an Optional containing the player-match if found, empty otherwise
     */
    Optional<PlayerMatch> findByPlayerAndMatch(long playerId, long matchId);

    /**
     * Find all player-match associations for a given match.
     *
     * @param matchId the match ID
     * @return list of player-match associations for the match (never null)
     */
    List<PlayerMatch> findByMatchId(long matchId);

    /**
     * Find all player-match associations for a given player.
     *
     * @param playerId the player ID
     * @return list of player-match associations for the player (never null)
     */
    List<PlayerMatch> findByPlayerId(long playerId);

    /**
     * Check if a player-match association exists.
     *
     * @param playerId the player ID
     * @param matchId the match ID
     * @return true if the association exists
     */
    boolean existsByPlayerAndMatch(long playerId, long matchId);

    /**
     * Count the number of player-match associations.
     *
     * @return the total count
     */
    long count();
}
