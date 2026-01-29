package ca.samanthaireland.engine.ext.modules.system;

import ca.samanthaireland.engine.core.benchmark.Benchmark;
import ca.samanthaireland.engine.core.store.EntityComponentStore;
import ca.samanthaireland.engine.ext.module.ModuleContext;
import ca.samanthaireland.engine.ext.modules.GridMapExports;
import ca.samanthaireland.engine.ext.modules.domain.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PhysicsIntegrationSystemTest {

    @Mock
    private ModuleContext context;

    @Mock
    private EntityComponentStore store;

    @Mock
    private GridMapExports gridMapExports;

    @Mock
    private Benchmark benchmark;

    @Mock
    private Benchmark.BenchmarkScope benchmarkScope;

    @BeforeEach
    void setUp() {
        when(context.getBenchmark()).thenReturn(benchmark);
        when(benchmark.scope("velocity-position-integration")).thenReturn(benchmarkScope);
    }

    @Test
    void updateEntities_integratesVelocityFromAcceleration() {
        when(context.getEntityComponentStore()).thenReturn(store);
        when(context.getModuleExports(GridMapExports.class)).thenReturn(gridMapExports);
        when(store.getEntitiesWithComponents(anyList())).thenReturn(Set.of(1L));
        when(gridMapExports.getPosition(1L)).thenReturn(Optional.of(new Position(0, 0, 0)));

        // Setup: velX=5.0, accelX=2.0
        doAnswer(invocation -> {
            float[] buffer = invocation.getArgument(2);
            buffer[0] = 5.0f;  // velX
            buffer[1] = 0.0f;  // velY
            buffer[2] = 0.0f;  // velZ
            buffer[3] = 2.0f;  // accelX
            buffer[4] = 0.0f;  // accelY
            buffer[5] = 0.0f;  // accelZ
            buffer[6] = 0.0f;  // linearDrag
            buffer[7] = 0.0f;  // angularDrag
            buffer[8] = 0.0f;  // rotation
            buffer[9] = 0.0f;  // angularVel
            return null;
        }).when(store).getComponents(eq(1L), any(long[].class), any(float[].class));

        PhysicsIntegrationSystem system = new PhysicsIntegrationSystem(context);
        system.updateEntities();

        // New velocity = old + (accel * dt) = 5.0 + (2.0 * 1/60)
        float expectedVelX = 5.0f + (2.0f * (1.0f / 60.0f));

        verify(store).attachComponents(eq(1L), any(long[].class), argThat(buffer ->
            Math.abs(buffer[0] - expectedVelX) < 0.001f
        ));
    }

    @Test
    void updateEntities_integratesPositionFromVelocity() {
        when(context.getEntityComponentStore()).thenReturn(store);
        when(context.getModuleExports(GridMapExports.class)).thenReturn(gridMapExports);
        when(store.getEntitiesWithComponents(anyList())).thenReturn(Set.of(1L));
        when(gridMapExports.getPosition(1L)).thenReturn(Optional.of(new Position(100, 200, 0)));

        // Setup: velX=10.0, velY=5.0
        doAnswer(invocation -> {
            float[] buffer = invocation.getArgument(2);
            buffer[0] = 10.0f; // velX
            buffer[1] = 5.0f;  // velY
            buffer[2] = 0.0f;  // velZ
            buffer[3] = 0.0f;  // accelX
            buffer[4] = 0.0f;  // accelY
            buffer[5] = 0.0f;  // accelZ
            buffer[6] = 0.0f;  // linearDrag
            buffer[7] = 0.0f;  // angularDrag
            buffer[8] = 0.0f;  // rotation
            buffer[9] = 0.0f;  // angularVel
            return null;
        }).when(store).getComponents(eq(1L), any(long[].class), any(float[].class));

        PhysicsIntegrationSystem system = new PhysicsIntegrationSystem(context);
        system.updateEntities();

        // New position = old + (vel * dt)
        float expectedX = 100.0f + (10.0f * (1.0f / 60.0f));
        float expectedY = 200.0f + (5.0f * (1.0f / 60.0f));

        verify(gridMapExports).setPosition(eq(1L),
            floatThat(x -> Math.abs(x - expectedX) < 0.001f),
            floatThat(y -> Math.abs(y - expectedY) < 0.001f),
            eq(0.0f)
        );
    }

    @Test
    void updateEntities_appliesLinearDrag() {
        when(context.getEntityComponentStore()).thenReturn(store);
        when(context.getModuleExports(GridMapExports.class)).thenReturn(gridMapExports);
        when(store.getEntitiesWithComponents(anyList())).thenReturn(Set.of(1L));
        when(gridMapExports.getPosition(1L)).thenReturn(Optional.of(new Position(0, 0, 0)));

        // Setup: velX=10.0, linearDrag=0.1 (10% drag)
        doAnswer(invocation -> {
            float[] buffer = invocation.getArgument(2);
            buffer[0] = 10.0f; // velX
            buffer[1] = 0.0f;  // velY
            buffer[2] = 0.0f;  // velZ
            buffer[3] = 0.0f;  // accelX
            buffer[4] = 0.0f;  // accelY
            buffer[5] = 0.0f;  // accelZ
            buffer[6] = 0.1f;  // linearDrag
            buffer[7] = 0.0f;  // angularDrag
            buffer[8] = 0.0f;  // rotation
            buffer[9] = 0.0f;  // angularVel
            return null;
        }).when(store).getComponents(eq(1L), any(long[].class), any(float[].class));

        PhysicsIntegrationSystem system = new PhysicsIntegrationSystem(context);
        system.updateEntities();

        // Velocity after drag = vel * (1 - drag) = 10.0 * 0.9 = 9.0
        verify(store).attachComponents(eq(1L), any(long[].class), argThat(buffer ->
            Math.abs(buffer[0] - 9.0f) < 0.001f
        ));
    }

    @Test
    void updateEntities_clearsForces() {
        when(context.getEntityComponentStore()).thenReturn(store);
        when(context.getModuleExports(GridMapExports.class)).thenReturn(gridMapExports);
        when(store.getEntitiesWithComponents(anyList())).thenReturn(Set.of(1L));
        when(gridMapExports.getPosition(1L)).thenReturn(Optional.of(new Position(0, 0, 0)));

        doAnswer(invocation -> {
            float[] buffer = invocation.getArgument(2);
            for (int i = 0; i < buffer.length; i++) {
                buffer[i] = 0.0f;
            }
            return null;
        }).when(store).getComponents(eq(1L), any(long[].class), any(float[].class));

        PhysicsIntegrationSystem system = new PhysicsIntegrationSystem(context);
        system.updateEntities();

        // Verify forces are cleared (indices 5-8 in write buffer)
        verify(store).attachComponents(eq(1L), any(long[].class), argThat(buffer ->
            buffer[5] == 0.0f && buffer[6] == 0.0f && buffer[7] == 0.0f && buffer[8] == 0.0f
        ));
    }

    @Test
    void updateEntities_withNoEntities_doesNothing() {
        when(context.getEntityComponentStore()).thenReturn(store);
        when(context.getModuleExports(GridMapExports.class)).thenReturn(gridMapExports);
        when(store.getEntitiesWithComponents(anyList())).thenReturn(Set.of());

        PhysicsIntegrationSystem system = new PhysicsIntegrationSystem(context);
        system.updateEntities();

        verify(store, never()).getComponents(anyLong(), any(long[].class), any(float[].class));
        verify(gridMapExports, never()).setPosition(anyLong(), anyFloat(), anyFloat(), anyFloat());
        verify(store, never()).attachComponents(anyLong(), any(long[].class), any(float[].class));
    }
}
