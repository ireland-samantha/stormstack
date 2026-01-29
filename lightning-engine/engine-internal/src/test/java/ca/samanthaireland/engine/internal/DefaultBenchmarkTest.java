package ca.samanthaireland.engine.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@ExtendWith(MockitoExtension.class)
class DefaultBenchmarkTest {

    @Test
    void scope_recordsExecutionTime() throws InterruptedException {
        DefaultBenchmark benchmark = new DefaultBenchmark("TestModule");

        try (var scope = benchmark.scope("test-operation")) {
            // Simulate work
            Thread.sleep(10);
        }

        List<BenchmarkMetrics> metrics = benchmark.getTickBenchmarks();
        assertThat(metrics).hasSize(1);
        assertThat(metrics.get(0).scopeName()).isEqualTo("test-operation");
        assertThat(metrics.get(0).moduleName()).isEqualTo("TestModule");
        assertThat(metrics.get(0).executionTimeNanos()).isGreaterThan(0);
        assertThat(metrics.get(0).executionTimeMs()).isGreaterThan(0);
    }

    @Test
    void scope_withMultipleScopes_recordsAll() {
        DefaultBenchmark benchmark = new DefaultBenchmark("TestModule");

        try (var scope = benchmark.scope("operation-1")) {}
        try (var scope = benchmark.scope("operation-2")) {}
        try (var scope = benchmark.scope("operation-3")) {}

        List<BenchmarkMetrics> metrics = benchmark.getTickBenchmarks();
        assertThat(metrics).hasSize(3);
        assertThat(metrics).extracting(BenchmarkMetrics::scopeName)
            .containsExactlyInAnyOrder("operation-1", "operation-2", "operation-3");
    }

    @Test
    void clearTickBenchmarks_clearsAllMetrics() {
        DefaultBenchmark benchmark = new DefaultBenchmark("TestModule");

        try (var scope = benchmark.scope("test-1")) {}
        try (var scope = benchmark.scope("test-2")) {}

        assertThat(benchmark.getTickBenchmarks()).hasSize(2);

        benchmark.clearTickBenchmarks();

        assertThat(benchmark.getTickBenchmarks()).isEmpty();
    }

    @Test
    void scope_withNullName_throwsNullPointerException() {
        DefaultBenchmark benchmark = new DefaultBenchmark("TestModule");

        assertThatThrownBy(() -> benchmark.scope(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_withNullModuleName_throwsNullPointerException() {
        assertThatThrownBy(() -> new DefaultBenchmark(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void scope_withSameName_overwritesPrevious() throws InterruptedException {
        DefaultBenchmark benchmark = new DefaultBenchmark("TestModule");

        try (var scope = benchmark.scope("same-operation")) {
            Thread.sleep(5);
        }
        try (var scope = benchmark.scope("same-operation")) {
            Thread.sleep(10);
        }

        List<BenchmarkMetrics> metrics = benchmark.getTickBenchmarks();
        assertThat(metrics).hasSize(1);
        assertThat(metrics.get(0).scopeName()).isEqualTo("same-operation");
    }

    @Test
    void scope_isThreadSafe() throws InterruptedException {
        DefaultBenchmark benchmark = new DefaultBenchmark("TestModule");
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try (var scope = benchmark.scope("thread-" + threadId)) {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();

        List<BenchmarkMetrics> metrics = benchmark.getTickBenchmarks();
        assertThat(metrics).hasSize(threadCount);
    }

    @Test
    void benchmarkMetrics_fullName_combinesModuleAndScope() {
        BenchmarkMetrics metrics = new BenchmarkMetrics("MyModule", "my-scope", 1000000L);

        assertThat(metrics.fullName()).isEqualTo("MyModule:my-scope");
    }

    @Test
    void benchmarkMetrics_executionTimeMs_convertsFromNanos() {
        BenchmarkMetrics metrics = new BenchmarkMetrics("Module", "scope", 1_500_000L);

        assertThat(metrics.executionTimeMs()).isEqualTo(1.5, within(0.001));
    }
}
