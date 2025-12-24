package com.lightningfirefly.game.orchestrator;

import com.lightningfirefly.game.backend.installation.GameFactory;

// orchestrator between the rendering engine and the backend.
public interface GameOrchestrator {

    void installGame(GameFactory factory);


    void startGame(GameFactory factory);


    void stopGame(GameFactory factory);

    void registerWatch(WatchedDomainPropertyUpdate watch);
}
