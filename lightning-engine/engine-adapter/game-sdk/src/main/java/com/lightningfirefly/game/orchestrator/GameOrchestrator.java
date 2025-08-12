package com.lightningfirefly.game.orchestrator;

import com.lightningfirefly.game.backend.installation.GameFactory;

/**
 * Orchestrator between the rendering engine and the backend.
 * <p>Implements AutoCloseable to ensure proper resource cleanup.
 * Use try-with-resources when possible.
 */
public interface GameOrchestrator extends AutoCloseable {

    /**
     * Install game resources (JARs, textures) on the server.
     * @param factory the game factory to install
     */
    void installGame(GameFactory factory);

    void uninstallGame(GameFactory factory);

    /**
     * Start the game by creating a match and subscribing to snapshots.
     * @param factory the game factory to start
     */
    void startGame(GameFactory factory);

    /**
     * Stop the game and clean up resources.
     * @param factory the game factory to stop
     */
    void stopGame(GameFactory factory);

    /**
     * Register a watch for property updates.
     * @param watch the watch to register
     */
    void registerDomainPropertyUpdate(DomainPropertyUpdate watch);

    void unregisterDomainPropertyUpdate(DomainPropertyUpdate update);

    GameSession getSession(GameFactory factory);

    boolean isGameRunning(GameFactory factory);

    /**
     * Shutdown the orchestrator and release all resources.
     */
    void shutdown();


    /**
     * Alias for shutdown() to implement AutoCloseable.
     */
    @Override
    default void close() {
        shutdown();
    }
}
