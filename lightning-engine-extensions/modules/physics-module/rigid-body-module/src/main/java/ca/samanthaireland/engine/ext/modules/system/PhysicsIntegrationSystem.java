package ca.samanthaireland.engine.ext.modules.system;

import ca.samanthaireland.engine.core.benchmark.Benchmark;
import ca.samanthaireland.engine.core.store.EntityComponentStore;
import ca.samanthaireland.engine.core.system.EngineSystem;
import ca.samanthaireland.engine.ext.module.ModuleContext;
import ca.samanthaireland.engine.ext.modules.GridMapExports;
import ca.samanthaireland.engine.ext.modules.domain.Position;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;

import static ca.samanthaireland.engine.ext.modules.RigidBodyModuleFactory.*;

/**
 * Physics integration system: velocity += acceleration, position += velocity.
 *
 * <p>This system integrates velocity from acceleration and position from velocity,
 * applying linear and angular drag. Position updates are delegated to GridMapModule
 * through its exports to maintain proper module boundaries.
 *
 * <p>Optimized with batch component reads/writes using pre-computed component ID arrays.
 *
 * <p>Physics timestep: Assumes 1 tick = 1/60 second
 */
@Slf4j
public class PhysicsIntegrationSystem implements EngineSystem {

    private static final float DT = 1.0f / 60.0f;

    // Physics read: velX, velY, velZ, accelX, accelY, accelZ, linearDrag, angularDrag, rotation, angularVel
    private static final long[] PHYSICS_READ_IDS = {
            VELOCITY_X.getId(), VELOCITY_Y.getId(), VELOCITY_Z.getId(),
            ACCELERATION_X.getId(), ACCELERATION_Y.getId(), ACCELERATION_Z.getId(),
            LINEAR_DRAG.getId(), ANGULAR_DRAG.getId(),
            ROTATION.getId(), ANGULAR_VELOCITY.getId()
    };

    // Physics write: velX, velY, velZ, rotation, angularVel, forceX, forceY, forceZ, torque
    private static final long[] PHYSICS_WRITE_IDS = {
            VELOCITY_X.getId(), VELOCITY_Y.getId(), VELOCITY_Z.getId(),
            ROTATION.getId(), ANGULAR_VELOCITY.getId(),
            FORCE_X.getId(), FORCE_Y.getId(), FORCE_Z.getId(),
            TORQUE.getId()
    };

    private final ModuleContext context;
    private final Benchmark benchmark;
    private final float[] readBuf = new float[PHYSICS_READ_IDS.length];
    private final float[] writeBuf = new float[PHYSICS_WRITE_IDS.length];
    private GridMapExports gridMapExports;

    /**
     * Create a physics integration system.
     *
     * @param context the module context for accessing the entity component store and benchmark
     */
    public PhysicsIntegrationSystem(ModuleContext context) {
        this.context = context;
        this.benchmark = context.getBenchmark();
    }

    @Override
    public void updateEntities() {
        try (var scope = benchmark.scope("velocity-position-integration")) {
            EntityComponentStore store = context.getEntityComponentStore();
            GridMapExports exports = getGridMapExports();
            Set<Long> entities = store.getEntitiesWithComponents(List.of(FLAG));

            for (long entity : entities) {
                // Batch read all physics components using pre-computed IDs
                try (var scope2 = benchmark.scope("read-ids")) {
                    store.getComponents(entity, PHYSICS_READ_IDS, readBuf);
                }

                float velX = readBuf[0];
                float velY = readBuf[1];
                float velZ = readBuf[2];
                float accelX = readBuf[3];
                float accelY = readBuf[4];
                float accelZ = readBuf[5];
                float linearDrag = readBuf[6];
                float angularDrag = readBuf[7];
                float rotation = readBuf[8];
                float angularVel = readBuf[9];

                // Get current position from GridMap exports
                Position currentPos;
                try (var scope2 = benchmark.scope("get export")) {
                    currentPos = exports.getPosition(entity).orElse(Position.origin());
                }

                float posX = currentPos.x();
                float posY = currentPos.y();
                float posZ = currentPos.z();

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

                // Apply angular drag
                if (angularDrag > 0 && angularDrag < 1) {
                    angularVel *= (1.0f - angularDrag);
                }

                // Integrate rotation
                rotation += angularVel * DT;

                // Batch write all output components
                // Order: velX, velY, velZ, rotation, angularVel, forceX, forceY, forceZ, torque
                writeBuf[0] = velX;
                writeBuf[1] = velY;
                writeBuf[2] = velZ;
                writeBuf[3] = rotation;
                writeBuf[4] = angularVel;
                writeBuf[5] = 0; // Clear force X
                writeBuf[6] = 0; // Clear force Y
                writeBuf[7] = 0; // Clear force Z
                writeBuf[8] = 0; // Clear torque

                try (var scope4 = benchmark.scope("write physics integration")) {
                    store.attachComponents(entity, PHYSICS_WRITE_IDS, writeBuf);
                }

                log.trace("Entity {} pos=({},{},{}) vel=({},{},{})",
                        entity, posX, posY, posZ, velX, velY, velZ);
            }
        }
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
}
