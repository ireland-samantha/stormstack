package com.lightningfirefly.engine.core.snapshot;

import java.util.List;
import java.util.Map;

/**
 * Represents a snapshot of entity component data.
 *
 * <p>Structure: moduleName -> componentName -> [values...]
 *
 * <pre>{@code
 * {
 *     "MovementModule": {
 *         "POSITION_X": [100.0, 200.0, 300.0],
 *         "POSITION_Y": [50.0, 60.0, 70.0],
 *         "VELOCITY_X": [1.5, 2.5, 3.5],
 *         "VELOCITY_Y": [0.0, 0.0, 0.0]
 *     },
 *     "SpawnModule": {
 *         "ENTITY_TYPE": [1.0, 1.0, 2.0],
 *         "OWNER_ID": [100.0, 100.0, 200.0]
 *     }
 * }
 * }</pre>
 *
 * @param snapshot map of module name to component data
 */
public record Snapshot(Map<String, Map<String, List<Float>>> snapshot) {
}
