package ca.samanthaireland.engine.ext.modules;

import ca.samanthaireland.engine.core.store.BaseComponent;
import ca.samanthaireland.engine.core.store.PermissionComponent;
import ca.samanthaireland.engine.core.store.PermissionLevel;
import ca.samanthaireland.engine.ext.module.EngineModule;
import ca.samanthaireland.engine.ext.module.ModuleContext;
import ca.samanthaireland.engine.ext.module.ModuleFactory;
import ca.samanthaireland.engine.util.IdGeneratorV2;

import java.util.List;

/**
 * Module factory for the RigidBody physics module.
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
public class RigidBodyModuleFactory implements ModuleFactory {

    // Velocity components
    public static final BaseComponent VELOCITY_X = new PermissionComponent(
            IdGeneratorV2.newId(), "VELOCITY_X", PermissionLevel.READ);
    public static final BaseComponent VELOCITY_Y = new PermissionComponent(
            IdGeneratorV2.newId(), "VELOCITY_Y", PermissionLevel.READ);
    public static final BaseComponent VELOCITY_Z = new PermissionComponent(
            IdGeneratorV2.newId(), "VELOCITY_Z", PermissionLevel.READ);

    // Acceleration components (accumulated from forces)
    public static final BaseComponent ACCELERATION_X = new PermissionComponent(
            IdGeneratorV2.newId(), "ACCELERATION_X", PermissionLevel.READ);
    public static final BaseComponent ACCELERATION_Y = new PermissionComponent(
            IdGeneratorV2.newId(), "ACCELERATION_Y", PermissionLevel.READ);
    public static final BaseComponent ACCELERATION_Z = new PermissionComponent(
            IdGeneratorV2.newId(), "ACCELERATION_Z", PermissionLevel.READ);

    // Force accumulator (cleared each tick after applying)
    public static final BaseComponent FORCE_X = new PermissionComponent(
            IdGeneratorV2.newId(), "FORCE_X", PermissionLevel.READ);
    public static final BaseComponent FORCE_Y = new PermissionComponent(
            IdGeneratorV2.newId(), "FORCE_Y", PermissionLevel.READ);
    public static final BaseComponent FORCE_Z = new PermissionComponent(
            IdGeneratorV2.newId(), "FORCE_Z", PermissionLevel.READ);

    // Mass (for F = ma calculations)
    public static final BaseComponent MASS = new PermissionComponent(
            IdGeneratorV2.newId(), "MASS", PermissionLevel.READ);

    // Angular components (2D rotation around Z axis)
    public static final BaseComponent ANGULAR_VELOCITY = new PermissionComponent(
            IdGeneratorV2.newId(), "ANGULAR_VELOCITY", PermissionLevel.READ);
    public static final BaseComponent ROTATION = new PermissionComponent(
            IdGeneratorV2.newId(), "ROTATION", PermissionLevel.READ);
    public static final BaseComponent TORQUE = new PermissionComponent(
            IdGeneratorV2.newId(), "TORQUE", PermissionLevel.READ);
    public static final BaseComponent INERTIA = new PermissionComponent(
            IdGeneratorV2.newId(), "INERTIA", PermissionLevel.READ);

    // Damping/drag coefficients
    public static final BaseComponent LINEAR_DRAG = new PermissionComponent(
            IdGeneratorV2.newId(), "LINEAR_DRAG", PermissionLevel.READ);
    public static final BaseComponent ANGULAR_DRAG = new PermissionComponent(
            IdGeneratorV2.newId(), "ANGULAR_DRAG", PermissionLevel.READ);

    public static final BaseComponent FLAG = new PermissionComponent(
            IdGeneratorV2.newId(), "rigidBody", PermissionLevel.READ);

    // Component groups
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
}
