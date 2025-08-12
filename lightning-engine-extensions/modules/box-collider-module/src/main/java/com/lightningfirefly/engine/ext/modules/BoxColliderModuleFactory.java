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
 * Module factory for the BoxCollider module.
 *
 * <p>Provides axis-aligned bounding box (AABB) collision detection:
 * <ul>
 *   <li>Box dimensions (width, height, depth)</li>
 *   <li>Offset from entity position</li>
 *   <li>Layer-based collision filtering</li>
 *   <li>Trigger vs solid colliders</li>
 *   <li>Collision events (entity IDs of colliding objects)</li>
 * </ul>
 *
 * <p>Collision detection is O(n²) - suitable for small numbers of entities.
 * For larger numbers, consider spatial partitioning (quadtree, spatial hash).
 *
 * <p>Uses EntityModule's shared position components (POSITION_X/Y/Z) for
 * determining entity positions.
 */
@Slf4j
public class BoxColliderModuleFactory implements ModuleFactory {

    // Box dimensions
    public static final BaseComponent BOX_WIDTH = new ColliderComponent(
            IdGeneratorV2.newId(), "BOX_WIDTH");
    public static final BaseComponent BOX_HEIGHT = new ColliderComponent(
            IdGeneratorV2.newId(), "BOX_HEIGHT");
    public static final BaseComponent BOX_DEPTH = new ColliderComponent(
            IdGeneratorV2.newId(), "BOX_DEPTH");

    // Offset from entity position (collider center = position + offset)
    public static final BaseComponent OFFSET_X = new ColliderComponent(
            IdGeneratorV2.newId(), "OFFSET_X");
    public static final BaseComponent OFFSET_Y = new ColliderComponent(
            IdGeneratorV2.newId(), "OFFSET_Y");
    public static final BaseComponent OFFSET_Z = new ColliderComponent(
            IdGeneratorV2.newId(), "OFFSET_Z");

    // Collision filtering
    public static final BaseComponent COLLISION_LAYER = new ColliderComponent(
            IdGeneratorV2.newId(), "COLLISION_LAYER");
    public static final BaseComponent COLLISION_MASK = new ColliderComponent(
            IdGeneratorV2.newId(), "COLLISION_MASK");

    // Trigger mode (1 = trigger, 0 = solid)
    public static final BaseComponent IS_TRIGGER = new ColliderComponent(
            IdGeneratorV2.newId(), "IS_TRIGGER");

    // Collision state (updated each tick)
    public static final BaseComponent IS_COLLIDING = new ColliderComponent(
            IdGeneratorV2.newId(), "IS_COLLIDING");
    public static final BaseComponent COLLISION_COUNT = new ColliderComponent(
            IdGeneratorV2.newId(), "COLLISION_COUNT");
    public static final BaseComponent LAST_COLLISION_ENTITY = new ColliderComponent(
            IdGeneratorV2.newId(), "LAST_COLLISION_ENTITY");

    // Collision normal (direction to push out)
    public static final BaseComponent COLLISION_NORMAL_X = new ColliderComponent(
            IdGeneratorV2.newId(), "COLLISION_NORMAL_X");
    public static final BaseComponent COLLISION_NORMAL_Y = new ColliderComponent(
            IdGeneratorV2.newId(), "COLLISION_NORMAL_Y");

    // Penetration depth
    public static final BaseComponent PENETRATION_DEPTH = new ColliderComponent(
            IdGeneratorV2.newId(), "PENETRATION_DEPTH");

    // Collision handler components
    public static final BaseComponent COLLISION_HANDLER_TYPE = new ColliderComponent(
            IdGeneratorV2.newId(), "COLLISION_HANDLER_TYPE");
    public static final BaseComponent COLLISION_HANDLER_PARAM1 = new ColliderComponent(
            IdGeneratorV2.newId(), "COLLISION_HANDLER_PARAM1");
    public static final BaseComponent COLLISION_HANDLER_PARAM2 = new ColliderComponent(
            IdGeneratorV2.newId(), "COLLISION_HANDLER_PARAM2");
    public static final BaseComponent COLLISION_HANDLED_TICK = new ColliderComponent(
            IdGeneratorV2.newId(), "COLLISION_HANDLED_TICK");

