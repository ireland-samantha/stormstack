package ca.samanthaireland.engine.internal;

import ca.samanthaireland.engine.core.benchmark.Benchmark;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link Benchmark}.
 *
 * <p>Thread-safe implementation using ConcurrentHashMap for benchmark storage.
 * Benchmarks are collected per-tick and can be retrieved for metrics reporting.
 *
 * <p>SOLID compliance:
 * <ul>
 *   <li>SRP: Only responsible for benchmark timing collection</li>
 *   <li>DIP: Implements Benchmark abstraction</li>
 * </ul>
 *
 * <p>Performance characteristics:
 * <ul>
 *   <li>Scope creation: O(1) - simple nanoTime() call</li>
 *   <li>Scope closing: O(1) - HashMap put operation</li>
 *   <li>Memory: ~100 bytes base + ~50 bytes per active scope</li>
 * </ul>
 */
@Slf4j
public class DefaultBenchmark implements Benchmark {

    private final String moduleName;
    private final ConcurrentHashMap<String, Long> currentTickBenchmarks = new ConcurrentHashMap<>();

    /**
     * Create a benchmark collector for a specific module.
     *
     * @param moduleName the name of the module (must not be null)
     * @throws NullPointerException if moduleName is null
     */
    public DefaultBenchmark(String moduleName) {
        this.moduleName = Objects.requireNonNull(moduleName, "moduleName must not be null");
    }

    @Override
    public BenchmarkScope scope(String name) {
        Objects.requireNonNull(name, "scope name must not be null");
        long startTime = System.nanoTime();
        return () -> {
            long endTime = System.nanoTime();
            long duration = endTime - startTime;
            currentTickBenchmarks.put(name, duration);
            log.trace("Benchmark {}:{} = {}ns ({} ms)", moduleName, name, duration, duration / 1_000_000.0);
        };
    }

    /**
     * Get all benchmarks collected during the current tick.
     *
     * <p>This method is called by GameLoop after all systems have executed.
     *
     * @return list of benchmark metrics
     */
    public List<BenchmarkMetrics> getTickBenchmarks() {
        List<BenchmarkMetrics> metrics = new ArrayList<>(currentTickBenchmarks.size());
        currentTickBenchmarks.forEach((scopeName, durationNanos) -> {
            metrics.add(new BenchmarkMetrics(moduleName, scopeName, durationNanos));
        });
        return metrics;
    }

    /**
     * Clear all benchmarks for the current tick.
     *
     * <p>Called by GameLoop at the start of each tick to reset state.
     */
    public void clearTickBenchmarks() {
        currentTickBenchmarks.clear();
    }
}
