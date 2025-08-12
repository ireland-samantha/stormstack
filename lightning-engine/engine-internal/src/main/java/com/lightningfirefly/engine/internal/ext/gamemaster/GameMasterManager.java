package com.lightningfirefly.engine.internal.ext.gamemaster;

import com.lightningfirefly.game.domain.GameMaster;
import com.lightningfirefly.game.backend.installation.GameMasterFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Manages game masters from external sources (e.g., JAR files).
 *
 * <p>Provides functionality to scan for game masters, install new game masters,
 * and resolve game masters by name.
 */
public interface GameMasterManager {

    /**
     * Scan for available game masters.
     *
     * <p>This method scans the configured location for game master implementations
     * and registers them for later resolution.
     *
     * @throws IOException if the scan fails
     */
    void reloadInstalled() throws IOException;

    /**
     * Install a game master from a file.
     *
     * <p>This method installs the game master from the specified file,
     * making it available for resolution.
     *
     * @param gameMasterFile the path to the game master JAR file to install
     * @throws IOException if the installation fails
     */
    void installGameMaster(Path gameMasterFile) throws IOException;

    /**
     * Install a game master from a factory class.
     *
     * <p>This method instantiates the factory class and registers the game master
     * for later resolution. Useful for programmatic registration
     * without requiring JAR files.
     *
     * @param gameMasterFactory the game master factory class to install
     */
    void installGameMaster(Class<? extends GameMasterFactory> gameMasterFactory);

    /**
     * Clear all caches and reset the scanned state.
     *
     * <p>Useful for hot-reloading game masters.
     */
    void reset();

    /**
     * Get a game master factory by name.
     *
     * @param gameMasterName the name of the game master to get
     * @return the factory, or null if not found
     */
    GameMasterFactory getFactory(String gameMasterName);

    /**
     * Create a game master instance for a specific match.
     *
     * <p>This creates a new instance of the game master bound to the specified match,
     * providing proper isolation between matches.
     *
     * @param gameMasterName the name of the game master
     * @param matchId the match ID for isolation
     * @return the created game master instance, or null if not found
     */
    GameMaster createForMatch(String gameMasterName, long matchId);

    /**
     * Get all available game master names.
     *
     * @return list of available game master names
     */
    List<String> getAvailableGameMasters();

    /**
     * Check if a game master with the given name is available.
     *
     * @param gameMasterName the game master name to check
     * @return true if the game master is available
     */
    boolean hasGameMaster(String gameMasterName);

    /**
     * Uninstall a game master by name.
     *
     * <p>This removes the game master from the cache. Note that this does not
     * delete the JAR file from disk.
     *
     * @param gameMasterName the name of the game master to uninstall
     * @return true if the game master was found and uninstalled
     */
    boolean uninstallGameMaster(String gameMasterName);
}
