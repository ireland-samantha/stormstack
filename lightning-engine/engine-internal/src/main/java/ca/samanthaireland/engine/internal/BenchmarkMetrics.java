package ca.samanthaireland.engine.internal;

/**
 * Metrics for a single benchmark scope in the last tick.
 *
 * <p>Similar to {@link SystemExecutionMetrics} but for module-defined benchmarks.
 * Records the execution time of a specific named scope within a module's systems.
 *
 * @param moduleName the name of the module that created this benchmark
 * @param scopeName the name of the benchmark scope
 * @param executionTimeNanos execution time in nanoseconds
 */
public record BenchmarkMetrics(
        String moduleName,
        String scopeName,
        long executionTimeNanos
) {
    /**
     * Get execution time in milliseconds.
     *
     * @return execution time in milliseconds with microsecond precision
     */
    public double executionTimeMs() {
        return executionTimeNanos / 1_000_000.0;
    }

    /**
     * Create a fully qualified benchmark name (module:scope).
     *
     * <p>Format: "{moduleName}:{scopeName}"
     *
     * @return the fully qualified benchmark name
     */
    public String fullName() {
        return moduleName + ":" + scopeName;
    }
}
