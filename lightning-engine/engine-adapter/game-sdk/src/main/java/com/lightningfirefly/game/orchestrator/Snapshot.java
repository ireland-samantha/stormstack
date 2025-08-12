package com.lightningfirefly.game.orchestrator;

import java.util.List;
import java.util.Map;

/**
 * Represents a snapshot of entity component data.
 *
 * <p>Structure: moduleName -> componentName -> [values...]
 *
 * @param tick       the simulation tick when this snapshot was taken
 * @param components map of module name to component data
 */
public record Snapshot(long tick, Map<String, Map<String, List<Float>>> components) {

    /**
     * Create a snapshot without tick information (defaults to 0).
     *
     * @param components the component data
     */
    public Snapshot(Map<String, Map<String, List<Float>>> components) {
        this(0, components);
    }
}
