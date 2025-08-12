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
 * Module factory for the RigidBody physics module.
 *
 * <p>Provides physics simulation with:
 * <ul>
 *   <li>Position tracking (uses EntityModule's shared positions)</li>
 *   <li>Velocity tracking (vx, vy, vz)</li>
 *   <li>Acceleration (ax, ay, az)</li>
 *   <li>Mass-based force calculations (F = ma)</li>
 *   <li>Angular velocity and rotation</li>
 *   <li>Drag/damping for realistic movement</li>
 * </ul>
 *
 * <p>The physics system runs each tick:
 * <ol>
 *   <li>Apply forces to calculate acceleration (a = F/m)</li>
 *   <li>Apply acceleration to velocity (v += a * dt)</li>
 *   <li>Apply velocity to position (p += v * dt)</li>
 *   <li>Apply drag to velocity (v *= (1 - drag))</li>
 *   <li>Clear accumulated forces</li>
 * </ol>
 *
 * <p>Note: Position components are from EntityModule (shared with rendering).
 */
@Slf4j
public class RigidBodyModuleFactory implements ModuleFactory {

    // Position components - use shared positions from EntityModule
    public static final BaseComponent POSITION_X = EntityModuleFactory.POSITION_X;
    public static final BaseComponent POSITION_Y = EntityModuleFactory.POSITION_Y;
    public static final BaseComponent POSITION_Z = EntityModuleFactory.POSITION_Z;

    // Velocity components
    public static final BaseComponent VELOCITY_X = new RigidBodyComponent(
            IdGeneratorV2.newId(), "VELOCITY_X");
    public static final BaseComponent VELOCITY_Y = new RigidBodyComponent(
            IdGeneratorV2.newId(), "VELOCITY_Y");
    public static final BaseComponent VELOCITY_Z = new RigidBodyComponent(
            IdGeneratorV2.newId(), "VELOCITY_Z");

    // Acceleration components (accumulated from forces)
    public static final BaseComponent ACCELERATION_X = new RigidBodyComponent(
            IdGeneratorV2.newId(), "ACCELERATION_X");
    public static final BaseComponent ACCELERATION_Y = new RigidBodyComponent(
            IdGeneratorV2.newId(), "ACCELERATION_Y");
    public static final BaseComponent ACCELERATION_Z = new RigidBodyComponent(
            IdGeneratorV2.newId(), "ACCELERATION_Z");

    // Force accumulator (cleared each tick after applying)
    public static final BaseComponent FORCE_X = new RigidBodyComponent(
            IdGeneratorV2.newId(), "FORCE_X");
    public static final BaseComponent FORCE_Y = new RigidBodyComponent(
            IdGeneratorV2.newId(), "FORCE_Y");
    public static final BaseComponent FORCE_Z = new RigidBodyComponent(
            IdGeneratorV2.newId(), "FORCE_Z");

    // Mass (for F = ma calculations)
    public static final BaseComponent MASS = new RigidBodyComponent(
            IdGeneratorV2.newId(), "MASS");

    // Angular components (2D rotation around Z axis)
    public static final BaseComponent ANGULAR_VELOCITY = new RigidBodyComponent(
            IdGeneratorV2.newId(), "ANGULAR_VELOCITY");
    public static final BaseComponent ROTATION = new RigidBodyComponent(
            IdGeneratorV2.newId(), "ROTATION");
    public static final BaseComponent TORQUE = new RigidBodyComponent(
            IdGeneratorV2.newId(), "TORQUE");
    public static final BaseComponent INERTIA = new RigidBodyComponent(
            IdGeneratorV2.newId(), "INERTIA");

    // Damping/drag coefficients
    public static final BaseComponent LINEAR_DRAG = new RigidBodyComponent(
            IdGeneratorV2.newId(), "LINEAR_DRAG");
    public static final BaseComponent ANGULAR_DRAG = new RigidBodyComponent(
            IdGeneratorV2.newId(), "ANGULAR_DRAG");

    // Module flag
    public static final BaseComponent FLAG = new RigidBodyComponent(
            IdGeneratorV2.newId(), "rigidBody");

    // Component groups - positions are from EntityModule
    public static final List<BaseComponent> POSITION_COMPONENTS =
            EntityModuleFactory.POSITION_COMPONENTS;

    public static final List<BaseComponent> VELOCITY_COMPONENTS =
            List.of(VELOCITY_X, VELOCITY_Y, VELOCITY_Z);

    public static final List<BaseComponent> ACCELERATION_COMPONENTS =
            List.of(ACCELERATION_X, ACCELERATION_Y, ACCELERATION_Z);

    public static final List<BaseComponent> FORCE_COMPONENTS =
            List.of(FORCE_X, FORCE_Y, FORCE_Z);

    /**
     * RigidBody's own components (excluding positions which are shared).
     */
    public static final List<BaseComponent> CORE_COMPONENTS = List.of(
            VELOCITY_X, VELOCITY_Y, VELOCITY_Z,
            ACCELERATION_X, ACCELERATION_Y, ACCELERATION_Z,
            FORCE_X, FORCE_Y, FORCE_Z,
            MASS,
            ANGULAR_VELOCITY, ROTATION, TORQUE, INERTIA,
            LINEAR_DRAG, ANGULAR_DRAG
    );

    /**
     * All components for snapshot export (excludes positions since they're in EntityModule).
     */
    public static final List<BaseComponent> ALL_COMPONENTS = List.of(
            VELOCITY_X, VELOCITY_Y, VELOCITY_Z,
            ACCELERATION_X, ACCELERATION_Y, ACCELERATION_Z,
            FORCE_X, FORCE_Y, FORCE_Z,
            MASS,
            ANGULAR_VELOCITY, ROTATION, TORQUE, INERTIA,
            LINEAR_DRAG, ANGULAR_DRAG,
            FLAG
    );

    @Override
    public EngineModule create(ModuleContext context) {
        return new RigidBodyModule(context);
    }

    /**
     * RigidBody component marker class.
     */
    public static class RigidBodyComponent extends BaseComponent {
        public RigidBodyComponent(long id, String name) {
            super(id, name);
        }
    }

    /**
     * RigidBody module implementation.
     */
    public static class RigidBodyModule implements EngineModule {
        private final ModuleContext context;
        private final List<Long> deleteQueue = new ArrayList<>();

        // Physics timestep (assuming 1 tick = 1/60 second)
        private static final float DT = 1.0f / 60.0f;

        public RigidBodyModule(ModuleContext context) {
            this.context = context;
        }

        @Override
        public List<EngineSystem> createSystems() {
            return List.of(
                    createForceIntegrationSystem(),
                    createPhysicsSystem(),
                    createCleanupSystem()
            );
        }

        @Override
        public List<EngineCommand> createCommands() {
            return List.of(
                    attachRigidBodyCommand(),
                    applyForceCommand(),
                    applyImpulseCommand(),
                    setVelocityCommand(),
                    setPositionCommand(),
                    applyTorqueCommand(),
                    deleteRigidBodyCommand()
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
            return "RigidBodyModule";
        }

        // ========== Systems ==========

        /**
         * Force integration system: converts forces to acceleration (a = F/m).
         */
        private EngineSystem createForceIntegrationSystem() {
            return () -> {
                EntityComponentStore store = context.getEntityComponentStore();
                Set<Long> entities = store.getEntitiesWithComponents(List.of(FLAG, MASS, FORCE_X));

                for (long entity : entities) {
                    float mass = store.getComponent(entity, MASS);
                    if (mass <= 0) mass = 1.0f; // Prevent division by zero

                    float forceX = store.getComponent(entity, FORCE_X);
                    float forceY = store.getComponent(entity, FORCE_Y);
                    float forceZ = store.getComponent(entity, FORCE_Z);

                    // a = F / m
                    float accelX = forceX / mass;
                    float accelY = forceY / mass;
                    float accelZ = forceZ / mass;

                    store.attachComponents(entity, ACCELERATION_COMPONENTS,
                            new float[]{accelX, accelY, accelZ});

                    // Handle angular: torque / inertia = angular acceleration
                    float torque = store.getComponent(entity, TORQUE);
                    float inertia = store.getComponent(entity, INERTIA);
                    if (inertia <= 0) inertia = 1.0f;

                    float angularAccel = torque / inertia;
                    float angularVel = store.getComponent(entity, ANGULAR_VELOCITY);
                    angularVel += angularAccel * DT;
                    store.attachComponent(entity, ANGULAR_VELOCITY, angularVel);

                    log.trace("Entity {} force=({},{},{}) -> accel=({},{},{})",
                            entity, forceX, forceY, forceZ, accelX, accelY, accelZ);
                }
            };
        }

        /**
         * Physics integration system: velocity += acceleration, position += velocity.
         */
        private EngineSystem createPhysicsSystem() {
            return () -> {
                EntityComponentStore store = context.getEntityComponentStore();
                Set<Long> entities = store.getEntitiesWithComponents(List.of(FLAG));

                for (long entity : entities) {
                    // Get current state
                    float posX = store.getComponent(entity, POSITION_X);
                    float posY = store.getComponent(entity, POSITION_Y);
                    float posZ = store.getComponent(entity, POSITION_Z);

                    float velX = store.getComponent(entity, VELOCITY_X);
                    float velY = store.getComponent(entity, VELOCITY_Y);
                    float velZ = store.getComponent(entity, VELOCITY_Z);

                    float accelX = store.getComponent(entity, ACCELERATION_X);
                    float accelY = store.getComponent(entity, ACCELERATION_Y);
                    float accelZ = store.getComponent(entity, ACCELERATION_Z);

                    float linearDrag = store.getComponent(entity, LINEAR_DRAG);
                    float angularDrag = store.getComponent(entity, ANGULAR_DRAG);

                    // Integrate velocity: v += a * dt
                    velX += accelX * DT;
                    velY += accelY * DT;
                    velZ += accelZ * DT;

                    // Apply linear drag: v *= (1 - drag)
                    if (linearDrag > 0 && linearDrag < 1) {
                        float dragFactor = 1.0f - linearDrag;
                        velX *= dragFactor;
                        velY *= dragFactor;
                        velZ *= dragFactor;
                    }

                    // Integrate position: p += v * dt
                    posX += velX * DT;
                    posY += velY * DT;
                    posZ += velZ * DT;

                    // Update position and velocity
                    store.attachComponents(entity, POSITION_COMPONENTS,
                            new float[]{posX, posY, posZ});
                    store.attachComponents(entity, VELOCITY_COMPONENTS,
                            new float[]{velX, velY, velZ});

                    // Handle rotation
                    float rotation = store.getComponent(entity, ROTATION);
                    float angularVel = store.getComponent(entity, ANGULAR_VELOCITY);

                    // Apply angular drag
                    if (angularDrag > 0 && angularDrag < 1) {
                        angularVel *= (1.0f - angularDrag);
                        store.attachComponent(entity, ANGULAR_VELOCITY, angularVel);
                    }

                    rotation += angularVel * DT;
                    store.attachComponent(entity, ROTATION, rotation);

                    // Clear forces (they're consumed each tick)
                    store.attachComponents(entity, FORCE_COMPONENTS, new float[]{0, 0, 0});
                    store.attachComponent(entity, TORQUE, 0);

                    log.trace("Entity {} pos=({},{},{}) vel=({},{},{})",
                            entity, posX, posY, posZ, velX, velY, velZ);
                }
            };
        }

        /**
         * Cleanup system for deleted rigid bodies.
         */
        private EngineSystem createCleanupSystem() {
            return () -> {
                EntityComponentStore store = context.getEntityComponentStore();
                for (Long entityId : deleteQueue) {
                    for (BaseComponent c : CORE_COMPONENTS) {
                        store.removeComponent(entityId, c);
                    }
                    store.removeComponent(entityId, FLAG);
                    log.debug("Cleaned up rigid body components for entity {}", entityId);
                }
                deleteQueue.clear();
            };
        }

        // ========== Commands ==========

        /**
         * Attach rigid body components to an entity.
         *
         * <p>Payload:
         * <ul>
         *   <li>entityId (long) - Target entity</li>
         *   <li>positionX/Y/Z (float) - Initial position</li>
         *   <li>velocityX/Y/Z (float) - Initial velocity</li>
         *   <li>mass (float) - Mass (default 1.0)</li>
         *   <li>linearDrag (float) - Linear drag coefficient (0-1, default 0)</li>
         *   <li>angularDrag (float) - Angular drag coefficient (0-1, default 0)</li>
         *   <li>inertia (float) - Moment of inertia (default 1.0)</li>
         * </ul>
         */
        private EngineCommand attachRigidBodyCommand() {
            Map<String, Class<?>> schema = new HashMap<>();
            schema.put("entityId", Long.class);
            schema.put("positionX", Float.class);
            schema.put("positionY", Float.class);
            schema.put("positionZ", Float.class);
            schema.put("velocityX", Float.class);
            schema.put("velocityY", Float.class);
            schema.put("velocityZ", Float.class);
            schema.put("mass", Float.class);
            schema.put("linearDrag", Float.class);
            schema.put("angularDrag", Float.class);
            schema.put("inertia", Float.class);

            return CommandBuilder.newCommand()
                    .withName("attachRigidBody")
                    .withSchema(schema)
                    .withExecution(payload -> {
                        Map<String, Object> data = payload.getPayload();
                        long entityId = extractLong(data, "entityId");
                        if (entityId == 0) {
                            log.warn("attachRigidBody: missing entityId");
                            return;
                        }

                        float posX = extractFloat(data, "positionX", 0);
                        float posY = extractFloat(data, "positionY", 0);
                        float posZ = extractFloat(data, "positionZ", 0);
                        float velX = extractFloat(data, "velocityX", 0);
                        float velY = extractFloat(data, "velocityY", 0);
                        float velZ = extractFloat(data, "velocityZ", 0);
                        float mass = extractFloat(data, "mass", 1.0f);
                        float linearDrag = extractFloat(data, "linearDrag", 0);
                        float angularDrag = extractFloat(data, "angularDrag", 0);
                        float inertia = extractFloat(data, "inertia", 1.0f);

                        EntityComponentStore store = context.getEntityComponentStore();

                        // Attach all components
                        store.attachComponents(entityId, POSITION_COMPONENTS, new float[]{posX, posY, posZ});
                        store.attachComponents(entityId, VELOCITY_COMPONENTS, new float[]{velX, velY, velZ});
                        store.attachComponents(entityId, ACCELERATION_COMPONENTS, new float[]{0, 0, 0});
                        store.attachComponents(entityId, FORCE_COMPONENTS, new float[]{0, 0, 0});
                        store.attachComponent(entityId, MASS, mass);
                        store.attachComponent(entityId, LINEAR_DRAG, linearDrag);
                        store.attachComponent(entityId, ANGULAR_DRAG, angularDrag);
                        store.attachComponent(entityId, ANGULAR_VELOCITY, 0);
                        store.attachComponent(entityId, ROTATION, 0);
                        store.attachComponent(entityId, TORQUE, 0);
                        store.attachComponent(entityId, INERTIA, inertia);
                        store.attachComponent(entityId, FLAG, 1.0f);

                        log.info("Attached rigid body to entity {}: pos=({},{},{}), vel=({},{},{}), mass={}",
                                entityId, posX, posY, posZ, velX, velY, velZ, mass);
                    })
                    .build();
        }

        /**
         * Apply a force to an entity (accumulated until next tick).
         *
         * <p>Payload: entityId, forceX, forceY, forceZ
         */
        private EngineCommand applyForceCommand() {
            return CommandBuilder.newCommand()
                    .withName("applyForce")
                    .withSchema(Map.of(
                            "entityId", Long.class,
                            "forceX", Float.class,
                            "forceY", Float.class,
                            "forceZ", Float.class
                    ))
                    .withExecution(payload -> {
                        Map<String, Object> data = payload.getPayload();
                        long entityId = extractLong(data, "entityId");
                        float forceX = extractFloat(data, "forceX", 0);
                        float forceY = extractFloat(data, "forceY", 0);
                        float forceZ = extractFloat(data, "forceZ", 0);

                        EntityComponentStore store = context.getEntityComponentStore();

                        // Accumulate forces
                        float currentFX = store.getComponent(entityId, FORCE_X);
                        float currentFY = store.getComponent(entityId, FORCE_Y);
                        float currentFZ = store.getComponent(entityId, FORCE_Z);

                        store.attachComponents(entityId, FORCE_COMPONENTS,
                                new float[]{currentFX + forceX, currentFY + forceY, currentFZ + forceZ});

                        log.debug("Applied force ({},{},{}) to entity {}", forceX, forceY, forceZ, entityId);
                    })
                    .build();
        }

        /**
         * Apply an impulse (instant velocity change).
         *
         * <p>Payload: entityId, impulseX, impulseY, impulseZ
         */
        private EngineCommand applyImpulseCommand() {
            return CommandBuilder.newCommand()
                    .withName("applyImpulse")
                    .withSchema(Map.of(
                            "entityId", Long.class,
                            "impulseX", Float.class,
                            "impulseY", Float.class,
                            "impulseZ", Float.class
                    ))
                    .withExecution(payload -> {
                        Map<String, Object> data = payload.getPayload();
                        long entityId = extractLong(data, "entityId");
                        float impulseX = extractFloat(data, "impulseX", 0);
                        float impulseY = extractFloat(data, "impulseY", 0);
                        float impulseZ = extractFloat(data, "impulseZ", 0);

                        EntityComponentStore store = context.getEntityComponentStore();
                        float mass = store.getComponent(entityId, MASS);
                        if (mass <= 0) mass = 1.0f;

                        // Impulse = mass * delta_velocity, so delta_v = impulse / mass
                        float dvX = impulseX / mass;
                        float dvY = impulseY / mass;
                        float dvZ = impulseZ / mass;

                        float velX = store.getComponent(entityId, VELOCITY_X) + dvX;
                        float velY = store.getComponent(entityId, VELOCITY_Y) + dvY;
                        float velZ = store.getComponent(entityId, VELOCITY_Z) + dvZ;

                        store.attachComponents(entityId, VELOCITY_COMPONENTS,
                                new float[]{velX, velY, velZ});

                        log.debug("Applied impulse ({},{},{}) to entity {}, new vel=({},{},{})",
                                impulseX, impulseY, impulseZ, entityId, velX, velY, velZ);
                    })
                    .build();
        }

        /**
         * Set velocity directly.
         *
         * <p>Payload: entityId, velocityX, velocityY, velocityZ
         */
        private EngineCommand setVelocityCommand() {
            return CommandBuilder.newCommand()
                    .withName("setVelocity")
                    .withSchema(Map.of(
                            "entityId", Long.class,
                            "velocityX", Float.class,
                            "velocityY", Float.class,
                            "velocityZ", Float.class
                    ))
                    .withExecution(payload -> {
                        Map<String, Object> data = payload.getPayload();
                        long entityId = extractLong(data, "entityId");
                        float velX = extractFloat(data, "velocityX", 0);
                        float velY = extractFloat(data, "velocityY", 0);
                        float velZ = extractFloat(data, "velocityZ", 0);

                        EntityComponentStore store = context.getEntityComponentStore();
                        store.attachComponents(entityId, VELOCITY_COMPONENTS,
                                new float[]{velX, velY, velZ});

                        log.debug("Set velocity ({},{},{}) for entity {}", velX, velY, velZ, entityId);
                    })
                    .build();
        }

        /**
         * Set position directly (teleport).
         *
         * <p>Payload: entityId, positionX, positionY, positionZ
         */
        private EngineCommand setPositionCommand() {
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
                        float posX = extractFloat(data, "positionX", 0);
                        float posY = extractFloat(data, "positionY", 0);
                        float posZ = extractFloat(data, "positionZ", 0);

                        EntityComponentStore store = context.getEntityComponentStore();
                        store.attachComponents(entityId, POSITION_COMPONENTS,
                                new float[]{posX, posY, posZ});

                        log.debug("Set position ({},{},{}) for entity {}", posX, posY, posZ, entityId);
                    })
                    .build();
        }

        /**
         * Apply torque (rotational force).
         *
         * <p>Payload: entityId, torque
         */
        private EngineCommand applyTorqueCommand() {
            return CommandBuilder.newCommand()
                    .withName("applyTorque")
                    .withSchema(Map.of(
                            "entityId", Long.class,
                            "torque", Float.class
                    ))
                    .withExecution(payload -> {
                        Map<String, Object> data = payload.getPayload();
                        long entityId = extractLong(data, "entityId");
                        float torque = extractFloat(data, "torque", 0);

                        EntityComponentStore store = context.getEntityComponentStore();
                        float currentTorque = store.getComponent(entityId, TORQUE);
                        store.attachComponent(entityId, TORQUE, currentTorque + torque);

                        log.debug("Applied torque {} to entity {}", torque, entityId);
                    })
                    .build();
        }

        /**
         * Delete rigid body components from entity.
         *
         * <p>Payload: entityId
         */
        private EngineCommand deleteRigidBodyCommand() {
            return CommandBuilder.newCommand()
                    .withName("deleteRigidBody")
                    .withSchema(Map.of("entityId", Long.class))
                    .withExecution(payload -> {
                        Map<String, Object> data = payload.getPayload();
                        long entityId = extractLong(data, "entityId");
                        deleteQueue.add(entityId);
                        log.debug("Queued rigid body deletion for entity {}", entityId);
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
    }
}
