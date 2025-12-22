package com.lightningfirefly.game.gm;

import com.lightningfirefly.game.engine.orchestrator.gm.GameMasterCommand;
import com.lightningfirefly.game.engine.orchestrator.gm.GameMasterFactory;

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

    void executeCommand(GameMasterCommand gameMasterCommand);
}
