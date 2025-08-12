package com.lightningfirefly.engine.ext.modules;

import com.lightningfirefly.engine.core.command.CommandBuilder;
import com.lightningfirefly.engine.core.command.EngineCommand;
import com.lightningfirefly.engine.core.store.BaseComponent;
import com.lightningfirefly.engine.core.store.EntityComponentStore;
import com.lightningfirefly.engine.core.system.EngineSystem;
import com.lightningfirefly.engine.ext.module.EngineModule;
import com.lightningfirefly.engine.ext.module.ModuleContext;
import com.lightningfirefly.engine.ext.module.ModuleFactory;
import com.lightningfirefly.engine.util.IdGeneratorV2;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Module factory for the Projectile module.
 *
 * <p>Provides projectile management:
 * <ul>
 *   <li>OWNER_ENTITY_ID - Entity that fired the projectile</li>
 *   <li>DAMAGE - Damage dealt on hit</li>
 *   <li>SPEED - Projectile speed</li>
 *   <li>DIRECTION_X/Y - Normalized direction vector</li>
 *   <li>LIFETIME - Ticks until auto-destroy (0 = no limit)</li>
 *   <li>TICKS_ALIVE - Current age in ticks</li>
 *   <li>PIERCE_COUNT - Number of targets to pierce (0 = destroy on first hit)</li>
 *   <li>HITS_REMAINING - Remaining pierce count</li>
 * </ul>
 *
 * <p>Commands:
 * <ul>
 *   <li>spawnProjectile - Create a new projectile</li>
 *   <li>destroyProjectile - Manually destroy a projectile</li>
 * </ul>
 *
 * <p>Note: Uses EntityModule for positions, RigidBodyModule for velocity if available.
 */
@Slf4j
public class ProjectileModuleFactory implements ModuleFactory {

    // Projectile metadata
    public static final BaseComponent OWNER_ENTITY_ID = new ProjectileComponent(
            IdGeneratorV2.newId(), "OWNER_ENTITY_ID");
    public static final BaseComponent DAMAGE = new ProjectileComponent(
            IdGeneratorV2.newId(), "DAMAGE");

    // Movement
    public static final BaseComponent SPEED = new ProjectileComponent(
            IdGeneratorV2.newId(), "SPEED");
    public static final BaseComponent DIRECTION_X = new ProjectileComponent(
            IdGeneratorV2.newId(), "DIRECTION_X");
    public static final BaseComponent DIRECTION_Y = new ProjectileComponent(
            IdGeneratorV2.newId(), "DIRECTION_Y");

    // Lifetime management
    public static final BaseComponent LIFETIME = new ProjectileComponent(
            IdGeneratorV2.newId(), "LIFETIME");
    public static final BaseComponent TICKS_ALIVE = new ProjectileComponent(
            IdGeneratorV2.newId(), "TICKS_ALIVE");

    // Piercing
    public static final BaseComponent PIERCE_COUNT = new ProjectileComponent(
            IdGeneratorV2.newId(), "PIERCE_COUNT");
    public static final BaseComponent HITS_REMAINING = new ProjectileComponent(
            IdGeneratorV2.newId(), "HITS_REMAINING");

    // Projectile type (for different projectile behaviors)
    public static final BaseComponent PROJECTILE_TYPE = new ProjectileComponent(
            IdGeneratorV2.newId(), "PROJECTILE_TYPE");

    // Pending destroy flag
    public static final BaseComponent PENDING_DESTROY = new ProjectileComponent(
            IdGeneratorV2.newId(), "PENDING_DESTROY");

    // Module flag
    public static final BaseComponent FLAG = new ProjectileComponent(
            IdGeneratorV2.newId(), "projectile");

    /**
     * Core components for projectile tracking.
     */
    public static final List<BaseComponent> CORE_COMPONENTS = List.of(
            OWNER_ENTITY_ID, DAMAGE, SPEED,
            DIRECTION_X, DIRECTION_Y,
            LIFETIME, TICKS_ALIVE,
            PIERCE_COUNT, HITS_REMAINING,
            PROJECTILE_TYPE, PENDING_DESTROY
    );

    /**
     * Components for snapshot export.
     */
    public static final List<BaseComponent> ALL_COMPONENTS = CORE_COMPONENTS;

    @Override
    public EngineModule create(ModuleContext context) {
        return new ProjectileModule(context);
    }

