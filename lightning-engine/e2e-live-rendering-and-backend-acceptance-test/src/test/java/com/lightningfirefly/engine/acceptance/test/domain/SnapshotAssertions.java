package com.lightningfirefly.engine.acceptance.test.domain;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fluent assertions for verifying snapshot data.
 *
 * <p>Designed to read like English:
 * <pre>{@code
 * match.snapshot()
 *     .hasModule("RenderModule")
 *     .withComponent("SPRITE_X").containingValue(200f)
 *     .withComponent("SPRITE_Y").containingValue(150f);
 * }</pre>
 */
public class SnapshotAssertions {

    private final SnapshotParser parser;
    private String currentModule;

    SnapshotAssertions(SnapshotParser parser) {
        this.parser = parser;
    }

    /**
     * Assert that the snapshot contains the specified module.
     *
     * @param moduleName the module name to verify
     * @return this for chaining
     */
    public SnapshotAssertions hasModule(String moduleName) {
        assertThat(parser.hasModule(moduleName))
                .as("Snapshot should contain module '%s'", moduleName)
                .isTrue();
        this.currentModule = moduleName;
        return this;
    }

    /**
     * Assert that the snapshot does not contain the specified module.
     */
    public SnapshotAssertions lacksModule(String moduleName) {
        assertThat(parser.hasModule(moduleName))
                .as("Snapshot should not contain module '%s'", moduleName)
                .isFalse();
        return this;
    }

    /**
     * Select a component for verification.
     *
     * @param componentName the component name
     * @return a ComponentAssertions for further verification
     */
    public ComponentAssertions withComponent(String componentName) {
        if (currentModule == null) {
            throw new IllegalStateException("Must call hasModule() before withComponent()");
        }
        return new ComponentAssertions(this, currentModule, componentName);
    }

    /**
     * Get the underlying parser for advanced operations.
     */
    public SnapshotParser parser() {
        return parser;
    }

    /**
     * Fluent assertions for component values.
     */
    public static class ComponentAssertions {
        private final SnapshotAssertions parent;
        private final String moduleName;
        private final String componentName;
        private final List<Float> values;

        ComponentAssertions(SnapshotAssertions parent, String moduleName, String componentName) {
            this.parent = parent;
            this.moduleName = moduleName;
            this.componentName = componentName;
            this.values = parent.parser.getComponent(moduleName, componentName);
        }

        /**
         * Assert that the component contains a specific value.
         */
        public ComponentAssertions containingValue(float expected) {
            assertThat(values)
                    .as("Component '%s.%s' should contain value %f", moduleName, componentName, expected)
                    .contains(expected);
            return this;
        }

        /**
         * Assert that the first value equals the expected value.
         */
        public ComponentAssertions equalTo(float expected) {
            assertThat(values)
                    .as("Component '%s.%s' should have values", moduleName, componentName)
                    .isNotEmpty();
            assertThat(values.get(0))
                    .as("Component '%s.%s' first value", moduleName, componentName)
                    .isEqualTo(expected);
            return this;
        }

        /**
         * Assert that the value at the given index equals the expected value.
         */
        public ComponentAssertions atIndex(int index, float expected) {
            assertThat(values)
                    .as("Component '%s.%s' should have at least %d values", moduleName, componentName, index + 1)
                    .hasSizeGreaterThan(index);
            assertThat(values.get(index))
                    .as("Component '%s.%s' value at index %d", moduleName, componentName, index)
                    .isEqualTo(expected);
            return this;
        }

        /**
         * Assert that the component has a specific number of values.
         */
        public ComponentAssertions withCount(int expected) {
            assertThat(values)
                    .as("Component '%s.%s' should have %d values", moduleName, componentName, expected)
                    .hasSize(expected);
            return this;
        }

        /**
         * Assert that the component is not empty.
         */
        public ComponentAssertions isPresent() {
            assertThat(values)
                    .as("Component '%s.%s' should have values", moduleName, componentName)
                    .isNotEmpty();
            return this;
        }

        /**
         * Assert that the component contains all specified values.
         */
        public ComponentAssertions containingValues(float... expected) {
            Float[] boxed = new Float[expected.length];
            for (int i = 0; i < expected.length; i++) {
                boxed[i] = expected[i];
            }
            assertThat(values)
                    .as("Component '%s.%s' should contain values", moduleName, componentName)
                    .contains(boxed);
            return this;
        }

        /**
         * Return to the parent SnapshotAssertions for chaining.
         */
        public SnapshotAssertions and() {
            return parent;
        }

        /**
         * Select another component in the same module.
         */
        public ComponentAssertions withComponent(String componentName) {
            return parent.withComponent(componentName);
        }
    }
}
