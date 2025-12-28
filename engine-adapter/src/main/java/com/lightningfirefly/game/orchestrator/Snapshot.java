package com.lightningfirefly.game.orchestrator;

import java.util.List;
import java.util.Map;

/**
 * Represents a snapshot of entity component data.
 *
 * <p>Structure: moduleName -> componentName -> [values...]
 *
 * @param components map of module name to component data
 */
public record Snapshot(Map<String, Map<String, List<Float>>> components) {
}
