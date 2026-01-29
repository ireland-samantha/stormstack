package ca.samanthaireland.engine.core.benchmark;

/**
 * Benchmark interface for modules to track custom performance metrics.
 *
 * <p>Modules can use this interface to instrument specific operations and measure
 * their execution time. Benchmark data is collected per-tick and exposed via the
 * metrics API alongside system execution metrics.
 *
 * <p>Usage example:
 * <pre>{@code
 * // In a system's updateEntities() method
 * try (var scope = benchmark.scope("pathfinding-calculation")) {
 *     // ... expensive pathfinding logic ...
 * }
 *
 * try (var scope = benchmark.scope("collision-detection")) {
 *     // ... collision detection logic ...
 * }
 * }</pre>
 *
 * <p>Thread Safety: Implementations must be thread-safe as modules may call from
 * multiple threads during tick execution.
 *
 * <p>Performance: Benchmark operations should have minimal overhead (< 1μs per scope).
 * Use efficient data structures and avoid allocations in hot paths.
 *
 * @see ca.samanthaireland.engine.ext.module.ModuleContext#getBenchmark()
 */
public interface Benchmark {

    /**
     * Start a named benchmark scope.
     *
     * <p>Use try-with-resources to automatically close the scope:
     * <pre>{@code
     * try (var scope = benchmark.scope("expensive-operation")) {
     *     // ... code to benchmark ...
     * }
     * }</pre>
     *
     * <p>The scope name should be descriptive and follow kebab-case naming
     * (e.g., "pathfinding-calculation", "collision-detection").
     *
     * <p>If a scope with the same name is already active in the current thread,
     * the new scope will overwrite the previous one. Nested scopes with different
     * names are not currently supported - use separate sequential scopes instead.
     *
     * @param name the name of the benchmark scope (must not be null)
     * @return a scope object that records timing when closed
     * @throws NullPointerException if name is null
     */
    BenchmarkScope scope(String name);

    /**
     * A benchmark scope that records execution time when closed.
     *
     * <p>Designed for use with try-with-resources. The scope records start time
     * on creation and end time on close, then reports the duration to the parent
     * Benchmark instance.
     */
    interface BenchmarkScope extends AutoCloseable {
        /**
         * End the benchmark scope and record the execution time.
         *
         * <p>This method does not throw checked exceptions, making it safe to use
         * with try-with-resources without explicit exception handling.
         */
        @Override
        void close();
    }
}
