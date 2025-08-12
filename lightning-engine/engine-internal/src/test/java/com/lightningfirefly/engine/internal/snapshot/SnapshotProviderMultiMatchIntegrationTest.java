package com.lightningfirefly.engine.internal.snapshot;

import com.lightningfirefly.engine.core.command.EngineCommand;
import com.lightningfirefly.engine.core.entity.CoreComponents;
import com.lightningfirefly.engine.core.store.BaseComponent;
import com.lightningfirefly.engine.core.store.EntityComponentStore;
import com.lightningfirefly.engine.core.system.EngineSystem;
import com.lightningfirefly.engine.ext.module.EngineModule;
import com.lightningfirefly.engine.ext.module.ModuleResolver;
import com.lightningfirefly.engine.internal.core.snapshot.SnapshotProvider;
import com.lightningfirefly.engine.internal.core.snapshot.SnapshotProviderImpl;
import com.lightningfirefly.engine.internal.core.store.ArrayEntityComponentStore;
import com.lightningfirefly.engine.internal.core.store.EcsProperties;
import com.lightningfirefly.engine.core.snapshot.Snapshot;
import com.lightningfirefly.engine.util.IdGeneratorV2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for SnapshotProvider multi-match isolation.
 *
 * <p>This test uses a real ECS store (not mocks) to verify that:
 * <ul>
 *   <li>Entities from different matches are properly isolated</li>
 *   <li>Snapshot for match A only contains entities belonging to match A</li>
 *   <li>ENTITY_ID component values are also properly filtered by match</li>
 * </ul>
 */
@DisplayName("SnapshotProvider Multi-Match Integration Test")
class SnapshotProviderMultiMatchIntegrationTest {

    private static final EcsProperties PROPERTIES = new EcsProperties(10000, 100);

    // Match IDs
    private static final long MATCH_1 = 1001L;
    private static final long MATCH_2 = 1002L;
    private static final long MATCH_3 = 1003L;

    // Entity IDs
    private static final long ENTITY_1_MATCH_1 = 100L;
    private static final long ENTITY_2_MATCH_1 = 101L;
    private static final long ENTITY_3_MATCH_2 = 200L;
    private static final long ENTITY_4_MATCH_2 = 201L;
    private static final long ENTITY_5_MATCH_2 = 202L;
    private static final long ENTITY_6_MATCH_3 = 300L;

    private EntityComponentStore store;
    private SnapshotProvider snapshotProvider;
    private TestModule testModule;

    @BeforeEach
    void setUp() {
        store = new ArrayEntityComponentStore(PROPERTIES);
        testModule = new TestModule();
        ModuleResolver moduleResolver = new TestModuleResolver(testModule);
        snapshotProvider = new SnapshotProviderImpl(store, moduleResolver);
    }

    @Test
    @DisplayName("Snapshot for match 1 should only contain entities from match 1")
    void snapshotForMatch1_shouldOnlyContainMatch1Entities() {
        // Create entities for Match 1
        createEntity(ENTITY_1_MATCH_1, MATCH_1, 100, 200);
        createEntity(ENTITY_2_MATCH_1, MATCH_1, 150, 250);

        // Create entities for Match 2
        createEntity(ENTITY_3_MATCH_2, MATCH_2, 1000, 2000);
        createEntity(ENTITY_4_MATCH_2, MATCH_2, 1500, 2500);
        createEntity(ENTITY_5_MATCH_2, MATCH_2, 1800, 2800);

        // Create entity for Match 3
        createEntity(ENTITY_6_MATCH_3, MATCH_3, 9000, 9500);

        // Get snapshot for Match 1
        Snapshot snapshot = snapshotProvider.createForMatch(MATCH_1);

        // Verify only Match 1 entities are in the snapshot
        Map<String, Map<String, List<Float>>> data = snapshot.snapshot();
        assertThat(data).containsKey("TestModule");

        Map<String, List<Float>> moduleData = data.get("TestModule");

        // Should have 2 entities' worth of POSITION_X values (100, 150)
        assertThat(moduleData.get("POSITION_X"))
                .as("POSITION_X should contain only Match 1 values")
                .containsExactlyInAnyOrder(100f, 150f);

        // Should NOT contain Match 2 or Match 3 values
        assertThat(moduleData.get("POSITION_X"))
                .as("POSITION_X should NOT contain Match 2 values")
                .doesNotContain(1000f, 1500f, 1800f, 9000f);

        // Verify POSITION_Y as well
        assertThat(moduleData.get("POSITION_Y"))
                .as("POSITION_Y should contain only Match 1 values")
                .containsExactlyInAnyOrder(200f, 250f);
    }