    // Module flag
    public static final BaseComponent FLAG = new ColliderComponent(
            IdGeneratorV2.newId(), "boxCollider");

    // Collision handler type constant
    public static final int HANDLER_NONE = 0;

    // Component groups
    public static final List<BaseComponent> DIMENSION_COMPONENTS =
            List.of(BOX_WIDTH, BOX_HEIGHT, BOX_DEPTH);

    public static final List<BaseComponent> OFFSET_COMPONENTS =
            List.of(OFFSET_X, OFFSET_Y, OFFSET_Z);

    public static final List<BaseComponent> COLLISION_STATE_COMPONENTS =
            List.of(IS_COLLIDING, COLLISION_COUNT, LAST_COLLISION_ENTITY,
                    COLLISION_NORMAL_X, COLLISION_NORMAL_Y, PENETRATION_DEPTH);

    public static final List<BaseComponent> HANDLER_COMPONENTS =
            List.of(COLLISION_HANDLER_TYPE, COLLISION_HANDLER_PARAM1,
                    COLLISION_HANDLER_PARAM2, COLLISION_HANDLED_TICK);

    public static final List<BaseComponent> ALL_COMPONENTS = List.of(
            BOX_WIDTH, BOX_HEIGHT, BOX_DEPTH,
            OFFSET_X, OFFSET_Y, OFFSET_Z,
            COLLISION_LAYER, COLLISION_MASK,
            IS_TRIGGER,
            IS_COLLIDING, COLLISION_COUNT, LAST_COLLISION_ENTITY,
            COLLISION_NORMAL_X, COLLISION_NORMAL_Y, PENETRATION_DEPTH,
            COLLISION_HANDLER_TYPE, COLLISION_HANDLER_PARAM1,
            COLLISION_HANDLER_PARAM2, COLLISION_HANDLED_TICK,
            FLAG
    );

    @Override
    public EngineModule create(ModuleContext context) {
        return new BoxColliderModule(context);
    }

    /**
     * BoxCollider component marker class.
     */
    public static class ColliderComponent extends BaseComponent {
        public ColliderComponent(long id, String name) {
            super(id, name);
        }
    }

    /**
     * Collision event record containing all collision information.
     */
    public record CollisionEvent(
            long selfEntity,
            long otherEntity,
            CollisionInfo info,
            float param1,
            float param2,
            int handlerType,
            long tick
    ) {}

    /**
     * Collision handler interface for custom collision responses.
     */
    @FunctionalInterface
    public interface CollisionHandler {
        /**
         * Handle a collision event.
         *
         * @param context the module context
         * @param event the collision event containing all relevant data
         */
        void handle(ModuleContext context, CollisionEvent event);
    }

    /**
     * BoxCollider module implementation.
     */
    public static class BoxColliderModule implements EngineModule {
        private final ModuleContext context;
        private final List<Long> deleteQueue = new ArrayList<>();

        // Collision pairs detected this tick (for event processing)
        private final List<CollisionPair> collisionPairs = new ArrayList<>();

        // Collision events emitted this tick
        private final List<CollisionEvent> collisionEvents = new ArrayList<>();

        // Collision handlers registry
        private final Map<Integer, CollisionHandler> collisionHandlers = new HashMap<>();

        // Tick counter for collision enter detection
        private long currentTick = 0;

        public BoxColliderModule(ModuleContext context) {
            this.context = context;
        }

        /**
         * Register a collision handler.
         *
         * @param handlerType the handler type ID (use values > 0)
         * @param handler the handler implementation
         */
        public void registerCollisionHandler(int handlerType, CollisionHandler handler) {
            collisionHandlers.put(handlerType, handler);
            log.info("Registered collision handler type {}", handlerType);
        }

        /**
         * Get collision events from the last tick.
         */
        public List<CollisionEvent> getCollisionEvents() {
            return new ArrayList<>(collisionEvents);
        }

