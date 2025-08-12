package com.lightningfirefly.engine.core.match;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for Player business operations.
 *
 * <p>Provides business-level operations with validation, authorization,
 * and domain logic. Uses {@link PlayerRepository} for persistence.
 *
 * <p>SOLID compliance:
 * <ul>
 *   <li>SRP: Handles business logic only, delegates persistence to repository</li>
 *   <li>ISP: Separate from PlayerRepository with distinct responsibilities</li>
 *   <li>DIP: Depends on PlayerRepository abstraction</li>
 * </ul>
 */
public interface PlayerService {

    /**
     * Create a new player with validation.
     *
     * @param player the player to create (must not be null)
     * @return the created player
     * @throws IllegalArgumentException if player data is invalid
     */
    Player createPlayer(Player player);

    /**
     * Delete a player by ID.
     *
     * @param playerId the player ID
     * @throws com.lightningfirefly.engine.core.exception.EntityNotFoundException if player doesn't exist
     */
    void deletePlayer(long playerId);

    /**
     * Get a player by ID.
     *
     * @param playerId the player ID
     * @return an Optional containing the player if found
     */
    Optional<Player> getPlayer(long playerId);

    /**
     * Get a player by ID, throwing if not found.
     *
     * @param playerId the player ID
     * @return the player
     * @throws com.lightningfirefly.engine.core.exception.EntityNotFoundException if not found
     */
    Player getPlayerOrThrow(long playerId);

    /**
     * Get all players.
     *
     * @return list of all players
     */
    List<Player> getAllPlayers();

    /**
     * Check if a player exists.
     *
     * @param playerId the player ID
     * @return true if the player exists
     */
    boolean playerExists(long playerId);
}