    @Test
    @DisplayName("Snapshot for match 2 should only contain entities from match 2")
    void snapshotForMatch2_shouldOnlyContainMatch2Entities() {
        // Create entities for Match 1
        createEntity(ENTITY_1_MATCH_1, MATCH_1, 100, 200);
        createEntity(ENTITY_2_MATCH_1, MATCH_1, 150, 250);

        // Create entities for Match 2
        createEntity(ENTITY_3_MATCH_2, MATCH_2, 1000, 2000);
        createEntity(ENTITY_4_MATCH_2, MATCH_2, 1500, 2500);
        createEntity(ENTITY_5_MATCH_2, MATCH_2, 1800, 2800);

        // Create entity for Match 3
        createEntity(ENTITY_6_MATCH_3, MATCH_3, 9000, 9500);

        // Get snapshot for Match 2
        Snapshot snapshot = snapshotProvider.createForMatch(MATCH_2);

        // Verify only Match 2 entities are in the snapshot
        Map<String, Map<String, List<Float>>> data = snapshot.snapshot();
        assertThat(data).containsKey("TestModule");

        Map<String, List<Float>> moduleData = data.get("TestModule");

        // Should have 3 entities' worth of POSITION_X values (1000, 1500, 1800)
        assertThat(moduleData.get("POSITION_X"))
                .as("POSITION_X should contain only Match 2 values")
                .containsExactlyInAnyOrder(1000f, 1500f, 1800f);

        // Should NOT contain Match 1 or Match 3 values
        assertThat(moduleData.get("POSITION_X"))
                .as("POSITION_X should NOT contain Match 1 or Match 3 values")
                .doesNotContain(100f, 150f, 9000f);
    }

    @Test
    @DisplayName("Snapshot for match with no entities should return empty data")
    void snapshotForEmptyMatch_shouldReturnEmptyData() {
        // Create entities only for Match 1
        createEntity(ENTITY_1_MATCH_1, MATCH_1, 100, 200);

        // Get snapshot for Match 2 (which has no entities)
        Snapshot snapshot = snapshotProvider.createForMatch(MATCH_2);

        // Verify snapshot is empty or has no data for TestModule
        Map<String, Map<String, List<Float>>> data = snapshot.snapshot();

        if (data.containsKey("TestModule")) {
            Map<String, List<Float>> moduleData = data.get("TestModule");
            // If present, values should be empty
            if (moduleData.containsKey("POSITION_X")) {
                assertThat(moduleData.get("POSITION_X"))
                        .as("Empty match should have no POSITION_X values")
                        .isEmpty();
            }
        }
    }

    @Test
    @DisplayName("ENTITY_ID component should also be filtered by match")
    void entityIdComponent_shouldBeFilteredByMatch() {
        // Create entities for Match 1 with ENTITY_ID attached
        createEntityWithEntityId(ENTITY_1_MATCH_1, MATCH_1, 100, 200);
        createEntityWithEntityId(ENTITY_2_MATCH_1, MATCH_1, 150, 250);

        // Create entities for Match 2 with ENTITY_ID attached
        createEntityWithEntityId(ENTITY_3_MATCH_2, MATCH_2, 1000, 2000);
        createEntityWithEntityId(ENTITY_4_MATCH_2, MATCH_2, 1500, 2500);

        // Get snapshot for Match 1
        Snapshot snapshot = snapshotProvider.createForMatch(MATCH_1);

        Map<String, Map<String, List<Float>>> data = snapshot.snapshot();

        // If ENTITY_ID is included in snapshot, it should only contain Match 1 entity IDs
        if (data.containsKey("TestModule")) {
            Map<String, List<Float>> moduleData = data.get("TestModule");
            if (moduleData.containsKey("ENTITY_ID")) {
                List<Float> entityIds = moduleData.get("ENTITY_ID");
                assertThat(entityIds)
                        .as("ENTITY_ID should only contain Match 1 entity IDs")
                        .containsExactlyInAnyOrder((float) ENTITY_1_MATCH_1, (float) ENTITY_2_MATCH_1);

                // Should NOT contain Match 2 entity IDs
                assertThat(entityIds)
                        .as("ENTITY_ID should NOT contain Match 2 entity IDs")
                        .doesNotContain((float) ENTITY_3_MATCH_2, (float) ENTITY_4_MATCH_2);
            }
        }
    }