        @Override
        public List<EngineSystem> createSystems() {
            return List.of(
                    createCollisionDetectionSystem(),
                    createHandleCollisionSystem(),
                    createCleanupSystem()
            );
        }

        @Override
        public List<EngineCommand> createCommands() {
            return List.of(
                    attachBoxColliderCommand(),
                    attachCollisionHandlerCommand(),
                    setColliderSizeCommand(),
                    setCollisionLayerCommand(),
                    deleteBoxColliderCommand()
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
            return "BoxColliderModule";
        }

        /**
         * Get collision pairs detected in the last tick.
         * Useful for game logic that needs to respond to collisions.
         */
        public List<CollisionPair> getCollisionPairs() {
            return new ArrayList<>(collisionPairs);
        }

        // ========== Systems ==========

        /**
         * Collision detection system using O(n²) AABB checks.
         *
         * <p>For each pair of entities with colliders:
         * <ol>
         *   <li>Check if layers allow collision (layer & mask)</li>
         *   <li>Calculate AABB bounds</li>
         *   <li>Test for intersection</li>
         *   <li>Calculate penetration depth and normal</li>
         *   <li>Update collision state components</li>
         * </ol>
         */
        private EngineSystem createCollisionDetectionSystem() {
            return () -> {
                EntityComponentStore store = context.getEntityComponentStore();
                Set<Long> entities = store.getEntitiesWithComponents(List.of(FLAG));

                // Increment tick counter
                currentTick++;

                // Clear previous collision state and events
                collisionPairs.clear();
                collisionEvents.clear();

                for (long entity : entities) {
                    store.attachComponent(entity, IS_COLLIDING, 0);
                    store.attachComponent(entity, COLLISION_COUNT, 0);
                    store.attachComponent(entity, LAST_COLLISION_ENTITY, 0);
                    store.attachComponent(entity, COLLISION_NORMAL_X, 0);
                    store.attachComponent(entity, COLLISION_NORMAL_Y, 0);
                    store.attachComponent(entity, PENETRATION_DEPTH, 0);
                }

                // Convert to list for O(n²) iteration
                List<Long> entityList = new ArrayList<>(entities);
                int n = entityList.size();

                // Check all pairs
                for (int i = 0; i < n; i++) {
                    for (int j = i + 1; j < n; j++) {
                        long entityA = entityList.get(i);
                        long entityB = entityList.get(j);

                        // Check layer filtering
                        if (!canCollide(store, entityA, entityB)) {
                            continue;
                        }

                        // Get AABBs
                        AABB boxA = getAABB(store, entityA);
                        AABB boxB = getAABB(store, entityB);

                        // Test intersection
                        if (boxA.intersects(boxB)) {
                            // Calculate penetration and normal
                            CollisionInfo info = calculateCollisionInfo(boxA, boxB);

                            // Update collision state for both entities
                            updateCollisionState(store, entityA, entityB, info);
                            updateCollisionState(store, entityB, entityA, info.inverted());

                            // Record collision pair
                            collisionPairs.add(new CollisionPair(entityA, entityB, info));

                            log.debug("Collision detected: entity {} <-> entity {} (depth={})",
                                    entityA, entityB, info.penetrationDepth);
                        }
                    }
                }

                if (!collisionPairs.isEmpty()) {
                    log.debug("Detected {} collision pairs", collisionPairs.size());
                }
            };
        }

        /**
         * Cleanup system for deleted colliders.
         */
        private EngineSystem createCleanupSystem() {
            return () -> {
                EntityComponentStore store = context.getEntityComponentStore();

                // Clean up deleted colliders
                for (Long entityId : deleteQueue) {
                    for (BaseComponent c : ALL_COMPONENTS) {
                        store.removeComponent(entityId, c);
                    }
                    log.debug("Cleaned up box collider for entity {}", entityId);
                }
                deleteQueue.clear();
            };
        }

        /**
         * Handle collision system - processes collision events and invokes handlers.
         *
         * <p>For each collision pair detected by the collision detection system:
         * <ol>
         *   <li>Check if either entity has a collision handler</li>
         *   <li>If handler exists, check if this is a new collision (enter) or ongoing</li>
         *   <li>Invoke the appropriate handler for each entity</li>
         *   <li>Record collision event for external processing</li>
         * </ol>
         */
        private EngineSystem createHandleCollisionSystem() {
            return () -> {
                EntityComponentStore store = context.getEntityComponentStore();

                for (CollisionPair pair : collisionPairs) {
                    long entityA = pair.entityA();
                    long entityB = pair.entityB();
                    CollisionInfo info = pair.info();

                    // Get handler types for both entities
                    int handlerTypeA = (int) store.getComponent(entityA, COLLISION_HANDLER_TYPE);
                    int handlerTypeB = (int) store.getComponent(entityB, COLLISION_HANDLER_TYPE);

                    // Skip if neither entity has a handler
                    if (handlerTypeA == HANDLER_NONE && handlerTypeB == HANDLER_NONE) {
                        continue;
                    }

                    // Check if this is a new collision (collision enter)
                    // by comparing the last handled tick
                    long lastHandledA = (long) store.getComponent(entityA, COLLISION_HANDLED_TICK);
                    long lastHandledB = (long) store.getComponent(entityB, COLLISION_HANDLED_TICK);

                    boolean isNewCollisionA = lastHandledA < currentTick - 1;
                    boolean isNewCollisionB = lastHandledB < currentTick - 1;

                    // Invoke handler for entity A
                    if (handlerTypeA != HANDLER_NONE) {
                        CollisionHandler handlerA = collisionHandlers.get(handlerTypeA);
                        if (handlerA != null) {
                            float param1A = store.getComponent(entityA, COLLISION_HANDLER_PARAM1);
                            float param2A = store.getComponent(entityA, COLLISION_HANDLER_PARAM2);
                            CollisionEvent eventA = new CollisionEvent(
                                    entityA, entityB, info, param1A, param2A, handlerTypeA, currentTick);
                            handlerA.handle(context, eventA);
                            store.attachComponent(entityA, COLLISION_HANDLED_TICK, currentTick);
                            collisionEvents.add(eventA);
                            log.debug("Invoked handler {} for entity {} (new={})",
                                    handlerTypeA, entityA, isNewCollisionA);
                        }
                    }

                    // Invoke handler for entity B
                    if (handlerTypeB != HANDLER_NONE) {
                        CollisionHandler handlerB = collisionHandlers.get(handlerTypeB);
                        if (handlerB != null) {
                            float param1B = store.getComponent(entityB, COLLISION_HANDLER_PARAM1);
                            float param2B = store.getComponent(entityB, COLLISION_HANDLER_PARAM2);
                            // Pass inverted collision info for entity B
                            CollisionEvent eventB = new CollisionEvent(
                                    entityB, entityA, info.inverted(), param1B, param2B, handlerTypeB, currentTick);
                            handlerB.handle(context, eventB);
                            store.attachComponent(entityB, COLLISION_HANDLED_TICK, currentTick);
                            collisionEvents.add(eventB);
                            log.debug("Invoked handler {} for entity {} (new={})",
                                    handlerTypeB, entityB, isNewCollisionB);
                        }
                    }
                }

                if (!collisionEvents.isEmpty()) {
                    log.debug("Processed {} collision events", collisionEvents.size());
                }
            };
        }

        // ========== Collision Helpers ==========

        /**
         * Check if two entities can collide based on layer/mask.
         */
        private boolean canCollide(EntityComponentStore store, long a, long b) {
            int layerA = (int) store.getComponent(a, COLLISION_LAYER);
            int maskA = (int) store.getComponent(a, COLLISION_MASK);
            int layerB = (int) store.getComponent(b, COLLISION_LAYER);
            int maskB = (int) store.getComponent(b, COLLISION_MASK);

            // A can hit B if A's mask includes B's layer, and vice versa
            return ((maskA & layerB) != 0) && ((maskB & layerA) != 0);
        }

        /**
         * Get the AABB for an entity.
         * Uses shared position components from EntityModule.
         */
        private AABB getAABB(EntityComponentStore store, long entity) {
            // Get position from EntityModule's shared position components
            float posX = store.getComponent(entity, EntityModuleFactory.POSITION_X);
            float posY = store.getComponent(entity, EntityModuleFactory.POSITION_Y);

            // Get offset
            float offsetX = store.getComponent(entity, OFFSET_X);
            float offsetY = store.getComponent(entity, OFFSET_Y);

            // Get dimensions
            float width = store.getComponent(entity, BOX_WIDTH);
            float height = store.getComponent(entity, BOX_HEIGHT);

            // Calculate center
            float centerX = posX + offsetX;
            float centerY = posY + offsetY;

            // Calculate half extents
            float halfW = width / 2;
            float halfH = height / 2;

            return new AABB(
                    centerX - halfW, centerY - halfH,  // min
                    centerX + halfW, centerY + halfH   // max
            );
        }

        /**
         * Calculate collision info (normal and penetration depth).
         */
        private CollisionInfo calculateCollisionInfo(AABB a, AABB b) {
            // Calculate overlap on each axis
            float overlapX = Math.min(a.maxX - b.minX, b.maxX - a.minX);
            float overlapY = Math.min(a.maxY - b.minY, b.maxY - a.minY);

            // The normal points from A to B, along the axis of minimum penetration
            float normalX, normalY, penetration;

            if (overlapX < overlapY) {
                penetration = overlapX;
                normalX = (a.centerX() < b.centerX()) ? 1 : -1;
                normalY = 0;
            } else {
                penetration = overlapY;
                normalX = 0;
                normalY = (a.centerY() < b.centerY()) ? 1 : -1;
            }

            return new CollisionInfo(normalX, normalY, penetration);
        }

        /**
         * Update collision state components for an entity.
         */
        private void updateCollisionState(EntityComponentStore store, long entity,
                                          long otherEntity, CollisionInfo info) {
            float currentCount = store.getComponent(entity, COLLISION_COUNT);
            store.attachComponent(entity, IS_COLLIDING, 1);
            store.attachComponent(entity, COLLISION_COUNT, currentCount + 1);
            store.attachComponent(entity, LAST_COLLISION_ENTITY, otherEntity);
            store.attachComponent(entity, COLLISION_NORMAL_X, info.normalX);
            store.attachComponent(entity, COLLISION_NORMAL_Y, info.normalY);
            store.attachComponent(entity, PENETRATION_DEPTH, info.penetrationDepth);
        }

        // ========== Commands ==========

        /**
         * Attach box collider to entity.
         *
         * <p>Payload:
         * <ul>
         *   <li>entityId (long) - Target entity</li>
         *   <li>width, height (float) - Box dimensions</li>
         *   <li>offsetX, offsetY (float) - Offset from position (default 0)</li>
         *   <li>layer (int) - Collision layer (default 1)</li>
         *   <li>mask (int) - Collision mask (default -1 = all layers)</li>
         *   <li>isTrigger (boolean) - Trigger mode (default false)</li>
         * </ul>
         */
        private EngineCommand attachBoxColliderCommand() {
            Map<String, Class<?>> schema = new HashMap<>();
            schema.put("entityId", Long.class);
            schema.put("width", Float.class);
            schema.put("height", Float.class);
            schema.put("depth", Float.class);
            schema.put("offsetX", Float.class);
            schema.put("offsetY", Float.class);
            schema.put("offsetZ", Float.class);
            schema.put("layer", Integer.class);
            schema.put("mask", Integer.class);
            schema.put("isTrigger", Boolean.class);

            return CommandBuilder.newCommand()
                    .withName("attachBoxCollider")
                    .withSchema(schema)
                    .withExecution(payload -> {
                        Map<String, Object> data = payload.getPayload();
                        long entityId = extractLong(data, "entityId");
                        if (entityId == 0) {
                            log.warn("attachBoxCollider: missing entityId");
                            return;
                        }

                        float width = extractFloat(data, "width", 1.0f);
                        float height = extractFloat(data, "height", 1.0f);
                        float depth = extractFloat(data, "depth", 1.0f);
                        float offsetX = extractFloat(data, "offsetX", 0);
                        float offsetY = extractFloat(data, "offsetY", 0);
                        float offsetZ = extractFloat(data, "offsetZ", 0);
                        int layer = extractInt(data, "layer", 1);
                        int mask = extractInt(data, "mask", -1); // -1 = all bits set
                        boolean isTrigger = extractBoolean(data, "isTrigger", false);

                        EntityComponentStore store = context.getEntityComponentStore();

                        store.attachComponents(entityId, DIMENSION_COMPONENTS,
                                new float[]{width, height, depth});
                        store.attachComponents(entityId, OFFSET_COMPONENTS,
                                new float[]{offsetX, offsetY, offsetZ});
                        store.attachComponent(entityId, COLLISION_LAYER, layer);
                        store.attachComponent(entityId, COLLISION_MASK, mask);
                        store.attachComponent(entityId, IS_TRIGGER, isTrigger ? 1 : 0);

                        // Initialize collision state
                        store.attachComponent(entityId, IS_COLLIDING, 0);
                        store.attachComponent(entityId, COLLISION_COUNT, 0);
                        store.attachComponent(entityId, LAST_COLLISION_ENTITY, 0);
                        store.attachComponent(entityId, COLLISION_NORMAL_X, 0);
                        store.attachComponent(entityId, COLLISION_NORMAL_Y, 0);
                        store.attachComponent(entityId, PENETRATION_DEPTH, 0);
                        store.attachComponent(entityId, FLAG, 1.0f);

                        log.info("Attached box collider to entity {}: size={}x{}, layer={}, mask={}",
                                entityId, width, height, layer, mask);
                    })
                    .build();
        }

        /**
         * Attach collision handler to entity.
         *
         * <p>Configures what happens when this entity collides with another.
         * Handlers must be registered using {@link BoxColliderModule#registerCollisionHandler}.
         *
         * <p>Payload:
         * <ul>
         *   <li>entityId (long) - Target entity (must have box collider)</li>
         *   <li>handlerType (int) - Handler type ID (0 = no handler, others must be registered)</li>
         *   <li>param1 (float) - First handler parameter</li>
         *   <li>param2 (float) - Second handler parameter</li>
         * </ul>
         */
        private EngineCommand attachCollisionHandlerCommand() {
            Map<String, Class<?>> schema = new HashMap<>();
            schema.put("entityId", Long.class);
            schema.put("handlerType", Integer.class);
            schema.put("param1", Float.class);
            schema.put("param2", Float.class);

            return CommandBuilder.newCommand()
                    .withName("attachCollisionHandler")
                    .withSchema(schema)
                    .withExecution(payload -> {
                        Map<String, Object> data = payload.getPayload();
                        long entityId = extractLong(data, "entityId");
                        if (entityId == 0) {
                            log.warn("attachCollisionHandler: missing entityId");
                            return;
                        }

                        int handlerType = extractInt(data, "handlerType", HANDLER_NONE);
                        float param1 = extractFloat(data, "param1", 0);
                        float param2 = extractFloat(data, "param2", 0);

                        EntityComponentStore store = context.getEntityComponentStore();

                        // Verify entity has a box collider
                        float hasCollider = store.getComponent(entityId, FLAG);
                        if (hasCollider == 0) {
                            log.warn("attachCollisionHandler: entity {} has no box collider", entityId);
                            return;
                        }

                        store.attachComponent(entityId, COLLISION_HANDLER_TYPE, handlerType);
                        store.attachComponent(entityId, COLLISION_HANDLER_PARAM1, param1);
                        store.attachComponent(entityId, COLLISION_HANDLER_PARAM2, param2);
                        store.attachComponent(entityId, COLLISION_HANDLED_TICK, 0);

                        log.info("Attached collision handler to entity {}: type={}, param1={}, param2={}",
                                entityId, handlerType, param1, param2);
                    })
                    .build();
        }

        /**
         * Set collider size.
         */
        private EngineCommand setColliderSizeCommand() {
            return CommandBuilder.newCommand()
                    .withName("setColliderSize")
                    .withSchema(Map.of(
                            "entityId", Long.class,
                            "width", Float.class,
                            "height", Float.class
                    ))
                    .withExecution(payload -> {
                        Map<String, Object> data = payload.getPayload();
                        long entityId = extractLong(data, "entityId");
                        float width = extractFloat(data, "width", 1.0f);
                        float height = extractFloat(data, "height", 1.0f);

                        EntityComponentStore store = context.getEntityComponentStore();
                        store.attachComponent(entityId, BOX_WIDTH, width);
                        store.attachComponent(entityId, BOX_HEIGHT, height);

                        log.debug("Set collider size for entity {}: {}x{}", entityId, width, height);
                    })
                    .build();
        }

        /**
         * Set collision layer and mask.
         */
        private EngineCommand setCollisionLayerCommand() {
            return CommandBuilder.newCommand()
                    .withName("setCollisionLayer")
                    .withSchema(Map.of(
                            "entityId", Long.class,
                            "layer", Integer.class,
                            "mask", Integer.class
                    ))
                    .withExecution(payload -> {
                        Map<String, Object> data = payload.getPayload();
                        long entityId = extractLong(data, "entityId");
                        int layer = extractInt(data, "layer", 1);
                        int mask = extractInt(data, "mask", -1);

                        EntityComponentStore store = context.getEntityComponentStore();
                        store.attachComponent(entityId, COLLISION_LAYER, layer);
                        store.attachComponent(entityId, COLLISION_MASK, mask);

                        log.debug("Set collision layer for entity {}: layer={}, mask={}", entityId, layer, mask);
                    })
                    .build();
        }

        /**
         * Delete box collider from entity.
         */
        private EngineCommand deleteBoxColliderCommand() {
            return CommandBuilder.newCommand()
                    .withName("deleteBoxCollider")
                    .withSchema(Map.of("entityId", Long.class))
                    .withExecution(payload -> {
                        Map<String, Object> data = payload.getPayload();
                        long entityId = extractLong(data, "entityId");
                        deleteQueue.add(entityId);
                        log.debug("Queued box collider deletion for entity {}", entityId);
                    })
                    .build();
        }

        // ========== Helpers ==========

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

        private int extractInt(Map<String, Object> data, String key, int defaultValue) {
            Object value = data.get(key);
            if (value == null) return defaultValue;
            if (value instanceof Number n) return n.intValue();
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        private boolean extractBoolean(Map<String, Object> data, String key, boolean defaultValue) {
            Object value = data.get(key);
            if (value == null) return defaultValue;
            if (value instanceof Boolean b) return b;
            return Boolean.parseBoolean(value.toString());
        }
    }

    // ========== Data Classes ==========

    /**
     * Axis-aligned bounding box.
     */
    public record AABB(float minX, float minY, float maxX, float maxY) {

        public float centerX() {
            return (minX + maxX) / 2;
        }

        public float centerY() {
            return (minY + maxY) / 2;
        }

        public float width() {
            return maxX - minX;
        }

        public float height() {
            return maxY - minY;
        }

        /**
         * Test if this AABB intersects another.
         */
        public boolean intersects(AABB other) {
            return minX < other.maxX && maxX > other.minX &&
                   minY < other.maxY && maxY > other.minY;
        }
    }

    /**
     * Collision info containing normal and penetration depth.
     */
    public record CollisionInfo(float normalX, float normalY, float penetrationDepth) {

        /**
         * Return inverted collision info (for the other entity in the pair).
         */
        public CollisionInfo inverted() {
            return new CollisionInfo(-normalX, -normalY, penetrationDepth);
        }
    }

    /**
     * A collision pair detected during the tick.
     */
    public record CollisionPair(long entityA, long entityB, CollisionInfo info) {
    }
}
