package com.lightningfirefly.engine.internal.core.snapshot;

import com.lightningfirefly.engine.core.entity.CoreComponents;
import com.lightningfirefly.engine.core.snapshot.Snapshot;
import com.lightningfirefly.engine.core.snapshot.SnapshotFilter;
import com.lightningfirefly.engine.core.store.BaseComponent;
import com.lightningfirefly.engine.core.store.EntityComponentStore;
import com.lightningfirefly.engine.ext.module.EngineModule;
import com.lightningfirefly.engine.ext.module.ModuleResolver;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Implementation of {@link SnapshotProvider} that creates snapshots from the ECS store.
 *
 * <p>Snapshots contain component values for all entities that have the requested
 * snapshot components from the loaded modules.
 */
@Slf4j
public class SnapshotProviderImpl implements SnapshotProvider {

    private final EntityComponentStore entityComponentStore;
    private final ModuleResolver moduleResolver;

    // Cache for requested snapshot components per module
    private volatile List<ModuleComponentMapping> cachedMappings;

    public SnapshotProviderImpl(EntityComponentStore entityComponentStore, ModuleResolver moduleResolver) {
        this.entityComponentStore = entityComponentStore;
        this.moduleResolver = moduleResolver;
    }

    @Override
    public Snapshot createForMatch(long matchId) {
        List<ModuleComponentMapping> mappings = getOrBuildMappings();
        if (mappings.isEmpty()) {
            log.debug("No snapshot components registered, returning empty snapshot");
            return new Snapshot(Map.of());
        }

        // Collect all components to find entities
        List<BaseComponent> allComponents = mappings.stream()
                .flatMap(m -> m.components().stream())
                .toList();

        if (allComponents.isEmpty()) {
            return new Snapshot(Map.of());
        }

        // Get all entities that have the requested *flag* components
        Set<Long> entities = new HashSet<>();
        for (BaseComponent flag : getFlagComponents()) {
            Set<Long> found = entityComponentStore.getEntitiesWithComponents(List.of(flag, CoreComponents.MATCH_ID));
            if (found != null && !found.isEmpty()) {
                entities.addAll(entityComponentStore.getEntitiesWithComponents(flag, CoreComponents.MATCH_ID));
            }
        };

        if (entities.isEmpty()) {
            log.debug("No entities found with requested components");
            return new Snapshot(Map.of());
        }

        // Build snapshot structure: moduleName -> componentName -> [values...]
        Map<String, Map<String, List<Float>>> snapshotData = new LinkedHashMap<>();
        float matchIdFloat = (float) matchId;

        for (ModuleComponentMapping mapping : mappings) {
            Map<String, List<Float>> moduleData = new LinkedHashMap<>();

            // Collect ENTITY_IDs for entities in this match (done once per module, not per component)
            List<Float> entityIds = new ArrayList<>();
            for (Long entityId : entities) {
                if (entityComponentStore.hasComponent(entityId, CoreComponents.MATCH_ID) &&
                        entityComponentStore.getComponent(entityId, CoreComponents.MATCH_ID) == matchIdFloat &&
                        entityComponentStore.hasComponent(entityId, CoreComponents.ENTITY_ID)) {
                    entityIds.add(entityComponentStore.getComponent(entityId, CoreComponents.ENTITY_ID));
                }
            }
            if (!entityIds.isEmpty()) {
                moduleData.put(CoreComponents.ENTITY_ID.getName(), entityIds);
            }

            for (BaseComponent component : mapping.components()) {
                List<Float> values = new ArrayList<>();

                for (Long entityId : entities) {
                    if (entityComponentStore.hasComponent(entityId, component) &&
                            entityComponentStore.hasComponent(entityId, CoreComponents.MATCH_ID) &&
                            entityComponentStore.getComponent(entityId, CoreComponents.MATCH_ID) == matchIdFloat) {
                        float value = entityComponentStore.getComponent(entityId, component);
                        values.add(value);
                    }
                }

                if (!values.isEmpty()) {
                    moduleData.put(component.getName(), values);
                }
            }

            if (!moduleData.isEmpty()) {
                snapshotData.put(mapping.moduleName(), moduleData);
            }
        }

        log.debug("Created snapshot with {} modules, {} entities",
                snapshotData.size(), entities.size());

        return new Snapshot(snapshotData);
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
        List<BaseComponent> mappings = new ArrayList<>();

        for (int i = 0; i < modules.size(); i++) {
            EngineModule module = modules.get(i);
            BaseComponent component = module.createFlagComponent();
            if (component != null ) {
                mappings.add(component);
            }
        }
        return mappings;
    }

    private List<ModuleComponentMapping> buildMappings() {
        List<EngineModule> modules = moduleResolver.resolveAllModules();
        List<ModuleComponentMapping> mappings = new ArrayList<>();

        for (int i = 0; i < modules.size(); i++) {
            EngineModule module = modules.get(i);
            List<BaseComponent> components = module.createComponents();
            if (components != null && !components.isEmpty()) {
                mappings.add(new ModuleComponentMapping(module.getName(), components));
            }
        }

        log.debug("Built snapshot mappings for {} modules", mappings.size());
        return mappings;
    }

    private record ModuleComponentMapping(String moduleName, List<BaseComponent> components) {
    }
}
