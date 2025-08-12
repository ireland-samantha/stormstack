package com.lightningfirefly.engine.core.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link BaseComponent} to ensure SOLID compliance:
 * - LSP: Constructors honor their contracts
 * - Validation: Invalid inputs are rejected
 */
class BaseComponentTest {

    /**
     * Concrete implementation for testing.
     */
    static class TestComponent extends BaseComponent {
        public TestComponent(long id, String name) {
            super(id, name);
        }

        public TestComponent(String name) {
            super(name);
        }
    }

    // ========== LSP Compliance Tests ==========

    @Test
    @DisplayName("Constructor with ID should honor the provided ID (LSP compliance)")
    void constructor_withId_shouldUseProvidedId() {
        long providedId = 42L;
        TestComponent component = new TestComponent(providedId, "TEST");

        assertThat(component.getId()).isEqualTo(providedId);
    }

    @Test
    @DisplayName("Constructor with ID should honor different IDs")
    void constructor_withId_shouldHonorDifferentIds() {
        TestComponent comp1 = new TestComponent(100L, "TEST1");
        TestComponent comp2 = new TestComponent(200L, "TEST2");

        assertThat(comp1.getId()).isEqualTo(100L);
        assertThat(comp2.getId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("Constructor without ID should auto-generate unique IDs")
    void constructor_withoutId_shouldAutoGenerateUniqueIds() {
        TestComponent comp1 = new TestComponent("TEST1");
        TestComponent comp2 = new TestComponent("TEST2");

        assertThat(comp1.getId()).isPositive();
        assertThat(comp2.getId()).isPositive();
        assertThat(comp1.getId()).isNotEqualTo(comp2.getId());
    }

    @Test
    @DisplayName("Name should be stored correctly")
    void constructor_shouldStoreNameCorrectly() {
        TestComponent component = new TestComponent(1L, "MY_COMPONENT");

        assertThat(component.getName()).isEqualTo("MY_COMPONENT");
    }

    // ========== Validation Tests ==========

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -100, Long.MIN_VALUE})
    @DisplayName("Constructor should reject non-positive IDs")
    void constructor_withNonPositiveId_shouldThrow(long invalidId) {
        assertThatThrownBy(() -> new TestComponent(invalidId, "TEST"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Component ID must be positive");
    }

    @Test
    @DisplayName("Constructor should reject null name")
    void constructor_withNullName_shouldThrow() {
        assertThatThrownBy(() -> new TestComponent(1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name must not be null or blank");
    }

    @Test
    @DisplayName("Constructor should reject blank name")
    void constructor_withBlankName_shouldThrow() {
        assertThatThrownBy(() -> new TestComponent(1L, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name must not be null or blank");
    }

    @Test
    @DisplayName("Constructor should reject empty name")
    void constructor_withEmptyName_shouldThrow() {
        assertThatThrownBy(() -> new TestComponent(1L, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name must not be null or blank");
    }

    @Test
    @DisplayName("Auto-generate constructor should also validate name")
    void constructorWithoutId_withNullName_shouldThrow() {
        assertThatThrownBy(() -> new TestComponent(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name must not be null or blank");
    }

    // ========== Equality Tests ==========

    @Test
    @DisplayName("Components with same ID should be equal")
    void equals_sameId_shouldBeEqual() {
        TestComponent comp1 = new TestComponent(42L, "NAME1");
        TestComponent comp2 = new TestComponent(42L, "NAME2");

        assertThat(comp1).isEqualTo(comp2);
        assertThat(comp1.hashCode()).isEqualTo(comp2.hashCode());
    }

    @Test
    @DisplayName("Components with different IDs should not be equal")
    void equals_differentId_shouldNotBeEqual() {
        TestComponent comp1 = new TestComponent(42L, "NAME");
        TestComponent comp2 = new TestComponent(43L, "NAME");

        assertThat(comp1).isNotEqualTo(comp2);
    }

    @Test
    @DisplayName("Component should not equal null")
    void equals_null_shouldNotBeEqual() {
        TestComponent comp = new TestComponent(42L, "NAME");

        assertThat(comp).isNotEqualTo(null);
    }

    @Test
    @DisplayName("Component should not equal different type")
    void equals_differentType_shouldNotBeEqual() {
        TestComponent comp = new TestComponent(42L, "NAME");

        assertThat(comp).isNotEqualTo("not a component");
    }

    // ========== Immutability Tests ==========

    @Test
    @DisplayName("ID should be immutable after construction")
    void id_shouldBeImmutableAfterConstruction() {
        TestComponent comp = new TestComponent(42L, "NAME");
        long originalId = comp.getId();

        // ID getter should always return the same value
        assertThat(comp.getId()).isEqualTo(originalId);
        assertThat(comp.getId()).isEqualTo(originalId);
    }

    @Test
    @DisplayName("Name should be immutable after construction")
    void name_shouldBeImmutableAfterConstruction() {
        TestComponent comp = new TestComponent(42L, "NAME");

        // Name getter should always return the same value
        assertThat(comp.getName()).isEqualTo("NAME");
        assertThat(comp.getName()).isEqualTo("NAME");
    }

    // ========== toString Tests ==========

    @Test
    @DisplayName("toString should include ID and name")
    void toString_shouldIncludeIdAndName() {
        TestComponent comp = new TestComponent(42L, "MY_COMPONENT");

        String result = comp.toString();

        assertThat(result).contains("42");
        assertThat(result).contains("MY_COMPONENT");
    }
}
