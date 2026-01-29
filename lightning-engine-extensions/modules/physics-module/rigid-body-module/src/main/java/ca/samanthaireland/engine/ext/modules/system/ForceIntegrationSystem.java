package ca.samanthaireland.engine.ext.modules.system;

import ca.samanthaireland.engine.core.benchmark.Benchmark;
import ca.samanthaireland.engine.core.store.EntityComponentStore;
import ca.samanthaireland.engine.core.system.EngineSystem;
import ca.samanthaireland.engine.ext.module.ModuleContext;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;

import static ca.samanthaireland.engine.ext.modules.RigidBodyModuleFactory.*;

/**
 * Force integration system: converts forces to acceleration (a = F/m).
 *
 * <p>This system processes all entities with rigid bodies and calculates
 * their acceleration based on applied forces using Newton's second law (F = ma).
 * It also handles angular acceleration from torque.
 *
 * <p>Optimized with batch component reads/writes using pre-computed component ID arrays.
 *
 * <p>Physics timestep: Assumes 1 tick = 1/60 second
 */
@Slf4j
public class ForceIntegrationSystem implements EngineSystem {

    private static final float DT = 1.0f / 60.0f;

    // Pre-computed component ID arrays for batch operations (avoid allocation during tick)
    // Force integration read: mass, forceX, forceY, forceZ, torque, inertia, angularVel
    private static final long[] FORCE_READ_IDS = {
            MASS.getId(), FORCE_X.getId(), FORCE_Y.getId(), FORCE_Z.getId(),
            TORQUE.getId(), INERTIA.getId(), ANGULAR_VELOCITY.getId()
    };

    // Force integration write: accelX, accelY, accelZ, angularVel
    private static final long[] FORCE_WRITE_IDS = {
            ACCELERATION_X.getId(), ACCELERATION_Y.getId(), ACCELERATION_Z.getId(), ANGULAR_VELOCITY.getId()
    };

    private final ModuleContext context;
    private final Benchmark benchmark;
    private final float[] readBuf = new float[FORCE_READ_IDS.length];
    private final float[] writeBuf = new float[FORCE_WRITE_IDS.length];

    /**
     * Create a force integration system.
     *
     * @param context the module context for accessing the entity component store and benchmark
     */
    public ForceIntegrationSystem(ModuleContext context) {
        this.context = context;
        this.benchmark = context.getBenchmark();
    }

    @Override
    public void updateEntities() {
        try (var scope = benchmark.scope("force-integration")) {
            EntityComponentStore store = context.getEntityComponentStore();
            Set<Long> entities = store.getEntitiesWithComponents(List.of(FLAG, MASS, FORCE_X));

            for (long entity : entities) {
                // Batch read all force components using pre-computed IDs
                store.getComponents(entity, FORCE_READ_IDS, readBuf);

                float mass = readBuf[0];
                float forceX = readBuf[1];
                float forceY = readBuf[2];
                float forceZ = readBuf[3];
                float torque = readBuf[4];
                float inertia = readBuf[5];
                float angularVel = readBuf[6];

                // Prevent division by zero
                if (mass <= 0) mass = 1.0f;
                if (inertia <= 0) inertia = 1.0f;

                // a = F / m
                float accelX = forceX / mass;
                float accelY = forceY / mass;
                float accelZ = forceZ / mass;

                // Handle angular: torque / inertia = angular acceleration
                float angularAccel = torque / inertia;
                angularVel += angularAccel * DT;

                // Batch write acceleration and angular velocity
                writeBuf[0] = accelX;
                writeBuf[1] = accelY;
                writeBuf[2] = accelZ;
                writeBuf[3] = angularVel;

                store.attachComponents(entity, FORCE_WRITE_IDS, writeBuf);

                log.trace("Entity {} force=({},{},{}) -> accel=({},{},{})",
                        entity, forceX, forceY, forceZ, accelX, accelY, accelZ);
            }
        }
    }
}
