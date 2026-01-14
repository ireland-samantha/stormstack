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


package ca.samanthaireland.engine.internal.core.snapshot;

import ca.samanthaireland.engine.core.entity.CoreComponents;
import ca.samanthaireland.engine.core.snapshot.Snapshot;
import ca.samanthaireland.engine.core.store.BaseComponent;
import ca.samanthaireland.engine.core.store.EntityComponentStore;
import ca.samanthaireland.engine.ext.module.EngineModule;
import ca.samanthaireland.engine.ext.module.ModuleResolver;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Predicate;

/**
 * Implementation of {@link SnapshotProvider} that creates snapshots from the ECS store.
 *
 * <p>Snapshots contain component values for all entities that match the filter criteria.
 * Supports filtering by:
 * <ul>
 *   <li>Match ID - only entities belonging to the specified match</li>
 *   <li>Player ID (optional) - only entities owned by the specified player</li>
 * </ul>
 *
 * <p>Uses a columnar data format for efficient serialization:
 * <pre>
 * {
 *   "ModuleName": {
 *     "COMPONENT_A": [value1, value2, ...],
 *     "COMPONENT_B": [value1, value2, ...]
 *   }
 * }
 * </pre>
 */
@Slf4j
public class SnapshotProviderImpl implements SnapshotProvider {

    private final EntityComponentStore entityStore;
    private final ModuleResolver moduleResolver;

    private volatile List<ModuleComponentMapping> cachedMappings;

    public SnapshotProviderImpl(EntityComponentStore entityStore, ModuleResolver moduleResolver) {
        this.entityStore = Objects.requireNonNull(entityStore, "entityStore must not be null");
        this.moduleResolver = Objects.requireNonNull(moduleResolver, "moduleResolver must not be null");
    }

    @Override
    public Snapshot createForMatch(long matchId) {
        return createSnapshot(
                new SnapshotFilter(matchId, Optional.empty())
        );
    }

    @Override
    public Snapshot createForMatchAndPlayer(long matchId, long playerId) {
        return createSnapshot(
                new SnapshotFilter(matchId, Optional.of(playerId))
        );
    }

    /**
     * Creates a snapshot using the specified filter criteria.
     */
    private Snapshot createSnapshot(SnapshotFilter filter) {
        List<ModuleComponentMapping> mappings = getOrBuildMappings();
        if (mappings.isEmpty()) {
            log.debug("No module components registered, returning empty snapshot");
            return Snapshot.empty();
        }

        Set<Long> candidateEntities = findCandidateEntities();
        if (candidateEntities.isEmpty()) {
            log.debug("No entities found with registered components");
            return Snapshot.empty();
        }

        Predicate<Long> entityFilter = buildEntityFilter(filter);
        Set<Long> matchingEntities = filterEntities(candidateEntities, entityFilter);

        if (matchingEntities.isEmpty()) {
            log.debug("No entities match filter criteria: {}", filter);
            return Snapshot.empty();
        }

        Map<String, Map<String, List<Float>>> snapshotData = buildSnapshotData(
                mappings, matchingEntities, filter.matchId()
        );

        log.debug("Created snapshot with {} modules, {} entities for filter: {}",
                snapshotData.size(), matchingEntities.size(), filter);

        return new Snapshot(snapshotData);
    }

    /**
     * Finds all entities that have any of the registered flag components.
     */
    private Set<Long> findCandidateEntities() {
        Set<Long> entities = new HashSet<>();

        for (BaseComponent flagComponent : getFlagComponents()) {
            Set<Long> found = entityStore.getEntitiesWithComponents(
                    List.of(flagComponent, CoreComponents.MATCH_ID)
            );
            if (found != null && !found.isEmpty()) {
                entities.addAll(found);
            }
        }

        return entities;
    }

    /**
     * Builds a predicate that filters entities based on the snapshot filter criteria.
     */
    private Predicate<Long> buildEntityFilter(SnapshotFilter filter) {
        float matchIdFloat = (float) filter.matchId();

        Predicate<Long> matchFilter = entityId ->
                entityStore.hasComponent(entityId, CoreComponents.MATCH_ID) &&
                entityStore.getComponent(entityId, CoreComponents.MATCH_ID) == matchIdFloat;

        if (filter.playerId().isEmpty()) {
            return matchFilter;
        }

        float playerIdFloat = filter.playerId().get().floatValue();

        return matchFilter.and(entityId -> {
            if (!entityStore.hasComponent(entityId, CoreComponents.OWNER_ID)) {
                return false;
            }
            float ownerId = entityStore.getComponent(entityId, CoreComponents.OWNER_ID);
            return !Float.isNaN(ownerId) && ownerId == playerIdFloat;
        });
    }

    /**
     * Filters entities using the provided predicate.
     */
    private Set<Long> filterEntities(Set<Long> entities, Predicate<Long> filter) {
        Set<Long> result = new HashSet<>();
        for (Long entityId : entities) {
            if (filter.test(entityId)) {
                result.add(entityId);
            }
        }
        return result;
    }

