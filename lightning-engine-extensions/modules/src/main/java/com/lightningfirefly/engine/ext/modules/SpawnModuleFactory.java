package com.lightningfirefly.engine.ext.modules;

import com.lightningfirefly.engine.core.command.CommandPayload;
import com.lightningfirefly.engine.core.command.EngineCommand;
import com.lightningfirefly.engine.core.entity.CoreComponents;
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
 * Module factory for the Spawn module.
 *
 * <p>Provides functionality to create new entities with initial components.
 * Spawned entities are tagged with match and player IDs for isolation,
 * and receive flag components based on the match's enabled modules.
 */
@Slf4j
public class SpawnModuleFactory implements ModuleFactory {

    // Component definitions for spawned entities (basic metadata only)
    public static final BaseComponent ENTITY_TYPE = new SpawnComponent(
            IdGeneratorV2.newId(), "ENTITY_TYPE");
    public static final BaseComponent OWNER_ID = new SpawnComponent(
            IdGeneratorV2.newId(), "OWNER_ID");
    public static final BaseComponent PLAYER_ID = new SpawnComponent(
            IdGeneratorV2.newId(), "PLAYER_ID");
    public static final BaseComponent FLAG = new SpawnComponent(
            IdGeneratorV2.newId(), "spawn");

    /**
     * Core spawn components (type, owner, player).
     * MATCH_ID and ENTITY_ID are attached automatically by EntityFactory.
     */
    public static final List<BaseComponent> CORE_COMPONENTS =
            List.of(ENTITY_TYPE, OWNER_ID, PLAYER_ID);

    /**
     * Components included in snapshots.
     */
    public static final List<BaseComponent> ALL_COMPONENTS =
            List.of(ENTITY_TYPE, OWNER_ID, PLAYER_ID);

    @Override
    public EngineModule create(ModuleContext context) {
        return new SpawnModuleImpl(context);
    }

    /**
     * Spawn module implementation.
     */
    public static class SpawnModuleImpl implements EngineModule {
        private final ModuleContext context;
        private final SpawnEngineCommand spawnCommand;

        public SpawnModuleImpl(ModuleContext context) {
            this.context = context;
            this.spawnCommand = new SpawnEngineCommand(context);
        }

        @Override
        public List<EngineSystem> createSystems() {
            return List.of();
        }

        @Override
        public List<EngineCommand> createCommands() {
            return List.of(spawnCommand);
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
            return "SpawnModule";
        }
    }

    /**
     * Payload for the spawn command.
     */
    public static class SpawnPayload implements CommandPayload {
        private final long matchId;
        private final long playerId;
        private final long entityType;
        private final long positionX;
        private final long positionY;

        public SpawnPayload(long matchId, long playerId, long entityType, long positionX, long positionY) {
            this.matchId = matchId;
            this.playerId = playerId;
            this.entityType = entityType;
            this.positionX = positionX;
            this.positionY = positionY;
        }

        public long getMatchId() {
            return matchId;
        }

        public long getPlayerId() {
            return playerId;
        }

        public long getEntityType() {
            return entityType;
        }

        public long getPositionX() {
            return positionX;
        }

        public long getPositionY() {
            return positionY;
        }

        @Override
        public Map<String, Object> getPayload() {
            return Map.of("matchId", matchId, "playerId", playerId, "entityType", entityType);
        }
    }

    /**
     * Command to spawn a new entity.
     *
     * <p>When executed, this command:
     * <ul>
     *   <li>Creates a new entity using EntityFactory (which handles MATCH_ID and ENTITY_ID)</li>
     *   <li>Attaches core spawn components (type, owner, player)</li>
     *   <li>Attaches flag components for each module enabled in the match</li>
     * </ul>
     *
     * <p>Note: Position/velocity components should be added separately via MoveModule's commands.
     * This command only creates the basic entity with metadata.
     *
     * <p>The flag components provide module isolation - each module's systems
     * can filter entities by checking for their flag component.
     */
    public static class SpawnEngineCommand implements EngineCommand {
        private final ModuleContext context;

        public SpawnEngineCommand(ModuleContext context) {
            this.context = context;
        }

        @Override
        public String getName() {
            return "spawn";
        }

        @Override
        public Map<String, Class<?>> schema() {
            return Map.of(
                    "matchId", Long.class,
                    "playerId", Long.class,
                    "entityType", Long.class
            );
        }

        @Override
        public void executeCommand(CommandPayload payload) {
            // Extract spawn parameters from payload
            long matchId = 0L;
            long playerId = 0L;
            long entityType = 0L;

            if (payload instanceof SpawnPayload spawnPayload) {
                matchId = spawnPayload.getMatchId();
                playerId = spawnPayload.getPlayerId();
                entityType = spawnPayload.getEntityType();
            } else if (payload != null && payload.getPayload() != null) {
                // Support generic map-based payload from REST API
                Map<String, Object> data = payload.getPayload();
                matchId = extractLong(data, "matchId");
                playerId = extractLong(data, "playerId");
                entityType = extractLong(data, "entityType");
            }

            // Create the entity using EntityFactory (automatically attaches MATCH_ID and ENTITY_ID)
            long entityId = context.getEntityFactory().createEntity(
                    matchId,
                    CORE_COMPONENTS,
                    new float[]{entityType, playerId, playerId}  // entityType, ownerId, playerId
            );

            // Attach flag components for match's enabled modules
            attachModuleFlags(entityId, matchId);

            log.info("Spawned entity {} for match {} with type {}", entityId, matchId, entityType);
        }

        private long extractLong(Map<String, Object> data, String key) {
            Object value = data.get(key);
            if (value == null) {
                return 0;
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        /**
         * Attach flag components for all modules enabled in the given match.
         *
         * <p>This creates isolation between modules by tagging entities with
         * which modules should process them. Systems can filter entities by
         * checking for their module's flag component.
         */
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
                            // Flag value of 1 means "enabled for this module"
                            store.attachComponent(entityId, flagComponent, 1.0f);
                            log.info("Spawned {} with flag {}", entityId, flagComponent);
                        }
                    }
                }
            }
        }
    }

    /**
     * Base component for spawn-related data.
     */
    public static class SpawnComponent extends BaseComponent {
        public SpawnComponent(long id, String name) {
            super(id, name);
        }
    }
}
