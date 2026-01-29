package ca.samanthaireland.engine.ext.modules.system;

import ca.samanthaireland.engine.core.benchmark.Benchmark;
import ca.samanthaireland.engine.core.store.EntityComponentStore;
import ca.samanthaireland.engine.ext.module.ModuleContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ForceIntegrationSystemTest {

    @Mock
    private ModuleContext context;

    @Mock
    private EntityComponentStore store;

    @Mock
    private Benchmark benchmark;

    @Mock
    private Benchmark.BenchmarkScope benchmarkScope;

    @BeforeEach
    void setUp() {
        when(context.getBenchmark()).thenReturn(benchmark);
        when(benchmark.scope("force-integration")).thenReturn(benchmarkScope);
    }

    @Test
    void updateEntities_calculatesAccelerationFromForce() {
        when(context.getEntityComponentStore()).thenReturn(store);
        when(store.getEntitiesWithComponents(anyList())).thenReturn(Set.of(1L));

        // Setup: entity with mass=2.0, forceX=10.0, forceY=20.0, forceZ=0.0
        doAnswer(invocation -> {
            float[] buffer = invocation.getArgument(2);
            buffer[0] = 2.0f;  // mass
            buffer[1] = 10.0f; // forceX
            buffer[2] = 20.0f; // forceY
            buffer[3] = 0.0f;  // forceZ
            buffer[4] = 0.0f;  // torque
            buffer[5] = 1.0f;  // inertia
            buffer[6] = 0.0f;  // angularVel
            return null;
        }).when(store).getComponents(eq(1L), any(long[].class), any(float[].class));

        ForceIntegrationSystem system = new ForceIntegrationSystem(context);
        system.updateEntities();

        // Verify acceleration calculated: accelX = 10/2 = 5.0, accelY = 20/2 = 10.0
        verify(store).attachComponents(eq(1L), any(long[].class), argThat(buffer ->
            buffer[0] == 5.0f && buffer[1] == 10.0f && buffer[2] == 0.0f
        ));
    }

    @Test
    void updateEntities_preventsZeroMassDivision() {
        when(context.getEntityComponentStore()).thenReturn(store);
        when(store.getEntitiesWithComponents(anyList())).thenReturn(Set.of(1L));

        // Setup: entity with mass=0.0 (invalid)
        doAnswer(invocation -> {
            float[] buffer = invocation.getArgument(2);
            buffer[0] = 0.0f;  // mass (invalid)
            buffer[1] = 10.0f; // forceX
            buffer[2] = 0.0f;  // forceY
            buffer[3] = 0.0f;  // forceZ
            buffer[4] = 0.0f;  // torque
            buffer[5] = 1.0f;  // inertia
            buffer[6] = 0.0f;  // angularVel
            return null;
        }).when(store).getComponents(eq(1L), any(long[].class), any(float[].class));

        ForceIntegrationSystem system = new ForceIntegrationSystem(context);

        // Should not throw exception (defaults to mass=1.0)
        assertDoesNotThrow(() -> system.updateEntities());
    }

    @Test
    void updateEntities_calculatesAngularAccelerationFromTorque() {
        when(context.getEntityComponentStore()).thenReturn(store);
        when(store.getEntitiesWithComponents(anyList())).thenReturn(Set.of(1L));

        // Setup: torque=5.0, inertia=2.0
        doAnswer(invocation -> {
            float[] buffer = invocation.getArgument(2);
            buffer[0] = 1.0f;  // mass
            buffer[1] = 0.0f;  // forceX
            buffer[2] = 0.0f;  // forceY
            buffer[3] = 0.0f;  // forceZ
            buffer[4] = 5.0f;  // torque
            buffer[5] = 2.0f;  // inertia
            buffer[6] = 1.0f;  // angularVel
            return null;
        }).when(store).getComponents(eq(1L), any(long[].class), any(float[].class));

        ForceIntegrationSystem system = new ForceIntegrationSystem(context);
        system.updateEntities();

        // Angular acceleration = torque / inertia = 5/2 = 2.5
        // New angular velocity = old + (angularAccel * dt) = 1.0 + (2.5 * 1/60)
        float expectedAngularVel = 1.0f + (2.5f * (1.0f / 60.0f));

        verify(store).attachComponents(eq(1L), any(long[].class), argThat(buffer ->
            Math.abs(buffer[3] - expectedAngularVel) < 0.001f
        ));
    }

    @Test
    void updateEntities_withNoEntities_doesNothing() {
        when(context.getEntityComponentStore()).thenReturn(store);
        when(store.getEntitiesWithComponents(anyList())).thenReturn(Set.of());

        ForceIntegrationSystem system = new ForceIntegrationSystem(context);
        system.updateEntities();

        verify(store, never()).getComponents(anyLong(), any(long[].class), any(float[].class));
        verify(store, never()).attachComponents(anyLong(), any(long[].class), any(float[].class));
    }
}
