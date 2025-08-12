package com.lightningfirefly.engine.rendering.testing;

/**
 * Functional interface for conditions to wait for.
 * @param <T> the return type
 */
@FunctionalInterface
public interface ExpectedCondition<T> {

    /**
     * Apply this condition to the driver.
     * @param driver the driver
     * @return the result (null or false means condition not met)
     */
    T apply(GuiDriver driver);

    /**
     * Get a description of this condition for error messages.
     * @return human-readable description
     */
    default String describe() {
        return "expected condition";
    }
}
