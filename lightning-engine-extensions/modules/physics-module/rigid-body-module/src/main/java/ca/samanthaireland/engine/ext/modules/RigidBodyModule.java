package ca.samanthaireland.engine.ext.modules;

import ca.samanthaireland.engine.core.command.EngineCommand;
import ca.samanthaireland.engine.core.store.BaseComponent;
import ca.samanthaireland.engine.core.system.EngineSystem;
import ca.samanthaireland.engine.ext.module.EngineModule;
import ca.samanthaireland.engine.ext.module.ModuleContext;
import ca.samanthaireland.engine.ext.modules.domain.repository.RigidBodyRepository;
import ca.samanthaireland.engine.ext.modules.domain.service.PhysicsService;
import ca.samanthaireland.engine.ext.modules.domain.service.RigidBodyService;
import ca.samanthaireland.engine.ext.modules.ecs.command.*;
import ca.samanthaireland.engine.ext.modules.ecs.repository.EcsRigidBodyRepository;
import ca.samanthaireland.engine.ext.modules.system.ForceIntegrationSystem;
import ca.samanthaireland.engine.ext.modules.system.PhysicsIntegrationSystem;
import ca.samanthaireland.engine.ext.modules.system.RigidBodyCleanupSystem;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

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
                new ForceIntegrationSystem(context),
                new PhysicsIntegrationSystem(context),
                new RigidBodyCleanupSystem(rigidBodyService, deleteQueue, context)
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
}
