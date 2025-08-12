package com.lightningfirefly.game.domain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DomainObjectRegistry")
class DomainObjectRegistryTest {

    private DomainObjectRegistry registry;

    @BeforeEach
    void setUp() {
        registry = DomainObjectRegistry.getInstance();
        registry.clear();
    }

    @AfterEach
    void tearDown() {
        registry.clear();
    }

    @Nested
    @DisplayName("getInstance")
    class GetInstance {

        @Test
        @DisplayName("should return singleton instance")
        void shouldReturnSingleton() {
            DomainObjectRegistry instance1 = DomainObjectRegistry.getInstance();
            DomainObjectRegistry instance2 = DomainObjectRegistry.getInstance();

            assertThat(instance1).isSameAs(instance2);
        }
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("should store domain object by entity ID")
        void shouldStoreByEntityId() {
            TestDomainObject obj = new TestDomainObject(42);

            assertThat(registry.get(42)).isSameAs(obj);
        }

        @Test
        @DisplayName("should notify add listeners")
        void shouldNotifyAddListeners() {
            List<DomainObject> added = new ArrayList<>();
            registry.addOnRegisterListener(added::add);

            TestDomainObject obj = new TestDomainObject(1);

            assertThat(added).hasSize(1);
            assertThat(added.get(0)).isSameAs(obj);
        }
    }

    @Nested
    @DisplayName("unregister")
    class Unregister {

        @Test
        @DisplayName("should remove domain object")
        void shouldRemove() {
            TestDomainObject obj = new TestDomainObject(42);
            assertThat(registry.contains(42)).isTrue();

            registry.unregister(obj);

            assertThat(registry.contains(42)).isFalse();
        }

        @Test
        @DisplayName("should notify remove listeners")
        void shouldNotifyRemoveListeners() {
            List<DomainObject> removed = new ArrayList<>();
            registry.addOnUnregisterListener(removed::add);

            TestDomainObject obj = new TestDomainObject(1);
            registry.unregister(obj);

            assertThat(removed).hasSize(1);
            assertThat(removed.get(0)).isSameAs(obj);
        }
    }

    @Nested
    @DisplayName("get")
    class Get {

        @Test
        @DisplayName("should return null for unknown entity ID")
        void shouldReturnNullForUnknown() {
            assertThat(registry.get(999)).isNull();
        }

        @Test
        @DisplayName("should return registered object")
        void shouldReturnRegisteredObject() {
            TestDomainObject obj = new TestDomainObject(42);

            assertThat(registry.get(42)).isSameAs(obj);
        }
    }

    @Nested
    @DisplayName("getAll")
    class GetAll {

        @Test
        @DisplayName("should return empty collection when empty")
        void shouldReturnEmptyWhenEmpty() {
            assertThat(registry.getAll()).isEmpty();
        }

        @Test
        @DisplayName("should return all registered objects")
        void shouldReturnAllObjects() {
            TestDomainObject obj1 = new TestDomainObject(1);
            TestDomainObject obj2 = new TestDomainObject(2);
            TestDomainObject obj3 = new TestDomainObject(3);

            assertThat(registry.getAll()).hasSize(3);
            assertThat(registry.getAll()).containsExactlyInAnyOrder(obj1, obj2, obj3);
        }
    }

    @Nested
    @DisplayName("size")
    class Size {

        @Test
        @DisplayName("should return zero when empty")
        void shouldReturnZeroWhenEmpty() {
            assertThat(registry.size()).isEqualTo(0);
        }

        @Test
        @DisplayName("should return count of registered objects")
        void shouldReturnCount() {
            new TestDomainObject(1);
            new TestDomainObject(2);

            assertThat(registry.size()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("contains")
    class Contains {

        @Test
        @DisplayName("should return false for unknown entity ID")
        void shouldReturnFalseForUnknown() {
            assertThat(registry.contains(999)).isFalse();
        }

        @Test
        @DisplayName("should return true for registered entity ID")
        void shouldReturnTrueForRegistered() {
            new TestDomainObject(42);

            assertThat(registry.contains(42)).isTrue();
        }
    }

    @Nested
    @DisplayName("clear")
    class Clear {

        @Test
        @DisplayName("should remove all objects")
        void shouldRemoveAll() {
            new TestDomainObject(1);
            new TestDomainObject(2);
            assertThat(registry.size()).isEqualTo(2);

            registry.clear();

            assertThat(registry.size()).isEqualTo(0);
        }

        @Test
        @DisplayName("should notify remove listeners for each object")
        void shouldNotifyRemoveListeners() {
            AtomicInteger removeCount = new AtomicInteger(0);
            registry.addOnUnregisterListener(obj -> removeCount.incrementAndGet());

            new TestDomainObject(1);
            new TestDomainObject(2);
            new TestDomainObject(3);

            registry.clear();

            assertThat(removeCount.get()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("listeners")
    class Listeners {

        @Test
        @DisplayName("should allow removing listeners")
        void shouldAllowRemovingListeners() {
            AtomicInteger count = new AtomicInteger(0);
            var listener = (java.util.function.Consumer<DomainObject>) obj -> count.incrementAndGet();
            registry.addOnRegisterListener(listener);

            new TestDomainObject(1);
            assertThat(count.get()).isEqualTo(1);

            registry.removeOnRegisterListener(listener);
            new TestDomainObject(2);
            assertThat(count.get()).isEqualTo(1); // Still 1, listener was removed
        }
    }

    // Test domain object
    static class TestDomainObject extends DomainObject {
        @EcsEntityId
        long entityId;

        TestDomainObject(long entityId) {
            super(entityId);
        }
    }
}
