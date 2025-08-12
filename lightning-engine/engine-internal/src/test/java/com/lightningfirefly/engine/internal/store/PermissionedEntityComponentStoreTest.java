//package com.lightningfirefly.simulation.internal.store;
//
//import com.lightningfirefly.simulation.store.BaseComponent;
//import com.lightningfirefly.simulation.store.EntityComponentStore;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Nested;
//import org.junit.jupiter.api.Test;
//
//import java.util.List;
//import java.util.Set;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.assertj.core.api.Assertions.assertThatThrownBy;
//
//class PermissionedEntityComponentStoreTest {
//
//    private EntityComponentStore baseStore;
//    private BaseComponent flagComponent;
//    private PermissionedEntityComponentStore filteredStore;
//
//    // Test components
//    private static final BaseComponent POSITION_X = new TestComponent(0, "POSITION_X");
//    private static final BaseComponent POSITION_Y = new TestComponent(1, "POSITION_Y");
//    private static final BaseComponent VELOCITY_X = new TestComponent(2, "VELOCITY_X");
//    private static final BaseComponent FLAG_A = new TestComponent(3, "FLAG_A");
//    private static final BaseComponent FLAG_B = new TestComponent(4, "FLAG_B");
//
//    @BeforeEach
//    void setUp() {
//        baseStore = new ArrayEntityComponentStore(new EcsProperties(100, 10));
//        flagComponent = FLAG_A;
//        filteredStore = new PermissionedEntityComponentStore(baseStore, flagComponent);
//    }
//
//    @Nested
//    class GetEntitiesWithComponents {
//
//        @Test
//        void shouldOnlyReturnEntitiesWithFlagComponent() {
//            // Create entity 1 with flag and position
//            baseStore.createEntity(1L);
//            baseStore.attachComponent(1L, POSITION_X, 100L);
//            baseStore.attachComponent(1L, POSITION_Y, 200L);
//            baseStore.attachComponent(1L, FLAG_A, 1L);
//
//            // Create entity 2 with position but NO flag
//            baseStore.createEntity(2L);
//            baseStore.attachComponent(2L, POSITION_X, 300L);
//            baseStore.attachComponent(2L, POSITION_Y, 400L);
//
//            // Create entity 3 with different flag
//            baseStore.createEntity(3L);
//            baseStore.attachComponent(3L, POSITION_X, 500L);
//            baseStore.attachComponent(3L, POSITION_Y, 600L);
//            baseStore.attachComponent(3L, FLAG_B, 1L);
//
//            // Query through filtered store should only return entity 1
//            Set<Long> result = filteredStore.getEntitiesWithComponents(POSITION_X, POSITION_Y);
//
//            assertThat(result).containsExactly(1L);
//        }
//
//        @Test
//        void shouldReturnAllFlaggedEntitiesMatchingQuery() {
//            // Create two entities with flag
//            baseStore.createEntity(1L);
//            baseStore.attachComponent(1L, POSITION_X, 100L);
//            baseStore.attachComponent(1L, FLAG_A, 1L);
//
//            baseStore.createEntity(2L);
//            baseStore.attachComponent(2L, POSITION_X, 200L);
//            baseStore.attachComponent(2L, FLAG_A, 1L);
//
//            Set<Long> result = filteredStore.getEntitiesWithComponents(POSITION_X);
//
//            assertThat(result).containsExactlyInAnyOrder(1L, 2L);
//        }
//
//        @Test
//        void shouldReturnEmptySetWhenNoEntitiesHaveFlag() {
//            baseStore.createEntity(1L);
//            baseStore.attachComponent(1L, POSITION_X, 100L);
//            // No flag attached
//
//            Set<Long> result = filteredStore.getEntitiesWithComponents(POSITION_X);
//
//            assertThat(result).isEmpty();
//        }
//
//        @Test
//        void withIntComponentIds_shouldFilterByFlag() {
//            baseStore.createEntity(1L);
//            baseStore.attachComponent(1L, (int) POSITION_X.getId(), 100L);
//            baseStore.attachComponent(1L, (int) FLAG_A.getId(), 1L);
//
//            baseStore.createEntity(2L);
//            baseStore.attachComponent(2L, (int) POSITION_X.getId(), 200L);
//            // No flag
//
//            Set<Long> result = filteredStore.getEntitiesWithComponents((int) POSITION_X.getId());
//
//            assertThat(result).containsExactly(1L);
//        }
//
//        @Test
//        void withCollection_shouldFilterByFlag() {
//            baseStore.createEntity(1L);
//            baseStore.attachComponent(1L, POSITION_X, 100L);
//            baseStore.attachComponent(1L, FLAG_A, 1L);
//
//            baseStore.createEntity(2L);
//            baseStore.attachComponent(2L, POSITION_X, 200L);
//
//            Set<Long> result = filteredStore.getEntitiesWithComponents(List.of(POSITION_X));
//
//            assertThat(result).containsExactly(1L);
//        }
//    }
//
//    @Nested
//    class Delegation {
//
//        @Test
//        void getDelegate_shouldReturnUnderlyingStore() {
//            assertThat(filteredStore.getDelegate()).isSameAs(baseStore);
//        }
//
//        @Test
//        void getFlagComponent_shouldReturnFlagComponent() {
//            assertThat(filteredStore.getFlagComponent()).isSameAs(flagComponent);
//        }
//
//        @Test
//        void createEntity_shouldDelegateToBaseStore() {
//            filteredStore.createEntity(42L);
//
//            // Verify entity exists in base store
//            baseStore.attachComponent(42L, POSITION_X, 100L);
//            assertThat(baseStore.getComponent(42L, POSITION_X)).isEqualTo(100L);
//        }
//
//        @Test
//        void attachComponent_shouldDelegateToBaseStore() {
//            // Create through filtered store so entity gets the flag
//            filteredStore.createEntity(1L);
//            filteredStore.attachComponent(1L, POSITION_X, 999L);
//
//            assertThat(baseStore.getComponent(1L, POSITION_X)).isEqualTo(999L);
//        }
//
//        @Test
//        void getComponent_shouldDelegateToBaseStore() {
//            baseStore.createEntity(1L);
//            baseStore.attachComponent(1L, POSITION_X, 123L);
//
//            assertThat(filteredStore.getComponent(1L, POSITION_X)).isEqualTo(123L);
//        }
//
//        @Test
//        void hasComponent_shouldDelegateToBaseStore() {
//            baseStore.createEntity(1L);
//            baseStore.attachComponent(1L, POSITION_X, 100L);
//
//            assertThat(filteredStore.hasComponent(1L, POSITION_X)).isTrue();
//            assertThat(filteredStore.hasComponent(1L, POSITION_Y)).isFalse();
//        }
//    }
//
//    @Nested
//    class ModuleIsolation {
//
//        @Test
//        void twoFilteredStoresWithDifferentFlags_shouldSeeOnlyTheirEntities() {
//            // Create two filtered stores with different flags
//            PermissionedEntityComponentStore storeA = new PermissionedEntityComponentStore(baseStore, FLAG_A);
//            PermissionedEntityComponentStore storeB = new PermissionedEntityComponentStore(baseStore, FLAG_B);
//
//            // Create entity for module A
//            baseStore.createEntity(1L);
//            baseStore.attachComponent(1L, POSITION_X, 100L);
//            baseStore.attachComponent(1L, FLAG_A, 1L);
//
//            // Create entity for module B
//            baseStore.createEntity(2L);
//            baseStore.attachComponent(2L, POSITION_X, 200L);
//            baseStore.attachComponent(2L, FLAG_B, 1L);
//
//            // Create entity with both flags (shared)
//            baseStore.createEntity(3L);
//            baseStore.attachComponent(3L, POSITION_X, 300L);
//            baseStore.attachComponent(3L, FLAG_A, 1L);
//            baseStore.attachComponent(3L, FLAG_B, 1L);
//
//            // Store A sees entities 1 and 3
//            assertThat(storeA.getEntitiesWithComponents(POSITION_X))
//                    .containsExactlyInAnyOrder(1L, 3L);
//
//            // Store B sees entities 2 and 3
//            assertThat(storeB.getEntitiesWithComponents(POSITION_X))
//                    .containsExactlyInAnyOrder(2L, 3L);
//        }
//    }
//
//    @Nested
//    class PermissionGuards {
//
//        @Test
//        void deleteEntity_shouldSucceedForOwnedEntity() {
//            // Create entity through filtered store (automatically gets flag)
//            filteredStore.createEntity(1L);
//
//            // Should be able to delete it
//            filteredStore.deleteEntity(1L);
//
//            // Entity should no longer exist
//            assertThat(baseStore.hasComponent(1L, FLAG_A)).isFalse();
//        }
//
//        @Test
//        void deleteEntity_shouldThrowForUnownedEntity() {
//            // Create entity directly in base store (no flag)
//            baseStore.createEntity(1L);
//            baseStore.attachComponent(1L, POSITION_X, 100L);
//
//            // Should throw when trying to delete through filtered store
//            assertThatThrownBy(() -> filteredStore.deleteEntity(1L))
//                    .isInstanceOf(IllegalStateException.class)
//                    .hasMessageContaining("not owned by this module");
//        }
//
//        @Test
//        void deleteEntity_shouldThrowForEntityOwnedByDifferentModule() {
//            // Create entity with different flag
//            baseStore.createEntity(1L);
//            baseStore.attachComponent(1L, POSITION_X, 100L);
//            baseStore.attachComponent(1L, FLAG_B, 1L);
//
//            // Should throw when trying to delete through FLAG_A filtered store
//            assertThatThrownBy(() -> filteredStore.deleteEntity(1L))
//                    .isInstanceOf(IllegalStateException.class)
//                    .hasMessageContaining("not owned by this module");
//        }
//
//        @Test
//        void removeComponent_shouldSucceedForOwnedEntity() {
//            // Create entity through filtered store
//            filteredStore.createEntity(1L);
//            baseStore.attachComponent(1L, POSITION_X, 100L);
//
//            // Should be able to remove component
//            filteredStore.removeComponent(1L, POSITION_X);
//
//            assertThat(baseStore.hasComponent(1L, POSITION_X)).isFalse();
//        }
//
//        @Test
//        void removeComponent_shouldThrowForUnownedEntity() {
//            // Create entity without flag
//            baseStore.createEntity(1L);
//            baseStore.attachComponent(1L, POSITION_X, 100L);
//
//            // Should throw
//            assertThatThrownBy(() -> filteredStore.removeComponent(1L, POSITION_X))
//                    .isInstanceOf(IllegalStateException.class)
//                    .hasMessageContaining("not owned by this module");
//        }
//
//        @Test
//        void removeComponentWithBaseComponent_shouldThrowForUnownedEntity() {
//            // Create entity without flag
//            baseStore.createEntity(1L);
//            baseStore.attachComponent(1L, POSITION_X, 100L);
//
//            // Should throw
//            assertThatThrownBy(() -> filteredStore.removeComponent(1L, POSITION_X))
//                    .isInstanceOf(IllegalStateException.class)
//                    .hasMessageContaining("not owned by this module");
//        }
//
//        @Test
//        void attachComponent_shouldSucceedForOwnedEntity() {
//            // Create entity through filtered store
//            filteredStore.createEntity(1L);
//
//            // Should be able to attach component
//            filteredStore.attachComponent(1L, POSITION_X, 999L);
//
//            assertThat(baseStore.getComponent(1L, POSITION_X)).isEqualTo(999L);
//        }
//
//        @Test
//        void attachComponent_shouldThrowForUnownedEntity() {
//            // Create entity without flag
//            baseStore.createEntity(1L);
//
//            // Should throw
//            assertThatThrownBy(() -> filteredStore.attachComponent(1L, POSITION_X, 100L))
//                    .isInstanceOf(IllegalStateException.class)
//                    .hasMessageContaining("not owned by this module");
//        }
//
//        @Test
//        void attachComponentWithBaseComponent_shouldThrowForUnownedEntity() {
//            // Create entity without flag
//            baseStore.createEntity(1L);
//
//            // Should throw
//            assertThatThrownBy(() -> filteredStore.attachComponent(1L, POSITION_X, 100L))
//                    .isInstanceOf(IllegalStateException.class)
//                    .hasMessageContaining("not owned by this module");
//        }
//
//        @Test
//        void attachComponents_shouldSucceedForOwnedEntity() {
//            // Create entity through filtered store
//            filteredStore.createEntity(1L);
//
//            // Should be able to attach multiple components
//            filteredStore.attachComponents(1L, new int[]{(int) POSITION_X.getId(), (int) POSITION_Y.getId()}, new long[]{10L, 20L});
//
//            assertThat(baseStore.getComponent(1L, POSITION_X)).isEqualTo(10L);
//            assertThat(baseStore.getComponent(1L, POSITION_Y)).isEqualTo(20L);
//        }
//
//        @Test
//        void attachComponents_shouldThrowForUnownedEntity() {
//            // Create entity without flag
//            baseStore.createEntity(1L);
//
//            // Should throw
//            assertThatThrownBy(() -> filteredStore.attachComponents(1L,
//                    new int[]{(int) POSITION_X.getId()}, new long[]{100L}))
//                    .isInstanceOf(IllegalStateException.class)
//                    .hasMessageContaining("not owned by this module");
//        }
//
//        @Test
//        void attachComponentsWithList_shouldSucceedForOwnedEntity() {
//            // Create entity through filtered store
//            filteredStore.createEntity(1L);
//
//            // Should be able to attach multiple components
//            filteredStore.attachComponents(1L, List.of(POSITION_X, POSITION_Y), new long[]{10L, 20L});
//
//            assertThat(baseStore.getComponent(1L, POSITION_X)).isEqualTo(10L);
//            assertThat(baseStore.getComponent(1L, POSITION_Y)).isEqualTo(20L);
//        }
//
//        @Test
//        void attachComponentsWithList_shouldThrowForUnownedEntity() {
//            // Create entity without flag
//            baseStore.createEntity(1L);
//
//            // Should throw
//            assertThatThrownBy(() -> filteredStore.attachComponents(1L,
//                    List.of(POSITION_X), new long[]{100L}))
//                    .isInstanceOf(IllegalStateException.class)
//                    .hasMessageContaining("not owned by this module");
//        }
//
//        @Test
//        void createEntity_shouldAutomaticallyAttachFlag() {
//            filteredStore.createEntity(1L);
//
//            // Entity should have the flag
//            assertThat(baseStore.hasComponent(1L, FLAG_A)).isTrue();
//            assertThat(baseStore.getComponent(1L, FLAG_A))
//                    .isEqualTo(PermissionedEntityComponentStore.FLAG_TRUE_BOOLEAN);
//        }
//    }
//
//    private static class TestComponent extends BaseComponent {
//        TestComponent(int id, String name) {
//            super(id, name);
//        }
//    }
//}
