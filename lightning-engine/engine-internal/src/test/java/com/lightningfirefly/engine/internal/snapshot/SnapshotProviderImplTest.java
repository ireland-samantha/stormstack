//package com.lightningfirefly.simulation.internal.snapshot;
//
//import com.lightningfirefly.simulation.command.Command;
//import com.lightningfirefly.simulation.module.Module;
//import com.lightningfirefly.simulation.module.ModuleResolver;
//import com.lightningfirefly.simulation.snapshot.Snapshot;
//import com.lightningfirefly.simulation.snapshot.SnapshotFilter;
//import com.lightningfirefly.simulation.store.BaseComponent;
//import com.lightningfirefly.simulation.store.EntityComponentStore;
//import com.lightningfirefly.simulation.system.System;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.*;
//
 // todo for AI: fix this test class
//@ExtendWith(MockitoExtension.class)
//class SnapshotProviderImplTest {
//
//    @Mock
//    private EntityComponentStore entityComponentStore;
//
//    @Mock
//    private ModuleResolver moduleResolver;
//
//    private SnapshotProviderImpl snapshotProvider;
//
//    @BeforeEach
//    void setUp() {
//        snapshotProvider = new SnapshotProviderImpl(entityComponentStore, moduleResolver);
//    }
//
//    @Test
//    void createForMatch_withNoModules_shouldReturnEmptySnapshot() {
//        when(moduleResolver.resolveAllModules()).thenReturn(List.of());
//
//        Snapshot snapshot = snapshotProvider.createForMatch(null);
//
//        assertThat(snapshot.snapshot()).isEmpty();
//    }
//
//    @Test
//    void createForMatch_withModuleWithNoComponents_shouldReturnEmptySnapshot() {
//        Module module = new TestModule(List.of());
//        when(moduleResolver.resolveAllModules()).thenReturn(List.of(module));
//
//        Snapshot snapshot = snapshotProvider.createForMatch(null);
//
//        assertThat(snapshot.snapshot()).isEmpty();
//    }
//
//    @Test
//    void createForMatch_withNoEntities_shouldReturnEmptySnapshot() {
//        TestComponent component = new TestComponent(0, "position");
//        Module module = new TestModule(List.of(component));
//        when(moduleResolver.resolveAllModules()).thenReturn(List.of(module));
//        when(entityComponentStore.getEntitiesWithComponents(any(List.class))).thenReturn(Set.of());
//
//        Snapshot snapshot = snapshotProvider.createForMatch(null);
//
//        assertThat(snapshot.snapshot()).isEmpty();
//    }
//
//    @Test
//    void createForMatch_shouldBuildSnapshotWithComponentValues() {
//        TestComponent positionComponent = new TestComponent(0, "position");
//        TestComponent healthComponent = new TestComponent(1, "health");
//        Module module = new TestModule(List.of(positionComponent, healthComponent));
//
//        when(moduleResolver.resolveAllModules()).thenReturn(List.of(module));
//        when(entityComponentStore.getEntitiesWithComponents(any(List.class)))
//                .thenReturn(Set.of(100L, 200L));
//        when(entityComponentStore.hasComponent(100L, positionComponent)).thenReturn(true);
//        when(entityComponentStore.hasComponent(100L, healthComponent)).thenReturn(true);
//        when(entityComponentStore.hasComponent(200L, positionComponent)).thenReturn(true);
//        when(entityComponentStore.hasComponent(200L, healthComponent)).thenReturn(false);
//        when(entityComponentStore.getComponent(100L, positionComponent)).thenReturn(1000L);
//        when(entityComponentStore.getComponent(100L, healthComponent)).thenReturn(100L);
//        when(entityComponentStore.getComponent(200L, positionComponent)).thenReturn(2000L);
//
//        Snapshot snapshot = snapshotProvider.createForMatch(null);
//
//        assertThat(snapshot.snapshot()).containsKey("TestModule");
//        Map<String, List<Long>> moduleData = snapshot.snapshot().get("TestModule");
//        assertThat(moduleData.get("position")).containsExactlyInAnyOrder(1000L, 2000L);
//        assertThat(moduleData.get("health")).containsExactly(100L);
//    }
//
//    @Test
//    void createForMatch_withFilter_shouldApplyFilter() {
//        TestComponent component = new TestComponent(0, "position");
//        Module module = new TestModule(List.of(component));
//        when(moduleResolver.resolveAllModules()).thenReturn(List.of(module));
//        when(entityComponentStore.getEntitiesWithComponents(any(List.class)))
//                .thenReturn(Set.of(100L));
//        when(entityComponentStore.hasComponent(100L, component)).thenReturn(true);
//        when(entityComponentStore.getComponent(100L, component)).thenReturn(1000L);
//
//        SnapshotFilter filter = new SnapshotFilter(List.of(1L), List.of(42L));
//        Snapshot snapshot = snapshotProvider.createForMatch(filter);
//
//        assertThat(snapshot.snapshot()).containsKey("TestModule");
//    }
//
//    @Test
//    void createForMatch_shouldCacheMappings() {
//        TestComponent component = new TestComponent(0, "position");
//        Module module = new TestModule(List.of(component));
//        when(moduleResolver.resolveAllModules()).thenReturn(List.of(module));
//        when(entityComponentStore.getEntitiesWithComponents(any(List.class))).thenReturn(Set.of());
//
//        snapshotProvider.createForMatch(null);
//        snapshotProvider.createForMatch(null);
//
//        // Should only call resolveAllModules once due to caching
//        verify(moduleResolver, times(1)).resolveAllModules();
//    }
//
//    @Test
//    void invalidateCache_shouldForceMappingsRebuild() {
//        TestComponent component = new TestComponent(0, "position");
//        Module module = new TestModule(List.of(component));
//        when(moduleResolver.resolveAllModules()).thenReturn(List.of(module));
//        when(entityComponentStore.getEntitiesWithComponents(any(List.class))).thenReturn(Set.of());
//
//        snapshotProvider.createForMatch(null);
//        snapshotProvider.invalidateCache();
//        snapshotProvider.createForMatch(null);
//
//        // Should call resolveAllModules twice after cache invalidation
//        verify(moduleResolver, times(2)).resolveAllModules();
//    }
//
//    @Test
//    void createForMatch_withMultipleModules_shouldIncludeAllModuleData() {
//        TestComponent comp1 = new TestComponent(0, "comp1");
//        TestComponent comp2 = new TestComponent(1, "comp2");
//        Module module1 = new TestModuleOne(List.of(comp1));
//        Module module2 = new TestModuleTwo(List.of(comp2));
//
//        when(moduleResolver.resolveAllModules()).thenReturn(List.of(module1, module2));
//        when(entityComponentStore.getEntitiesWithComponents(any(List.class)))
//                .thenReturn(Set.of(100L));
//        when(entityComponentStore.hasComponent(100L, comp1)).thenReturn(true);
//        when(entityComponentStore.hasComponent(100L, comp2)).thenReturn(true);
//        when(entityComponentStore.getComponent(100L, comp1)).thenReturn(111L);
//        when(entityComponentStore.getComponent(100L, comp2)).thenReturn(222L);
//
//        Snapshot snapshot = snapshotProvider.createForMatch(null);
//
//        assertThat(snapshot.snapshot()).containsKeys("TestModuleOne", "TestModuleTwo");
//        assertThat(snapshot.snapshot().get("TestModuleOne").get("comp1")).containsExactly(111L);
//        assertThat(snapshot.snapshot().get("TestModuleTwo").get("comp2")).containsExactly(222L);
//    }
//
//    @Test
//    void captureAll_shouldCallCreateForMatchWithNull() {
//        when(moduleResolver.resolveAllModules()).thenReturn(List.of());
//
//        Snapshot snapshot = snapshotProvider.captureAll();
//
//        assertThat(snapshot.snapshot()).isEmpty();
//    }
//
//    // Test implementations
//
//    private static class TestComponent extends BaseComponent {
//        TestComponent(int id, String name) {
//            super(id, name);
//        }
//    }
//
//    private static class TestModule implements Module {
//        private final List<BaseComponent> components;
//
//        TestModule(List<BaseComponent> components) {
//            this.components = components;
//        }
//
//        @Override
//        public List<System> createSystems() {
//            return List.of();
//        }
//
//        @Override
//        public List<Command> createCommands() {
//            return List.of();
//        }
//
//        @Override
//        public List<BaseComponent> createComponents() {
//            return components;
//        }
//
//        @Override
//        public BaseComponent flagComponent() {
//            return null;
//        }
//
//        @Override
//        public String getName() {
//            return "TestModule";
//        }
//    }
//
//    private static class TestModuleOne extends TestModule {
//        TestModuleOne(List<BaseComponent> components) {
//            super(components);
//        }
//
//        @Override
//        public String getName() {
//            return "TestModuleOne";
//        }
//    }
//
//    private static class TestModuleTwo extends TestModule {
//        TestModuleTwo(List<BaseComponent> components) {
//            super(components);
//        }
//
//        @Override
//        public String getName() {
//            return "TestModuleTwo";
//        }
//    }
//}
