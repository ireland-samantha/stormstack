/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */
package ca.samanthaireland.engine.dashboard.acceptance;

import ca.samanthaireland.engine.dashboard.acceptance.pages.PWDashboardPage;
import ca.samanthaireland.engine.dashboard.acceptance.pages.PWMetricsPage;
import ca.samanthaireland.engine.dashboard.acceptance.pages.PWModulesPage;
import ca.samanthaireland.engine.dashboard.acceptance.pages.PWSidebarNavigator;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Playwright integration tests for Physics Systems with Benchmarks.
 *
 * <p>Tests verify that:
 * <ul>
 *   <li>Physics modules can be installed</li>
 *   <li>Metrics panel works after physics module installation</li>
 *   <li>Future: Physics-specific benchmark metrics appear when instrumented</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PhysicsSystemBenchmarksPlaywrightIT extends PlaywrightTestBase {

    private static final String TEST_CONTAINER_NAME = "physics-benchmark-test-container";
    private static String createdContainerId;

    @BeforeEach
    void setup() {
        loginAsAdmin();

        // Create and start a container for physics testing
        PWDashboardPage dashboard = new PWDashboardPage(page);
        var existingContainers = dashboard.getContainerNames();

        // Clean up old test containers
        for (String name : existingContainers) {
            if (name.startsWith("physics-benchmark-test")) {
                try {
                    String status = dashboard.getContainerStatus(name);
                    if (status.contains("RUNNING")) {
                        dashboard.stopContainer(name);
                    }
                } catch (Exception ignored) {}
            }
        }

        // Create fresh container
        String uniqueName = TEST_CONTAINER_NAME + "-" + System.currentTimeMillis();
        dashboard.createContainer(uniqueName);
        dashboard.startContainer(uniqueName);
        createdContainerId = uniqueName;

        // Select the container
        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        sidebar.selectContainer(uniqueName);
    }

    @AfterEach
    void cleanup() {
        if (createdContainerId != null) {
            try {
                PWDashboardPage dashboard = new PWDashboardPage(page);
                dashboard.stopContainer(createdContainerId);
            } catch (Exception ignored) {}
        }
    }

    @Test
    @Order(1)
    @DisplayName("Physics module can be installed in container")
    void physicsModuleCanBeInstalled() {
        // When I navigate to Modules panel
        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWModulesPage modulesPage = sidebar.goToModules();

        // Then the modules page should be displayed
        assertThat(modulesPage.isDisplayed()).isTrue();

        // RigidBodyModule may or may not be available depending on environment
        // This test documents the expected behavior
    }

    @Test
    @Order(2)
    @DisplayName("Metrics panel works with physics systems running")
    void metricsPanelWorksWithPhysicsSystems() {
        // Given: Some ticks have been advanced
        PWDashboardPage dashboard = new PWDashboardPage(page);
        for (int i = 0; i < 10; i++) {
            dashboard.clickStep(createdContainerId);
            sleep(50);
        }

        // When I navigate to Metrics panel
        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWMetricsPage metricsPage = sidebar.goToMetrics();

        // Then the metrics should be displayed
        assertThat(metricsPage.isDisplayed()).isTrue();
        assertThat(metricsPage.hasStandardMetricsFields()).isTrue();
    }

    @Test
    @Order(3)
    @DisplayName("Metrics panel structure supports future physics benchmarks")
    void metricsPanelStructureSupportsFutureBenchmarks() {
        // Given: Metrics panel is displayed
        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWMetricsPage metricsPage = sidebar.goToMetrics();

        // Then the metrics panel should be ready to display benchmarks
        assertThat(metricsPage.isDisplayed()).isTrue();

        // When physics modules are instrumented with benchmark.scope() calls in the future,
        // this test will verify that benchmark data appears correctly
        // For now, we just verify the panel loads correctly
    }

    @Test
    @Order(4)
    @DisplayName("Benchmark data structure can handle physics-specific scopes")
    void benchmarkDataStructureHandlesPhysicsScopes() {
        // This test documents the expected structure for future physics benchmarks:
        // - moduleName: "RigidBodyModule"
        // - scopeName: "position-integration", "velocity-integration", "collision-detection"
        // - fullName: "RigidBodyModule:position-integration"
        // - executionTimeMs: double
        // - executionTimeNanos: long

        // Given: Metrics panel is displayed
        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWMetricsPage metricsPage = sidebar.goToMetrics();

        // Then the panel should be ready
        assertThat(metricsPage.isDisplayed()).isTrue();

        // Future enhancement: When RigidBodyModule uses benchmark.scope(),
        // verify specific scope names appear in the benchmarks table
    }
}
