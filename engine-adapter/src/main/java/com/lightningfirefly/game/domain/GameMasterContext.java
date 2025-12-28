package com.lightningfirefly.game.domain;

import com.lightningfirefly.game.backend.adapter.GameMasterCommand;
import com.lightningfirefly.game.backend.installation.GameMasterFactory;

/**
 * Context provided to game masters for interaction with the server.
 *
 * <p>Example usage:
 * <pre>{@code
 * public class MyGameMaster implements GameMaster {
 *     private final GameMasterContext context;
 *
 *     public MyGameMaster(GameMasterContext context) {
 *         this.context = context;
 *     }
 *
 *     @Override
 *     public void onTick() {
 *         long matchId = context.getMatchId();
 *         // Access game state and implement tick logic
 *     }
 * }
 * }</pre>
 *
 * @see GameMasterFactory
 * @see GameMaster
 */
public interface GameMasterContext {

    /**
     * Get the current match ID this game master is operating in.
     *
     * <p>Game masters are bound to specific matches for isolation.
     * All entity operations should be scoped to this match ID.
     *
     * @return the match ID
     */
    long getMatchId();

    /**
     * Get the current tick number.
     *
     * @return the current tick number
     */
    long getCurrentTick();

    /**
     * Execute a game master command.
     *
     * @param gameMasterCommand the command to execute
     */
    void executeCommand(GameMasterCommand gameMasterCommand);

    /**
     * Look up a resource ID by its name.
     *
     * <p>Resources are uploaded by the client and stored on the server.
     * This method allows the game master to look up resource IDs for
     * attaching sprites to entities.
     *
     * @param resourceName the name of the resource (e.g., "red-checker")
     * @return the resource ID, or -1 if not found
     */
    default long getResourceIdByName(String resourceName) {
        return -1; // Default implementation returns not found
    }
}
