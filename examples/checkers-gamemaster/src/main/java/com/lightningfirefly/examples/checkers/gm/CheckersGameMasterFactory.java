package com.lightningfirefly.examples.checkers.gm;

import com.lightningfirefly.examples.checkers.gm.domain.CheckersGameMaster;
import com.lightningfirefly.game.engine.orchestrator.gm.GameMasterFactory;
import com.lightningfirefly.game.gm.GameMaster;
import com.lightningfirefly.game.gm.GameMasterContext;

/**
 * Factory for creating CheckersGameMaster instances.
 *
 * <p>This factory is loaded via the JAR manifest attribute {@code Game-Master-Factory-Class}
 * when the GameMaster JAR is uploaded to the server.
 */
public class CheckersGameMasterFactory implements GameMasterFactory {

    @Override
    public GameMaster create(GameMasterContext context) {
        return new CheckersGameMaster(context);
    }

    @Override
    public String getName() {
        return "CheckersGameMaster";
    }
}
