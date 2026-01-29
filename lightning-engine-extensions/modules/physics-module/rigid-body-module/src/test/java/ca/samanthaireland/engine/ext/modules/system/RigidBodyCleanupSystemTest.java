package ca.samanthaireland.engine.ext.modules.system;

import ca.samanthaireland.engine.core.benchmark.Benchmark;
import ca.samanthaireland.engine.ext.module.ModuleContext;
import ca.samanthaireland.engine.ext.modules.domain.service.RigidBodyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RigidBodyCleanupSystemTest {

    @Mock
    private RigidBodyService rigidBodyService;

    @Mock
    private ModuleContext context;

    @Mock
    private Benchmark benchmark;

    @Mock
    private Benchmark.BenchmarkScope benchmarkScope;

    @BeforeEach
    void setUp() {
        when(context.getBenchmark()).thenReturn(benchmark);
        when(benchmark.scope("cleanup")).thenReturn(benchmarkScope);
    }

    @Test
    void updateEntities_deletesAllQueuedEntities() {
        List<Long> deleteQueue = new ArrayList<>(List.of(1L, 2L, 3L));
        RigidBodyCleanupSystem system = new RigidBodyCleanupSystem(rigidBodyService, deleteQueue, context);

        system.updateEntities();

        verify(rigidBodyService).delete(1L);
        verify(rigidBodyService).delete(2L);
        verify(rigidBodyService).delete(3L);
        assertThat(deleteQueue).isEmpty();
    }

    @Test
    void updateEntities_withEmptyQueue_doesNothing() {
        List<Long> deleteQueue = new ArrayList<>();
        RigidBodyCleanupSystem system = new RigidBodyCleanupSystem(rigidBodyService, deleteQueue, context);

        system.updateEntities();

        verifyNoInteractions(rigidBodyService);
    }

    @Test
    void updateEntities_clearsQueueAfterProcessing() {
        List<Long> deleteQueue = new ArrayList<>(List.of(1L, 2L));
        RigidBodyCleanupSystem system = new RigidBodyCleanupSystem(rigidBodyService, deleteQueue, context);

        system.updateEntities();

        assertThat(deleteQueue).isEmpty();
    }

    @Test
    void updateEntities_deletesInOrder() {
        List<Long> deleteQueue = new ArrayList<>(List.of(5L, 3L, 7L, 1L));
        RigidBodyCleanupSystem system = new RigidBodyCleanupSystem(rigidBodyService, deleteQueue, context);

        system.updateEntities();

        // Verify deletion order matches queue order
        var inOrder = inOrder(rigidBodyService);
        inOrder.verify(rigidBodyService).delete(5L);
        inOrder.verify(rigidBodyService).delete(3L);
        inOrder.verify(rigidBodyService).delete(7L);
        inOrder.verify(rigidBodyService).delete(1L);
    }

    @Test
    void updateEntities_usesBenchmarkScope() {
        List<Long> deleteQueue = new ArrayList<>();
        RigidBodyCleanupSystem system = new RigidBodyCleanupSystem(rigidBodyService, deleteQueue, context);

        system.updateEntities();

        verify(benchmark).scope("cleanup");
        verify(benchmarkScope).close();
    }
}
