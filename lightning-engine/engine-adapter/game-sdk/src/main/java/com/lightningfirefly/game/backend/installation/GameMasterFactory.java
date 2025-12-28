package com.lightningfirefly.game.backend.installation;

import com.lightningfirefly.game.domain.GameMaster;
import com.lightningfirefly.game.domain.GameMasterContext;

/**
 * Factory interface for creating game masters.
 *
 * <p>Each game master implementation should provide a factory that implements this interface.
 * The factory is responsible for creating configured instances of the game master.
 *
 * <p>Game masters are called every game tick via {@link GameMaster#onTick()} and can
 * be used to implement game logic that runs continuously.
 *
 * <p>Example implementation:
 * <pre>{@code
 * public class MyGameMasterFactory implements GameMasterFactory {
 *     @Override
 *     public GameMaster create(GameMasterContext context) {
 *         return new MyGameMaster(context);
 *     }
 *
 *     @Override
 *     public String getName() {
 *         return "MyGameMaster";
 *     }
 * }
 * }</pre>
 *
 * @see GameMaster
 * @see GameMasterContext
 */
public interface GameMasterFactory {

    /**
     * Create an instance of the game master.
     *
     * @param context the context for dependency injection
     * @return the created game master instance
     */
    GameMaster create(GameMasterContext context);

    /**
     * Get the unique name for this game master.
     *
     * <p>This name is used for identification in the UI and REST API.
     *
     * @return the game master name
     */
    String getName();
}