    @Test
    @DisplayName("Multiple snapshots for different matches should be independent")
    void multipleSnapshots_shouldBeIndependent() {
        // Create entities for Match 1
        createEntity(ENTITY_1_MATCH_1, MATCH_1, 100, 200);

        // Create entities for Match 2
        createEntity(ENTITY_3_MATCH_2, MATCH_2, 1000, 2000);

        // Get both snapshots
        Snapshot snapshot1 = snapshotProvider.createForMatch(MATCH_1);
        Snapshot snapshot2 = snapshotProvider.createForMatch(MATCH_2);

        // Verify they are independent
        Map<String, List<Float>> module1 = snapshot1.snapshot().get("TestModule");
        Map<String, List<Float>> module2 = snapshot2.snapshot().get("TestModule");

        assertThat(module1.get("POSITION_X"))
                .as("Match 1 snapshot should contain only Match 1 data")
                .containsExactly(100f);

        assertThat(module2.get("POSITION_X"))
                .as("Match 2 snapshot should contain only Match 2 data")
                .containsExactly(1000f);

        // Ensure no cross-contamination
        assertThat(module1.get("POSITION_X"))
                .as("Match 1 snapshot should NOT contain Match 2 data")
                .doesNotContain(1000f);

        assertThat(module2.get("POSITION_X"))
                .as("Match 2 snapshot should NOT contain Match 1 data")
                .doesNotContain(100f);
    }

    // ==================== Helper Methods ====================

    private void createEntity(long entityId, long matchId, long posX, long posY) {
        store.attachComponent(entityId, CoreComponents.MATCH_ID, matchId);
        store.attachComponent(entityId, testModule.flagComponent, 1L);
        store.attachComponent(entityId, testModule.positionX, posX);
        store.attachComponent(entityId, testModule.positionY, posY);
    }

    private void createEntityWithEntityId(long entityId, long matchId, long posX, long posY) {
        createEntity(entityId, matchId, posX, posY);
        store.attachComponent(entityId, CoreComponents.ENTITY_ID, entityId);
    }

    // ==================== Test Module Implementation ====================

    private static class TestComponent extends BaseComponent {
        TestComponent(String name) {
            super(IdGeneratorV2.newId(), name);
        }
    }

    private static class TestModule implements EngineModule {
        final BaseComponent flagComponent = new TestComponent("TEST_FLAG");
        final BaseComponent positionX = new TestComponent("POSITION_X");
        final BaseComponent positionY = new TestComponent("POSITION_Y");

        @Override
        public List<EngineSystem> createSystems() {
            return List.of();
        }

        @Override
        public List<EngineCommand> createCommands() {
            return List.of();
        }

        @Override
        public List<BaseComponent> createComponents() {
            return List.of(positionX, positionY);
        }

        @Override
        public BaseComponent createFlagComponent() {
            return flagComponent;
        }

        @Override
        public String getName() {
            return "TestModule";
        }
    }

    private static class TestModuleResolver implements ModuleResolver {
        private final TestModule module;

        TestModuleResolver(TestModule module) {
            this.module = module;
        }

        @Override
        public EngineModule resolveModule(String moduleName) {
            return "TestModule".equals(moduleName) ? module : null;
        }

        @Override
        public List<String> getAvailableModules() {
            return List.of("TestModule");
        }

        @Override
        public List<EngineModule> resolveAllModules() {
            return List.of(module);
        }

        @Override
        public boolean hasModule(String moduleName) {
            return "TestModule".equals(moduleName);
        }
    }
}