    /**
     * Builds the columnar snapshot data structure for matching entities.
     */
    private Map<String, Map<String, List<Float>>> buildSnapshotData(
            List<ModuleComponentMapping> mappings,
            Set<Long> entities,
            long matchId) {

        Map<String, Map<String, List<Float>>> snapshotData = new LinkedHashMap<>();
        float matchIdFloat = (float) matchId;

        for (ModuleComponentMapping mapping : mappings) {
            Map<String, List<Float>> moduleData = buildModuleData(mapping, entities, matchIdFloat);

            if (!moduleData.isEmpty()) {
                snapshotData.put(mapping.moduleName(), moduleData);
            }
        }

        return snapshotData;
    }

    /**
     * Builds the component data for a single module.
     */
    private Map<String, List<Float>> buildModuleData(
            ModuleComponentMapping mapping,
            Set<Long> entities,
            float matchIdFloat) {

        Map<String, List<Float>> moduleData = new LinkedHashMap<>();

        // Collect ENTITY_IDs first (ensures consistent entity ordering)
        List<Long> orderedEntityIds = collectOrderedEntityIds(entities, matchIdFloat);

        if (orderedEntityIds.isEmpty()) {
            return moduleData;
        }

        // Add ENTITY_ID column
        List<Float> entityIdValues = new ArrayList<>(orderedEntityIds.size());
        for (Long entityId : orderedEntityIds) {
            entityIdValues.add(entityStore.getComponent(entityId, CoreComponents.ENTITY_ID));
        }
        moduleData.put(CoreComponents.ENTITY_ID.getName(), entityIdValues);

        // Add component columns
        for (BaseComponent component : mapping.components()) {
            List<Float> values = collectComponentValues(component, orderedEntityIds, matchIdFloat);
            if (!values.isEmpty()) {
                moduleData.put(component.getName(), values);
            }
        }

        return moduleData;
    }

    /**
     * Collects entity IDs in a consistent order for columnar alignment.
     */
    private List<Long> collectOrderedEntityIds(Set<Long> entities, float matchIdFloat) {
        List<Long> ordered = new ArrayList<>();

        for (Long entityId : entities) {
            if (entityStore.hasComponent(entityId, CoreComponents.MATCH_ID) &&
                entityStore.getComponent(entityId, CoreComponents.MATCH_ID) == matchIdFloat &&
                entityStore.hasComponent(entityId, CoreComponents.ENTITY_ID)) {
                ordered.add(entityId);
            }
        }

        return ordered;
    }

    /**
     * Collects component values for all matching entities.
     */
    private List<Float> collectComponentValues(
            BaseComponent component,
            List<Long> entityIds,
            float matchIdFloat) {

        List<Float> values = new ArrayList<>(entityIds.size());

        for (Long entityId : entityIds) {
            if (entityStore.hasComponent(entityId, component) &&
                entityStore.hasComponent(entityId, CoreComponents.MATCH_ID) &&
                entityStore.getComponent(entityId, CoreComponents.MATCH_ID) == matchIdFloat) {
                values.add(entityStore.getComponent(entityId, component));
            }
        }

        return values;
    }

    private List<ModuleComponentMapping> getOrBuildMappings() {
        List<ModuleComponentMapping> mappings = cachedMappings;
        if (mappings == null) {
            mappings = buildMappings();
            cachedMappings = mappings;
        }
        return mappings;
    }

    private List<BaseComponent> getFlagComponents() {
        List<EngineModule> modules = moduleResolver.resolveAllModules();
        List<BaseComponent> flags = new ArrayList<>();

        for (EngineModule module : modules) {
            BaseComponent flag = module.createFlagComponent();
            if (flag != null) {
                flags.add(flag);
            }
        }

        return flags;
    }

    private List<ModuleComponentMapping> buildMappings() {
        List<EngineModule> modules = moduleResolver.resolveAllModules();
        List<ModuleComponentMapping> mappings = new ArrayList<>();

        for (EngineModule module : modules) {
            List<BaseComponent> components = module.createComponents();
            if (components != null && !components.isEmpty()) {
                mappings.add(new ModuleComponentMapping(module.getName(), components));
            }
        }

        log.debug("Built component mappings for {} modules", mappings.size());
        return mappings;
    }

    /**
     * Invalidates the cached component mappings.
     * Call this when modules are added or removed.
     */
    public void invalidateCache() {
        cachedMappings = null;
        log.debug("Component mapping cache invalidated");
    }

    /**
     * Filter criteria for snapshot creation.
     */
    private record SnapshotFilter(long matchId, Optional<Long> playerId) {
        @Override
        public String toString() {
            return playerId
                    .map(pid -> "match=" + matchId + ", player=" + pid)
                    .orElse("match=" + matchId);
        }
    }

    /**
     * Mapping of module name to its snapshot components.
     */
    private record ModuleComponentMapping(String moduleName, List<BaseComponent> components) {
    }
}
