package ca.samanthaireland.engine.ext.modules.system;

import ca.samanthaireland.engine.core.benchmark.Benchmark;
import ca.samanthaireland.engine.core.system.EngineSystem;
import ca.samanthaireland.engine.ext.module.ModuleContext;
import ca.samanthaireland.engine.ext.modules.domain.service.RigidBodyService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Cleanup system for deleted rigid bodies.
 *
 * <p>This system processes a queue of entity IDs that have been marked for
 * deletion and removes their rigid body components. The queue is populated
 * by the DeleteRigidBodyCommand.
 *
 * <p>Follows the command-system pattern where commands queue work and
 * systems execute the work during the tick.
 */
@Slf4j
public class RigidBodyCleanupSystem implements EngineSystem {

    private final RigidBodyService rigidBodyService;
    private final List<Long> deleteQueue;
    private final Benchmark benchmark;

    /**
     * Create a rigid body cleanup system.
     *
     * @param rigidBodyService the service for deleting rigid body components
     * @param deleteQueue the shared queue of entity IDs to delete (populated by DeleteRigidBodyCommand)
     * @param context the module context for accessing the benchmark
     */
    public RigidBodyCleanupSystem(RigidBodyService rigidBodyService, List<Long> deleteQueue, ModuleContext context) {
        this.rigidBodyService = rigidBodyService;
        this.deleteQueue = deleteQueue;
        this.benchmark = context.getBenchmark();
    }

    @Override
    public void updateEntities() {
        try (var scope = benchmark.scope("cleanup")) {
            for (Long entityId : deleteQueue) {
                rigidBodyService.delete(entityId);
                log.debug("Cleaned up rigid body components for entity {}", entityId);
            }
            deleteQueue.clear();
        }
    }
}
