/*
 * Copyright (c) 2026 Samantha Ireland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */


package ca.samanthaireland.game.domain;

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
