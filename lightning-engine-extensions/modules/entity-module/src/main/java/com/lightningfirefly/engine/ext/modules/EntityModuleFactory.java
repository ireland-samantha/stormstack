package com.lightningfirefly.engine.ext.modules;

import com.lightningfirefly.engine.core.command.CommandBuilder;
import com.lightningfirefly.engine.core.command.CommandPayload;
import com.lightningfirefly.engine.core.command.EngineCommand;
import com.lightningfirefly.engine.core.match.Match;
import com.lightningfirefly.engine.core.match.MatchService;
import com.lightningfirefly.engine.core.store.BaseComponent;
import com.lightningfirefly.engine.core.store.EntityComponentStore;
import com.lightningfirefly.engine.core.system.EngineSystem;
import com.lightningfirefly.engine.ext.module.EngineModule;
import com.lightningfirefly.engine.ext.module.ModuleContext;
import com.lightningfirefly.engine.ext.module.ModuleFactory;
import com.lightningfirefly.engine.ext.module.ModuleResolver;
import com.lightningfirefly.engine.util.IdGeneratorV2;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Module factory for the Entity module.
 *
 * <p>Provides core entity functionality:
 * <ul>
 *   <li>Entity creation (spawn command)</li>
 *   <li>Position components (shared by rendering and physics)</li>
 *   <li>Entity metadata (type, owner, player)</li>
 * </ul>
 *
 * <p>Position components in this module are the authoritative positions used by:
 * <ul>
 *   <li>RigidBodyModule - for physics calculations</li>
 *   <li>RenderModule - for sprite rendering</li>
 *   <li>BoxColliderModule - for collision detection</li>
 * </ul>
 */
@Slf4j
public class EntityModuleFactory implements ModuleFactory {

    // Entity metadata components
    public static final BaseComponent ENTITY_TYPE = new EntityComponent(
            IdGeneratorV2.newId(), "ENTITY_TYPE");
    public static final BaseComponent OWNER_ID = new EntityComponent(
            IdGeneratorV2.newId(), "OWNER_ID");
    public static final BaseComponent PLAYER_ID = new EntityComponent(
            IdGeneratorV2.newId(), "PLAYER_ID");
    public static final BaseComponent FLAG = new EntityComponent(
            IdGeneratorV2.newId(), "entity");

    // Position components - shared by rendering and physics
    public static final BaseComponent POSITION_X = new EntityComponent(
            IdGeneratorV2.newId(), "POSITION_X");
    public static final BaseComponent POSITION_Y = new EntityComponent(
            IdGeneratorV2.newId(), "POSITION_Y");
    public static final BaseComponent POSITION_Z = new EntityComponent(
            IdGeneratorV2.newId(), "POSITION_Z");

    /**
     * Core entity components (type, owner, player).
     * MATCH_ID and ENTITY_ID are attached automatically by EntityFactory.
     */
    public static final List<BaseComponent> CORE_COMPONENTS =
            List.of(ENTITY_TYPE, OWNER_ID, PLAYER_ID);

    /**
     * Position components (shared by rendering and physics).
     */
    public static final List<BaseComponent> POSITION_COMPONENTS =
            List.of(POSITION_X, POSITION_Y, POSITION_Z);

    /**
     * Components included in snapshots.
     */
    public static final List<BaseComponent> ALL_COMPONENTS =
            List.of(ENTITY_TYPE, OWNER_ID, PLAYER_ID, POSITION_X, POSITION_Y, POSITION_Z);

    @Override
    public EngineModule create(ModuleContext context) {
        return new EntityModuleImpl(context);
    }

    /**
     * Entity module implementation.
     */
    public static class EntityModuleImpl implements EngineModule {
        private final ModuleContext context;

        public EntityModuleImpl(ModuleContext context) {
            this.context = context;
        }

        @Override
        public List<EngineSystem> createSystems() {
            return List.of();
        }

        @Override
        public List<EngineCommand> createCommands() {
            return List.of(
                    createSpawnCommand(),
                    createSetPositionCommand()
            );
        }

        @Override
        public List<BaseComponent> createComponents() {
            return ALL_COMPONENTS;
        }

        @Override
        public BaseComponent createFlagComponent() {
            return FLAG;
        }

        @Override
        public String getName() {
            return "EntityModule";
        }