    /**
     * Projectile module implementation.
     */
    public static class ProjectileModule implements EngineModule {
        private final ModuleContext context;
        private final List<Long> destroyQueue = new ArrayList<>();

        public ProjectileModule(ModuleContext context) {
            this.context = context;
        }

        @Override
        public List<EngineSystem> createSystems() {
            return List.of(
                    createMovementSystem(),
                    createLifetimeSystem(),
                    createCleanupSystem()
            );
        }

        @Override
        public List<EngineCommand> createCommands() {
            return List.of(
                    createSpawnProjectileCommand(),
                    createDestroyProjectileCommand()
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
            return "ProjectileModule";
        }

        /**
         * System to move projectiles based on speed and direction.
         *
         * <p>Updates position using EntityModule's position components.
         */
        private EngineSystem createMovementSystem() {
            return () -> {
                EntityComponentStore store = context.getEntityComponentStore();
                Set<Long> entities = store.getEntitiesWithComponents(List.of(FLAG));

                for (Long entity : entities) {
                    float pendingDestroy = store.getComponent(entity, PENDING_DESTROY);
                    if (pendingDestroy > 0) continue;

                    float speed = store.getComponent(entity, SPEED);
                    float dirX = store.getComponent(entity, DIRECTION_X);
                    float dirY = store.getComponent(entity, DIRECTION_Y);

                    if (speed <= 0) continue;

                    // Get current position from EntityModule
                    float posX = store.getComponent(entity, EntityModuleFactory.POSITION_X);
                    float posY = store.getComponent(entity, EntityModuleFactory.POSITION_Y);

                    // Update position
                    posX += dirX * speed;
                    posY += dirY * speed;

                    store.attachComponent(entity, EntityModuleFactory.POSITION_X, posX);
                    store.attachComponent(entity, EntityModuleFactory.POSITION_Y, posY);

                    log.trace("Projectile {} moved to ({}, {})", entity, posX, posY);
                }
            };
        }

        /**
         * System to manage projectile lifetime and auto-destroy.
         */
        private EngineSystem createLifetimeSystem() {
            return () -> {
                EntityComponentStore store = context.getEntityComponentStore();
                Set<Long> entities = store.getEntitiesWithComponents(List.of(FLAG));

                for (Long entity : entities) {
                    float pendingDestroy = store.getComponent(entity, PENDING_DESTROY);
                    if (pendingDestroy > 0) {
                        destroyQueue.add(entity);
                        continue;
                    }

                    float lifetime = store.getComponent(entity, LIFETIME);
                    if (lifetime <= 0) continue; // No lifetime limit

                    float ticksAlive = store.getComponent(entity, TICKS_ALIVE);
                    ticksAlive++;
                    store.attachComponent(entity, TICKS_ALIVE, ticksAlive);

                    if (ticksAlive >= lifetime) {
                        log.debug("Projectile {} expired after {} ticks", entity, ticksAlive);
                        destroyQueue.add(entity);
                    }
                }
            };
        }

        /**
         * System to clean up destroyed projectiles.
         */
        private EngineSystem createCleanupSystem() {
            return () -> {
                if (destroyQueue.isEmpty()) return;

                EntityComponentStore store = context.getEntityComponentStore();

                for (Long entityId : destroyQueue) {
                    // Remove all projectile components
                    for (BaseComponent c : CORE_COMPONENTS) {
                        store.removeComponent(entityId, c);
                    }
                    store.removeComponent(entityId, FLAG);

                    // Also remove position components if we "own" them
                    // Note: This is aggressive - consider only removing our flag
                    log.debug("Cleaned up projectile {}", entityId);
                }

                destroyQueue.clear();
            };
        }

        /**
         * Command to spawn a new projectile.
         *
         * <p>Payload:
         * <ul>
         *   <li>matchId (long) - Target match</li>
         *   <li>ownerEntityId (long) - Entity that fired the projectile</li>
         *   <li>positionX/Y (float) - Starting position</li>
         *   <li>directionX/Y (float) - Direction vector (will be normalized)</li>
         *   <li>speed (float) - Projectile speed</li>
         *   <li>damage (float) - Damage dealt on hit</li>
         *   <li>lifetime (float) - Ticks until auto-destroy (0 = no limit)</li>
         *   <li>pierceCount (float) - Number of targets to pierce (default 0)</li>
         *   <li>projectileType (float) - Type identifier (default 0)</li>
         * </ul>
         */
        private EngineCommand createSpawnProjectileCommand() {
            Map<String, Class<?>> schema = new HashMap<>();
            schema.put("matchId", Long.class);
            schema.put("ownerEntityId", Long.class);
            schema.put("positionX", Float.class);
            schema.put("positionY", Float.class);
            schema.put("directionX", Float.class);
            schema.put("directionY", Float.class);
            schema.put("speed", Float.class);
            schema.put("damage", Float.class);
            schema.put("lifetime", Float.class);
            schema.put("pierceCount", Float.class);
            schema.put("projectileType", Float.class);

            return CommandBuilder.newCommand()
                    .withName("spawnProjectile")
                    .withSchema(schema)
                    .withExecution(payload -> {
                        Map<String, Object> data = payload.getPayload();
                        long matchId = extractLong(data, "matchId");
                        long ownerEntityId = extractLong(data, "ownerEntityId");
                        float posX = extractFloat(data, "positionX", 0);
                        float posY = extractFloat(data, "positionY", 0);
                        float dirX = extractFloat(data, "directionX", 1);
                        float dirY = extractFloat(data, "directionY", 0);
                        float speed = extractFloat(data, "speed", 10);
                        float damage = extractFloat(data, "damage", 10);
                        float lifetime = extractFloat(data, "lifetime", 0);
                        float pierceCount = extractFloat(data, "pierceCount", 0);
                        float projectileType = extractFloat(data, "projectileType", 0);

                        // Normalize direction
                        float magnitude = (float) Math.sqrt(dirX * dirX + dirY * dirY);
                        if (magnitude > 0) {
                            dirX /= magnitude;
                            dirY /= magnitude;
                        }

                        // Create entity via EntityModule's spawn command (or directly)
                        long entityId = context.getEntityFactory().createEntity(
                                matchId,
                                EntityModuleFactory.CORE_COMPONENTS,
                                new float[]{projectileType, ownerEntityId, ownerEntityId}
                        );

                        EntityComponentStore store = context.getEntityComponentStore();

                        // Attach position
                        store.attachComponents(entityId, EntityModuleFactory.POSITION_COMPONENTS,
                                new float[]{posX, posY, 0});

                        // Attach projectile components
                        store.attachComponent(entityId, OWNER_ENTITY_ID, ownerEntityId);
                        store.attachComponent(entityId, DAMAGE, damage);
                        store.attachComponent(entityId, SPEED, speed);
                        store.attachComponent(entityId, DIRECTION_X, dirX);
                        store.attachComponent(entityId, DIRECTION_Y, dirY);
                        store.attachComponent(entityId, LIFETIME, lifetime);
                        store.attachComponent(entityId, TICKS_ALIVE, 0);
                        store.attachComponent(entityId, PIERCE_COUNT, pierceCount);
                        store.attachComponent(entityId, HITS_REMAINING, pierceCount);
                        store.attachComponent(entityId, PROJECTILE_TYPE, projectileType);
                        store.attachComponent(entityId, PENDING_DESTROY, 0);
                        store.attachComponent(entityId, FLAG, 1.0f);

                        log.info("Spawned projectile {} at ({}, {}) dir=({}, {}) speed={} damage={}",
                                entityId, posX, posY, dirX, dirY, speed, damage);
                    })
                    .build();
        }

        /**
         * Command to destroy a projectile.
         *
         * <p>Payload:
         * <ul>
         *   <li>entityId (long) - Projectile to destroy</li>
         * </ul>
         */
        private EngineCommand createDestroyProjectileCommand() {
            return CommandBuilder.newCommand()
                    .withName("destroyProjectile")
                    .withSchema(Map.of("entityId", Long.class))
                    .withExecution(payload -> {
                        Map<String, Object> data = payload.getPayload();
                        long entityId = extractLong(data, "entityId");

                        if (entityId == 0) {
                            log.warn("destroyProjectile: missing entityId");
                            return;
                        }

                        EntityComponentStore store = context.getEntityComponentStore();
                        store.attachComponent(entityId, PENDING_DESTROY, 1.0f);

                        log.debug("Marked projectile {} for destruction", entityId);
                    })
                    .build();
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
     * Base component for projectile-related data.
     */
    public static class ProjectileComponent extends BaseComponent {
        public ProjectileComponent(long id, String name) {
            super(id, name);
        }
    }
}
