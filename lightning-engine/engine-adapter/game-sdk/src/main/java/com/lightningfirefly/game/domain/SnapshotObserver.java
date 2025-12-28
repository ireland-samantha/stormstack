package com.lightningfirefly.game.domain;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Observer that updates domain objects when ECS snapshots are received.
 *
 * <p>This class bridges the ECS world (snapshots) with domain-driven design (domain objects).
 * When a components is received, it:
 * <ol>
 *   <li>Iterates through all registered domain objects</li>
 *   <li>For each domain object, finds its entity in the components</li>
 *   <li>Updates all {@link EcsComponent} fields with the components values</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>{@code
 * SnapshotObserver observer = new SnapshotObserver();
 *
 * // When components is received (e.g., from WebSocket):
 * observer.onSnapshot(snapshotData);
 * }</pre>
 *
 * <p>The observer uses {@link DomainObjectRegistry} to find all registered domain objects.
 */
public class SnapshotObserver implements Consumer<Map<String, Map<String, List<Float>>>> {

    private static final String ENTITY_ID_COMPONENT = "ENTITY_ID";

    private final DomainObjectRegistry registry;

    /**
     * Create a new components observer using the default registry.
     */
    public SnapshotObserver() {
        this(DomainObjectRegistry.getInstance());
    }

    /**
     * Create a new components observer with a custom registry.
     *
     * @param registry the domain object registry
     */
    public SnapshotObserver(DomainObjectRegistry registry) {
        this.registry = registry;
    }

    /**
     * Process a components and update all registered domain objects.
     *
     * @param snapshotData the components data (moduleName -> componentName -> values)
     */
    @Override
    public void accept(Map<String, Map<String, List<Float>>> snapshotData) {
        onSnapshot(snapshotData);
    }

    /**
     * Process a components and update all registered domain objects.
     *
     * @param snapshotData the components data (moduleName -> componentName -> values)
     */
    public void onSnapshot(Map<String, Map<String, List<Float>>> snapshotData) {
        if (snapshotData == null || snapshotData.isEmpty()) {
            return;
        }

        for (DomainObject domainObject : registry.getAll()) {
            if (domainObject.isDisposed()) {
                continue;
            }
            updateDomainObject(domainObject, snapshotData);
        }
    }

    /**
     * Update a single domain object from the components.
     *
     * @param domainObject the domain object to update
     * @param snapshotData the components data
     */
    private void updateDomainObject(DomainObject domainObject, Map<String, Map<String, List<Float>>> snapshotData) {
        long entityId = domainObject.getEntityId();
        List<DomainObject.WatchedField> watchedFields = domainObject.getWatchedFields();

        if (watchedFields.isEmpty()) {
            return;
        }

        boolean anyUpdated = false;

        for (DomainObject.WatchedField watchedField : watchedFields) {
            String moduleName = watchedField.moduleName();
            String componentName = watchedField.componentName();

            Map<String, List<Float>> moduleData = snapshotData.get(moduleName);
            if (moduleData == null) {
                continue;
            }

            // Find the entity index in this module
            int entityIndex = findEntityIndex(moduleData, entityId);
            if (entityIndex < 0) {
                continue;
            }

            // Get the component value at this index
            List<Float> componentValues = moduleData.get(componentName);
            if (componentValues == null || entityIndex >= componentValues.size()) {
                continue;
            }

            Float value = componentValues.get(entityIndex);
            if (value != null) {
                domainObject.updateField(watchedField, value);
                anyUpdated = true;
            }
        }

        if (anyUpdated) {
            domainObject.onSnapshotUpdated();
        }
    }

    /**
     * Find the index of an entity in a module's data.
     *
     * @param moduleData the module's component data
     * @param entityId the entity ID to find
     * @return the index, or -1 if not found
     */
    private int findEntityIndex(Map<String, List<Float>> moduleData, long entityId) {
        List<Float> entityIds = moduleData.get(ENTITY_ID_COMPONENT);
        if (entityIds == null) {
            return -1;
        }

        float targetId = (float) entityId;
        for (int i = 0; i < entityIds.size(); i++) {
            Float id = entityIds.get(i);
            if (id != null && id == targetId) {
                return i;
            }
        }

        return -1;
    }
}
