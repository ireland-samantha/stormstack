package com.lightningfirefly.game.domain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DomainObject")
class DomainObjectTest {

    @BeforeEach
    void setUp() {
        DomainObjectRegistry.getInstance().clear();
    }

    @AfterEach
    void tearDown() {
        DomainObjectRegistry.getInstance().clear();
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("should auto-register with registry")
        void shouldAutoRegister() {
            TestDomainObject obj = new TestDomainObject(42);

            assertThat(DomainObjectRegistry.getInstance().contains(42)).isTrue();
            assertThat(DomainObjectRegistry.getInstance().get(42)).isSameAs(obj);
        }

        @Test
        @DisplayName("should set entityId field")
        void shouldSetEntityIdField() {
            TestDomainObject obj = new TestDomainObject(123);

            assertThat(obj.getEntityId()).isEqualTo(123);
            assertThat(obj.entityId).isEqualTo(123);
        }

        @Test
        @DisplayName("should scan watched fields")
        void shouldScanWatchedFields() {
            TestDomainObject obj = new TestDomainObject(1);

            List<DomainObject.WatchedField> fields = obj.getWatchedFields();
            assertThat(fields).hasSize(2);

            DomainObject.WatchedField posX = fields.stream()
                .filter(f -> f.componentName().equals("POSITION_X"))
                .findFirst()
                .orElse(null);
            assertThat(posX).isNotNull();
            assertThat(posX.moduleName()).isEqualTo("MoveModule");

            DomainObject.WatchedField posY = fields.stream()
                .filter(f -> f.componentName().equals("POSITION_Y"))
                .findFirst()
                .orElse(null);
            assertThat(posY).isNotNull();
            assertThat(posY.moduleName()).isEqualTo("MoveModule");
        }
    }

    @Nested
    @DisplayName("dispose")
    class Dispose {

        @Test
        @DisplayName("should unregister from registry")
        void shouldUnregister() {
            TestDomainObject obj = new TestDomainObject(42);
            assertThat(DomainObjectRegistry.getInstance().contains(42)).isTrue();

            obj.dispose();

            assertThat(DomainObjectRegistry.getInstance().contains(42)).isFalse();
        }

        @Test
        @DisplayName("should mark as disposed")
        void shouldMarkAsDisposed() {
            TestDomainObject obj = new TestDomainObject(42);
            assertThat(obj.isDisposed()).isFalse();

            obj.dispose();

            assertThat(obj.isDisposed()).isTrue();
        }

        @Test
        @DisplayName("should be idempotent")
        void shouldBeIdempotent() {
            TestDomainObject obj = new TestDomainObject(42);

            obj.dispose();
            obj.dispose();
            obj.dispose();

            assertThat(obj.isDisposed()).isTrue();
        }
    }

    @Nested
    @DisplayName("updateField")
    class UpdateField {

        @Test
        @DisplayName("should update float field")
        void shouldUpdateFloatField() {
            TestDomainObject obj = new TestDomainObject(1);
            DomainObject.WatchedField field = obj.getWatchedFields().stream()
                .filter(f -> f.componentName().equals("POSITION_X"))
                .findFirst()
                .orElseThrow();

            obj.updateField(field, 123.5f);

            assertThat(obj.positionX).isEqualTo(123.5f);
        }

        @Test
        @DisplayName("should not update after disposed")
        void shouldNotUpdateAfterDisposed() {
            TestDomainObject obj = new TestDomainObject(1);
            obj.positionX = 10f;
            obj.dispose();

            DomainObject.WatchedField field = obj.getWatchedFields().stream()
                .filter(f -> f.componentName().equals("POSITION_X"))
                .findFirst()
                .orElseThrow();

            obj.updateField(field, 999f);

            assertThat(obj.positionX).isEqualTo(10f);
        }
    }

    @Nested
    @DisplayName("invalid ecsPath")
    class InvalidEcsPath {

        @Test
        @DisplayName("should throw for invalid ecsPath format")
        void shouldThrowForInvalidFormat() {
            assertThatThrownBy(() -> new InvalidDomainObject(1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid ecsPath");
        }
    }

    // Test domain object
    static class TestDomainObject extends DomainObject {
        @EcsEntityId
        long entityId;

        @EcsComponent(componentPath = "MoveModule.POSITION_X")
        float positionX;

        @EcsComponent(componentPath = "MoveModule.POSITION_Y")
        float positionY;

        TestDomainObject(long entityId) {
            super(entityId);
        }
    }

    // Domain object with invalid ecsPath
    static class InvalidDomainObject extends DomainObject {
        @EcsEntityId
        long entityId;

        @EcsComponent(componentPath = "invalidPath")
        float invalid;

        InvalidDomainObject(long entityId) {
            super(entityId);
        }
    }
}
