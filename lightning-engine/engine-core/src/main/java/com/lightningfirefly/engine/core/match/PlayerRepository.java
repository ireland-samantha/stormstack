package com.lightningfirefly.engine.core.match;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Player persistence operations.
 *
 * <p>Follows the Repository pattern - provides pure CRUD operations
 * without business logic. Business rules belong in {@link PlayerService}.
 *
 * <p>SOLID compliance:
 * <ul>
 *   <li>SRP: Only handles data persistence, no business logic</li>
 *   <li>ISP: Separate from PlayerService with distinct responsibilities</li>
 * </ul>
 */
public interface PlayerRepository {

    /**
     * Save a player to the repository.
     *
     * @param player the player to save (must not be null)
     * @return the saved player
     */
    Player save(Player player);

    /**
     * Delete a player by its ID.
     *
     * @param id the player ID
     */
    void deleteById(long id);

    /**
     * Find a player by its ID.
     *
     * @param id the player ID
     * @return an Optional containing the player if found, empty otherwise
     */
    Optional<Player> findById(long id);

    /**
     * Find all players.
     *
     * @return a list of all players (never null)
     */
    List<Player> findAll();

    /**
     * Check if a player exists by ID.
     *
     * @param id the player ID
     * @return true if the player exists
     */
    boolean existsById(long id);

    /**
     * Count the number of players.
     *
     * @return the total count
     */
    long count();
}