        /**
         * Command to spawn a new entity.
         *
         * <p>Payload:
         * <ul>
         *   <li>matchId (long) - Target match</li>
         *   <li>playerId (long) - Owner player ID</li>
         *   <li>entityType (long) - Entity type ID</li>
         *   <li>positionX, positionY, positionZ (float) - Initial position (default 0)</li>
         * </ul>
         */
        private EngineCommand createSpawnCommand() {
            return CommandBuilder.newCommand()
                    .withName("spawn")
                    .withSchema(Map.of(
                            "matchId", Long.class,
                            "playerId", Long.class,
                            "entityType", Long.class,
                            "positionX", Float.class,
                            "positionY", Float.class,
                            "positionZ", Float.class
                    ))
                    .withExecution(payload -> {
                        Map<String, Object> data = payload.getPayload();
                        long matchId = extractLong(data, "matchId");
                        long playerId = extractLong(data, "playerId");
                        long entityType = extractLong(data, "entityType");
                        float posX = extractFloat(data, "positionX", 0);
                        float posY = extractFloat(data, "positionY", 0);
                        float posZ = extractFloat(data, "positionZ", 0);

                        // Create the entity
                        long entityId = context.getEntityFactory().createEntity(
                                matchId,
                                CORE_COMPONENTS,
                                new float[]{entityType, playerId, playerId}
                        );

                        // Attach position components
                        EntityComponentStore store = context.getEntityComponentStore();
                        store.attachComponents(entityId, POSITION_COMPONENTS,
                                new float[]{posX, posY, posZ});

                        // Attach flag components for match's enabled modules
                        attachModuleFlags(entityId, matchId);

                        log.info("Spawned entity {} at ({}, {}, {}) for match {} with type {}",
                                entityId, posX, posY, posZ, matchId, entityType);
                    })
                    .build();
        }

        /**
         * Command to set entity position.
         *
         * <p>Payload:
         * <ul>
         *   <li>entityId (long) - Target entity</li>
         *   <li>positionX, positionY (float) - New position</li>
         *   <li>positionZ (float) - Optional Z position</li>
         * </ul>
         */
        private EngineCommand createSetPositionCommand() {
            return CommandBuilder.newCommand()
                    .withName("setPosition")
                    .withSchema(Map.of(
                            "entityId", Long.class,
                            "positionX", Float.class,
                            "positionY", Float.class,
                            "positionZ", Float.class
                    ))
                    .withExecution(payload -> {
                        Map<String, Object> data = payload.getPayload();
                        long entityId = extractLong(data, "entityId");
                        if (entityId == 0) {
                            log.warn("setPosition: missing entityId");
                            return;
                        }

                        EntityComponentStore store = context.getEntityComponentStore();

                        if (data.containsKey("positionX")) {
                            store.attachComponent(entityId, POSITION_X, extractFloat(data, "positionX", 0));
                        }
                        if (data.containsKey("positionY")) {
                            store.attachComponent(entityId, POSITION_Y, extractFloat(data, "positionY", 0));
                        }
                        if (data.containsKey("positionZ")) {
                            store.attachComponent(entityId, POSITION_Z, extractFloat(data, "positionZ", 0));
                        }

                        log.debug("Set position for entity {}", entityId);
                    })
                    .build();
        }

        private void attachModuleFlags(long entityId, long matchId) {
            MatchService matchService = context.getMatchService();
            if (matchService == null) {
                log.warn("No match service found in module context");
                return;
            }
            Optional<Match> matchOpt = matchService.getMatch(matchId);
            if (matchOpt.isPresent()) {
                Match match = matchOpt.get();
                ModuleResolver resolver = context.getModuleResolver();
                if (resolver == null) {
                    log.warn("No module resolver found in module context");
                    return;
                }
                EntityComponentStore store = context.getEntityComponentStore();
                for (String module : match.enabledModules()) {
                    EngineModule moduleFound = resolver.resolveModule(module);
                    if (moduleFound != null) {
                        BaseComponent flagComponent = moduleFound.createFlagComponent();
                        if (flagComponent != null) {
                            store.attachComponent(entityId, flagComponent, 1.0f);
                            log.debug("Attached flag {} to entity {}", flagComponent.getName(), entityId);
                        }
                    }
                }
            }
        }

        private long extractLong(Map<String, Object> data, String key) {
            Object value = data.get(key);
            if (value == null) return 0;
            if (value instanceof Number n) return n.longValue();
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        private float extractFloat(Map<String, Object> data, String key, float defaultValue) {
            Object value = data.get(key);
            if (value == null) return defaultValue;
            if (value instanceof Number n) return n.floatValue();
            try {
                return Float.parseFloat(value.toString());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
    }

    /**
     * Base component for entity-related data.
     */
    public static class EntityComponent extends BaseComponent {
        public EntityComponent(long id, String name) {
            super(id, name);
        }
    }

    /**
     * Payload for spawn commands (used by protobuf adapter).
     */
    public record SpawnPayload(
            long matchId,
            long playerId,
            long entityType,
            float positionX,
            float positionY
    ) implements CommandPayload {
        @Override
        public Map<String, Object> getPayload() {
            return Map.of(
                    "matchId", matchId,
                    "playerId", playerId,
                    "entityType", entityType,
                    "positionX", positionX,
                    "positionY", positionY
            );
        }
    }
}
