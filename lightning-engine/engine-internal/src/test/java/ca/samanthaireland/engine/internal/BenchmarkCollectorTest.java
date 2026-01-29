package ca.samanthaireland.engine.internal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkCollectorTest {

    @Test
    void collectTickBenchmarks_aggregatesFromAllModules() {
        BenchmarkCollector collector = new BenchmarkCollector();

        DefaultBenchmark module1 = new DefaultBenchmark("Module1");
        DefaultBenchmark module2 = new DefaultBenchmark("Module2");

        collector.registerModuleBenchmark("Module1", module1);
        collector.registerModuleBenchmark("Module2", module2);

        try (var scope = module1.scope("op1")) {}
        try (var scope = module2.scope("op2")) {}

        collector.collectTickBenchmarks();

        List<BenchmarkMetrics> metrics = collector.getLastTickBenchmarks();
        assertThat(metrics).hasSize(2);
        assertThat(metrics).extracting(BenchmarkMetrics::moduleName)
            .containsExactlyInAnyOrder("Module1", "Module2");
    }

    @Test
    void clearTickBenchmarks_clearsAllModuleBenchmarks() {
        BenchmarkCollector collector = new BenchmarkCollector();

        DefaultBenchmark module1 = new DefaultBenchmark("Module1");
        collector.registerModuleBenchmark("Module1", module1);

        try (var scope = module1.scope("op1")) {}
        collector.collectTickBenchmarks();

        assertThat(collector.getLastTickBenchmarks()).hasSize(1);

        collector.clearTickBenchmarks();

        assertThat(module1.getTickBenchmarks()).isEmpty();
    }

    @Test
    void collectTickBenchmarks_withNoModules_returnsEmptyList() {
        BenchmarkCollector collector = new BenchmarkCollector();

        collector.collectTickBenchmarks();

        assertThat(collector.getLastTickBenchmarks()).isEmpty();
    }

    @Test
    void registerModuleBenchmark_allowsMultipleModules() {
        BenchmarkCollector collector = new BenchmarkCollector();

        collector.registerModuleBenchmark("Module1", new DefaultBenchmark("Module1"));
        collector.registerModuleBenchmark("Module2", new DefaultBenchmark("Module2"));
        collector.registerModuleBenchmark("Module3", new DefaultBenchmark("Module3"));

        // No exception thrown - test passes if we reach here
        assertThat(collector).isNotNull();
    }

    @Test
    void collectTickBenchmarks_preservesBenchmarksUntilNextCollection() {
        BenchmarkCollector collector = new BenchmarkCollector();

        DefaultBenchmark module1 = new DefaultBenchmark("Module1");
        collector.registerModuleBenchmark("Module1", module1);

        // Tick 1: Create benchmark
        try (var scope = module1.scope("op-1")) {}
        collector.collectTickBenchmarks();
        List<BenchmarkMetrics> tick1Metrics = collector.getLastTickBenchmarks();
        assertThat(tick1Metrics).hasSize(1);
        assertThat(tick1Metrics.get(0).scopeName()).isEqualTo("op-1");

        // Tick 2: Different benchmark
        collector.clearTickBenchmarks();
        try (var scope = module1.scope("op-2")) {}
        collector.collectTickBenchmarks();
        List<BenchmarkMetrics> tick2Metrics = collector.getLastTickBenchmarks();

        // Should only have op-2, not op-1
        assertThat(tick2Metrics).hasSize(1);
        assertThat(tick2Metrics.get(0).scopeName()).isEqualTo("op-2");
    }
}
