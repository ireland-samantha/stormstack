package com.lightningfirefly.game.orchestrator;

import java.util.function.Consumer;

/**
 * Callback for watching property updates from ECS snapshots.
 *
 * <p>This interface is used by the GameOrchestrator to notify when
 * a specific ECS component value changes.
 *
 * @param ecsPath the path to watch (format: "moduleName.componentName")
 * @param entityId the entity ID to watch
 * @param callback the callback to invoke with the new value
 */
public record WatchedDomainPropertyUpdate(
    String ecsPath,
    long entityId,
    Consumer<Float> callback
) {
}
