package ca.samanthaireland.engine.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized collector for all module benchmarks.
 *
 * <p>This class is owned by GameLoop and aggregates benchmarks from all modules
 * after each tick completes. It manages the lifecycle of benchmark data:
 * <ol>
 *   <li>Clear: At tick start, clear all module benchmarks</li>
 *   <li>Execute: Modules record benchmarks during system execution</li>
 *   <li>Collect: After systems run, collect all benchmarks</li>
 *   <li>Expose: GameLoop exposes collected benchmarks via metrics API</li>
 * </ol>
 *
 * <p>Thread Safety: Uses ConcurrentHashMap for thread-safe module registration.
 * The lastTickBenchmarks field is volatile to ensure visibility across threads.
 */
public class BenchmarkCollector {

    private final Map<String, DefaultBenchmark> moduleBenchmarks = new ConcurrentHashMap<>();
    private volatile List<BenchmarkMetrics> lastTickBenchmarks = Collections.emptyList();

    /**
     * Register a module's benchmark instance.
     *
     * <p>Called during module initialization. Each module gets its own
     * DefaultBenchmark instance that tracks its specific benchmarks.
     *
     * @param moduleName the name of the module
     * @param benchmark the benchmark instance for this module
     */
    public void registerModuleBenchmark(String moduleName, DefaultBenchmark benchmark) {
        moduleBenchmarks.put(moduleName, benchmark);
    }

    /**
     * Collect benchmarks from all modules after tick execution.
     *
     * <p>Called by GameLoop after all systems have executed. Aggregates
     * benchmark data from all registered modules into a single list.
     *
     * <p>This operation is O(n*m) where n is the number of modules and
     * m is the average number of benchmarks per module.
     */
    public void collectTickBenchmarks() {
        List<BenchmarkMetrics> collected = new ArrayList<>();
        moduleBenchmarks.values().forEach(benchmark -> {
            collected.addAll(benchmark.getTickBenchmarks());
        });
        lastTickBenchmarks = collected;
    }

    /**
     * Clear all module benchmarks at the start of each tick.
     *
     * <p>Called by GameLoop before system execution. Ensures that each tick
     * starts with a clean slate and benchmarks don't accumulate across ticks.
     */
    public void clearTickBenchmarks() {
        moduleBenchmarks.values().forEach(DefaultBenchmark::clearTickBenchmarks);
    }

    /**
     * Get benchmarks collected from the last tick.
     *
     * <p>This method is called by the metrics API to expose benchmark data
     * to clients. Returns an immutable snapshot of the last tick's benchmarks.
     *
     * @return list of benchmark metrics from the last tick
     */
    public List<BenchmarkMetrics> getLastTickBenchmarks() {
        return lastTickBenchmarks;
    }
}
