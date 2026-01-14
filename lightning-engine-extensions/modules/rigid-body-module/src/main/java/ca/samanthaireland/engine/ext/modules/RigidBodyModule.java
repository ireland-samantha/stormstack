package ca.samanthaireland.engine.ext.modules;

import ca.samanthaireland.engine.core.command.EngineCommand;
import ca.samanthaireland.engine.core.store.BaseComponent;
import ca.samanthaireland.engine.core.store.EntityComponentStore;
import ca.samanthaireland.engine.core.system.EngineSystem;
import ca.samanthaireland.engine.ext.module.EngineModule;
import ca.samanthaireland.engine.ext.module.ModuleContext;
import ca.samanthaireland.engine.ext.modules.domain.Position;
import ca.samanthaireland.engine.ext.modules.domain.repository.RigidBodyRepository;
import ca.samanthaireland.engine.ext.modules.domain.service.PhysicsService;
import ca.samanthaireland.engine.ext.modules.domain.service.RigidBodyService;
import ca.samanthaireland.engine.ext.modules.ecs.command.*;
import ca.samanthaireland.engine.ext.modules.ecs.repository.EcsRigidBodyRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static ca.samanthaireland.engine.ext.modules.RigidBodyModuleFactory.*;

/**
 * RigidBody module implementation.
 *
 * <p>Provides physics simulation with:
 * <ul>
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
 * <p>Note: Position operations use GridMapExports (position components are in GridMapModule).
 */
@Slf4j
public class RigidBodyModule implements EngineModule {
    private final ModuleContext context;
    private final RigidBodyService rigidBodyService;
    private final PhysicsService physicsService;
    private final List<Long> deleteQueue = new ArrayList<>();

    // Lazily resolved reference to GridMapModule's exports for position management
    private GridMapExports gridMapExports;

    // Physics timestep (assuming 1 tick = 1/60 second)
    private static final float DT = 1.0f / 60.0f;

    public RigidBodyModule(ModuleContext context) {
        this.context = context;

        RigidBodyRepository rigidBodyRepository = new EcsRigidBodyRepository(context);

        this.rigidBodyService = new RigidBodyService(rigidBodyRepository);
        this.physicsService = new PhysicsService(rigidBodyRepository);
    }

    /**
     * Gets the GridMapExports, resolving it lazily on first access.
     * This is necessary because GridMapModule may not be initialized when RigidBodyModule is created.
     */
    private GridMapExports getGridMapExports() {
        if (gridMapExports == null) {
            gridMapExports = context.getModuleExports(GridMapExports.class);
            if (gridMapExports == null) {
                throw new IllegalStateException(
                        "GridMapExports not available. Ensure GridMapModule is loaded before RigidBodyModule.");
            }
        }
        return gridMapExports;
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
                AttachRigidBodyCommand.create(rigidBodyService),
                ApplyForceCommand.create(physicsService),
                ApplyImpulseCommand.create(physicsService),
                SetVelocityCommand.create(physicsService),
                SetPositionCommand.create(physicsService),
                ApplyTorqueCommand.create(physicsService),
                DeleteRigidBodyCommand.create(rigidBodyService, deleteQueue)
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
     * Uses GridMapExports to update positions through the proper module boundary.
     */
    private EngineSystem createPhysicsSystem() {
        return () -> {
            EntityComponentStore store = context.getEntityComponentStore();
            GridMapExports exports = getGridMapExports();
            Set<Long> entities = store.getEntitiesWithComponents(List.of(FLAG));

            for (long entity : entities) {
                // Get current position from GridMap exports
                Position currentPos = exports.getPosition(entity).orElse(Position.origin());
                float posX = currentPos.x();
                float posY = currentPos.y();
                float posZ = currentPos.z();

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

                // Update position through GridMapModule exports
                exports.setPosition(entity, posX, posY, posZ);

                // Update velocity (RigidBodyModule's own components)
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
            for (Long entityId : deleteQueue) {
                rigidBodyService.delete(entityId);
                log.debug("Cleaned up rigid body components for entity {}", entityId);
            }
            deleteQueue.clear();
        };
    }
}
