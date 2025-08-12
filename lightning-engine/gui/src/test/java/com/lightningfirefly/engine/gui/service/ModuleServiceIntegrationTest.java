package com.lightningfirefly.engine.gui.service;

import com.lightningfirefly.engine.gui.service.ModuleService.ModuleInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ModuleService against a live backend.
 *
 * <p>Run with:
 * <pre>
 * BACKEND_URL=http://localhost:8080 ./mvnw test -pl lightning-engine/gui \
 *     -Dtest=ModuleServiceIntegrationTest
 * </pre>
 */
@Tag("integration")
@DisplayName("ModuleService Live Backend Integration Tests")
@EnabledIfEnvironmentVariable(named = "BACKEND_URL", matches = ".+")
class ModuleServiceIntegrationTest {

    private ModuleService service;
    private String backendUrl;

    @BeforeEach
    void setUp() {
        backendUrl = System.getenv("BACKEND_URL");
        if (backendUrl == null || backendUrl.isEmpty()) {
            backendUrl = "http://localhost:8080";
        }
        service = new ModuleService(backendUrl);
    }

    @Test
    @DisplayName("listModules returns modules from backend")
    void listModules_returnsModulesFromBackend() throws Exception {
        System.out.println("Testing with backend URL: " + backendUrl);

        // Make the async call
        CompletableFuture<List<ModuleInfo>> future = service.listModules();

        // Wait for result with timeout
        List<ModuleInfo> modules = future.get(10, TimeUnit.SECONDS);

        System.out.println("Received " + modules.size() + " modules:");
        for (ModuleInfo module : modules) {
            System.out.println("  - " + module.name() +
                " (flag=" + module.flagComponent() +
                ", matches=" + module.enabledMatches() + ")");
        }

        // Verify we got some modules
        assertThat(modules)
            .as("Backend should return at least one module")
            .isNotEmpty();

        // Check for MoveModule specifically if it exists
        boolean hasMoveModule = modules.stream()
            .anyMatch(m -> m.name().equals("MoveModule"));

        if (hasMoveModule) {
            System.out.println("MoveModule found in response");
        } else {
            System.out.println("MoveModule NOT found - available: " +
                modules.stream().map(ModuleInfo::name).toList());
        }
    }

    @Test
    @DisplayName("listModules handles async correctly")
    void listModules_handlesAsyncCorrectly() throws Exception {
        System.out.println("Testing async behavior...");

        // Track callback execution
        final boolean[] callbackExecuted = {false};
        final List<ModuleInfo>[] receivedModules = new List[]{null};

        CompletableFuture<List<ModuleInfo>> future = service.listModules();

        future.thenAccept(modules -> {
            callbackExecuted[0] = true;
            receivedModules[0] = modules;
            System.out.println("Async callback executed with " + modules.size() + " modules");
        });

        // Wait a bit for async
        Thread.sleep(2000);

        assertThat(callbackExecuted[0])
            .as("Callback should have been executed")
            .isTrue();

        assertThat(receivedModules[0])
            .as("Modules should have been received")
            .isNotNull();

        System.out.println("Async test passed - callback executed correctly");
    }

    @Test
    @DisplayName("Multiple concurrent requests work correctly")
    void multipleConcurrentRequests_workCorrectly() throws Exception {
        System.out.println("Testing concurrent requests...");

        CompletableFuture<List<ModuleInfo>> future1 = service.listModules();
        CompletableFuture<List<ModuleInfo>> future2 = service.listModules();
        CompletableFuture<List<ModuleInfo>> future3 = service.listModules();

        List<ModuleInfo> result1 = future1.get(10, TimeUnit.SECONDS);
        List<ModuleInfo> result2 = future2.get(10, TimeUnit.SECONDS);
        List<ModuleInfo> result3 = future3.get(10, TimeUnit.SECONDS);

        System.out.println("Request 1: " + result1.size() + " modules");
        System.out.println("Request 2: " + result2.size() + " modules");
        System.out.println("Request 3: " + result3.size() + " modules");

        assertThat(result1.size()).isEqualTo(result2.size());
        assertThat(result2.size()).isEqualTo(result3.size());

        System.out.println("Concurrent requests test passed");
    }
}
