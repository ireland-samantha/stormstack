package com.lightningfirefly.game.engine.orchestrator;

import com.lightningfirefly.game.engine.GameFactory;

// orchestrator between the rendering engine and the backend.
public interface GameOrchestrator {

    void installGame(GameFactory factory);


    void startGame(GameFactory factory);


    void stopGame(GameFactory factory);

    void registerWatch(WatchedPropertyUpdate watch);
}
