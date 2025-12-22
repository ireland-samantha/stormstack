package com.lightningfirefly.game.engine.orchestrator;

import com.lightningfirefly.game.engine.GameModule;

// orchestrator between the rendering engine and the backend.
public interface GameOrchestrator {

    void installGame(GameModule module);


    void startGame(GameModule module);


    void stopGame(GameModule module);

    void registerWatch(WatchedPropertyUpdate watch);
}
