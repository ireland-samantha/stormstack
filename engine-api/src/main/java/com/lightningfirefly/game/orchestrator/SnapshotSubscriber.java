package com.lightningfirefly.game.orchestrator;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Interface for subscribing to components updates from the server.
 */
@FunctionalInterface
public interface SnapshotSubscriber {
    /**
     * Subscribe to components updates for a match.
     *
     * @param matchId  the match ID
     * @param callback called when a components is received
     * @return a runnable that unsubscribes when called
     */
    Runnable subscribe(long matchId, Consumer<Map<String, Map<String, List<Float>>>> callback);
}
