package com.lightningfirefly.engine.core.entity;

import com.lightningfirefly.engine.core.store.BaseComponent;
import com.lightningfirefly.engine.util.IdGeneratorV2;

/**
 * Core components required by the engine infrastructure.
 *
 * <p>These components are fundamental to engine operation and are
 * automatically managed by the engine. Modules should not modify
 * these components directly.
 */
public final class CoreComponents {

    private CoreComponents() {
        // Utility class
    }

    /**
     * Component that stores the match ID an entity belongs to.
     *
     * <p>Every entity created through {@link EntityFactory} automatically
     * has this component attached. This enables:
     * <ul>
     *   <li>Querying entities by match</li>
     *   <li>Match isolation and cleanup</li>
     *   <li>Preventing cross-match entity leakage</li>
     * </ul>
     */
    public static final BaseComponent MATCH_ID = new CoreComponent(
            IdGeneratorV2.newId(), "MATCH_ID");

    public static final BaseComponent ENTITY_ID = new CoreComponent(
            IdGeneratorV2.newId(), "ENTITY_ID");

    /**
     * Internal component class for core components.
     */
    private static class CoreComponent extends BaseComponent {
        CoreComponent(long id, String name) {
            super(id, name);
        }
    }
}
