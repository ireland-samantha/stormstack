/*
 * Copyright (c) 2026 Samantha Ireland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */


package ca.samanthaireland.game.orchestrator;

import ca.samanthaireland.game.backend.installation.GameFactory;

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
