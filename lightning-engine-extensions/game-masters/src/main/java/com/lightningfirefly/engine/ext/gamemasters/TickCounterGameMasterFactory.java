package com.lightningfirefly.engine.ext.gamemasters;

import com.lightningfirefly.game.gm.GameMaster;
import com.lightningfirefly.game.gm.GameMasterContext;
import com.lightningfirefly.game.engine.orchestrator.gm.GameMasterFactory;

/**
 * Factory for creating TickCounterGameMaster instances.
 *
 * <p>This is a sample game master that demonstrates the GameMaster interface
 * by simply counting ticks and logging the count periodically.
 */
public class TickCounterGameMasterFactory implements GameMasterFactory {

    @Override
    public GameMaster create(GameMasterContext context) {
        return new TickCounterGameMaster(context);
    }

    @Override
    public String getName() {
        return "TickCounter";
    }
}
