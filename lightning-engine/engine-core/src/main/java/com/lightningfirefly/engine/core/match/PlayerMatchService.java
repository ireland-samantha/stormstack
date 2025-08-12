package com.lightningfirefly.engine.core.match;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for PlayerMatch business operations.
 *
 * <p>Provides business-level operations with validation, authorization,
 * and domain logic. Uses {@link PlayerMatchRepository} for persistence.
 *
 * <p>SOLID compliance:
 * <ul>
 *   <li>SRP: Handles business logic only, delegates persistence to repository</li>
 *   <li>ISP: Separate from PlayerMatchRepository with distinct responsibilities</li>
 *   <li>DIP: Depends on PlayerMatchRepository abstraction</li>
 * </ul>
 */
public interface PlayerMatchService {

    /**
     * Join a player to a match.
     *
     * <p>Validates that both player and match exist before creating the association.
     *
     * @param playerId the player ID
     * @param matchId the match ID
     * @return the created player-match association
     * @throws com.lightningfirefly.engine.core.exception.EntityNotFoundException if player or match doesn't exist
     * @throws IllegalStateException if player is already in the match
     */
    PlayerMatch joinMatch(long playerId, long matchId);

    /**
     * Remove a player from a match.
     *
     * @param playerId the player ID
     * @param matchId the match ID
     * @throws com.lightningfirefly.engine.core.exception.EntityNotFoundException if association doesn't exist
     */
    void leaveMatch(long playerId, long matchId);

    /**
     * Get a player-match association.
     *
     * @param playerId the player ID
     * @param matchId the match ID
     * @return an Optional containing the player-match if found
     */
    Optional<PlayerMatch> getPlayerMatch(long playerId, long matchId);

    /**
     * Get a player-match association, throwing if not found.
     *
     * @param playerId the player ID
     * @param matchId the match ID
     * @return the player-match association
     * @throws com.lightningfirefly.engine.core.exception.EntityNotFoundException if not found
     */
    PlayerMatch getPlayerMatchOrThrow(long playerId, long matchId);

    /**
     * Get all players in a match.
     *
     * @param matchId the match ID
     * @return list of player-match associations for the match
     */
    List<PlayerMatch> getPlayersInMatch(long matchId);

    /**
     * Get all matches for a player.
     *
     * @param playerId the player ID
     * @return list of player-match associations for the player
     */
    List<PlayerMatch> getMatchesForPlayer(long playerId);

    /**
     * Check if a player is in a match.
     *
     * @param playerId the player ID
     * @param matchId the match ID
     * @return true if the player is in the match
     */
    boolean isPlayerInMatch(long playerId, long matchId);
}
