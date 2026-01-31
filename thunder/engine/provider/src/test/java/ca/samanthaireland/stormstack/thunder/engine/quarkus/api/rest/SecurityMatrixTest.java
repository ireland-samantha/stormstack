/*
 * Copyright (c) 2026 Samantha Ireland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ca.samanthaireland.stormstack.thunder.engine.quarkus.api.rest;

import ca.samanthaireland.stormstack.thunder.auth.quarkus.annotation.Scopes;
import ca.samanthaireland.stormstack.thunder.auth.quarkus.security.ScopeMatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import jakarta.ws.rs.*;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security matrix tests for the Lightning Engine REST API.
 *
 * <p>This test verifies that all REST endpoints have appropriate scope annotations
 * and that the scope matching logic works correctly with wildcards.
 *
 * <h2>Engine Service Scope Matrix</h2>
 * <table>
 *   <tr><th>Scope</th><th>Description</th></tr>
 *   <tr><td>engine.container.create</td><td>Create containers</td></tr>
 *   <tr><td>engine.container.read</td><td>View containers and stats</td></tr>
 *   <tr><td>engine.container.delete</td><td>Delete containers</td></tr>
 *   <tr><td>engine.container.lifecycle</td><td>Start/stop/pause/resume</td></tr>
 *   <tr><td>engine.match.create</td><td>Create matches</td></tr>
 *   <tr><td>engine.match.read</td><td>View matches</td></tr>
 *   <tr><td>engine.match.delete</td><td>Delete matches</td></tr>
 *   <tr><td>engine.command.read</td><td>View command queue</td></tr>
 *   <tr><td>engine.command.submit</td><td>Submit commands</td></tr>
 *   <tr><td>engine.snapshot.read</td><td>View snapshots</td></tr>
 *   <tr><td>engine.snapshot.record</td><td>Record snapshots</td></tr>
 *   <tr><td>engine.snapshot.delete</td><td>Clear snapshot history</td></tr>
 *   <tr><td>engine.module.read</td><td>View modules</td></tr>
 *   <tr><td>engine.module.reload</td><td>Reload modules</td></tr>
 *   <tr><td>engine.ai.read</td><td>View AI backends</td></tr>
 *   <tr><td>engine.ai.manage</td><td>Enable/disable AI</td></tr>
 *   <tr><td>engine.player.read</td><td>View players</td></tr>
 *   <tr><td>engine.player.manage</td><td>Connect/disconnect players</td></tr>
 *   <tr><td>engine.session.read</td><td>View sessions</td></tr>
 *   <tr><td>engine.session.manage</td><td>Create/revoke sessions</td></tr>
 *   <tr><td>engine.metrics.read</td><td>View metrics</td></tr>
 *   <tr><td>engine.simulation.read</td><td>View simulation status</td></tr>
 *   <tr><td>engine.simulation.control</td><td>Advance ticks, control simulation</td></tr>
 * </table>
 */
@DisplayName("Security Matrix - Engine Service")
class SecurityMatrixTest {

    @Nested
    @DisplayName("ContainerLifecycleResource")
    class ContainerLifecycleResourceTests {

        @Test
        @DisplayName("GET /api/containers requires engine.container.read")
        void getAllContainers_requiresReadScope() {
            assertMethodHasScope(ContainerLifecycleResource.class, "getAllContainers", "engine.container.read");
        }

        @Test
        @DisplayName("POST /api/containers requires engine.container.create")
        void createContainer_requiresCreateScope() {
            assertMethodHasScope(ContainerLifecycleResource.class, "createContainer", "engine.container.create");
        }

        @Test
        @DisplayName("GET /api/containers/{id} requires engine.container.read")
        void getContainer_requiresReadScope() {
            assertMethodHasScope(ContainerLifecycleResource.class, "getContainer", "engine.container.read");
        }

        @Test
        @DisplayName("DELETE /api/containers/{id} requires engine.container.delete")
        void deleteContainer_requiresDeleteScope() {
            assertMethodHasScope(ContainerLifecycleResource.class, "deleteContainer", "engine.container.delete");
        }

        @Test
        @DisplayName("POST /api/containers/{id}/start requires engine.container.lifecycle")
        void startContainer_requiresLifecycleScope() {
            assertMethodHasScope(ContainerLifecycleResource.class, "startContainer", "engine.container.lifecycle");
        }

        @Test
        @DisplayName("POST /api/containers/{id}/stop requires engine.container.lifecycle")
        void stopContainer_requiresLifecycleScope() {
            assertMethodHasScope(ContainerLifecycleResource.class, "stopContainer", "engine.container.lifecycle");
        }
    }

    @Nested
    @DisplayName("ContainerMatchResource")
    class ContainerMatchResourceTests {

        @Test
        @DisplayName("GET /api/containers/{id}/matches requires engine.match.read")
        void getMatches_requiresReadScope() {
            assertMethodHasScope(ContainerMatchResource.class, "getMatches", "engine.match.read");
        }

        @Test
        @DisplayName("POST /api/containers/{id}/matches requires engine.match.create")
        void createMatch_requiresCreateScope() {
            assertMethodHasScope(ContainerMatchResource.class, "createMatch", "engine.match.create");
        }

        @Test
        @DisplayName("GET /api/containers/{id}/matches/{matchId} requires engine.match.read")
        void getMatch_requiresReadScope() {
            assertMethodHasScope(ContainerMatchResource.class, "getMatch", "engine.match.read");
        }

        @Test
        @DisplayName("DELETE /api/containers/{id}/matches/{matchId} requires engine.match.delete")
        void deleteMatch_requiresDeleteScope() {
            assertMethodHasScope(ContainerMatchResource.class, "deleteMatch", "engine.match.delete");
        }
    }

    @Nested
    @DisplayName("ContainerSnapshotResource")
    class ContainerSnapshotResourceTests {

        @Test
        @DisplayName("GET .../snapshot requires engine.snapshot.read")
        void getMatchSnapshot_requiresReadScope() {
            assertMethodHasScope(ContainerSnapshotResource.class, "getMatchSnapshot", "engine.snapshot.read");
        }

        @Test
        @DisplayName("GET .../snapshots/delta requires engine.snapshot.read")
        void getDeltaSnapshot_requiresReadScope() {
            assertMethodHasScope(ContainerSnapshotResource.class, "getDeltaSnapshot", "engine.snapshot.read");
        }

        @Test
        @DisplayName("POST .../snapshots/record requires engine.snapshot.record")
        void recordSnapshot_requiresRecordScope() {
            assertMethodHasScope(ContainerSnapshotResource.class, "recordSnapshot", "engine.snapshot.record");
        }

        @Test
        @DisplayName("DELETE .../snapshots/history requires engine.snapshot.delete")
        void clearSnapshotHistory_requiresDeleteScope() {
            assertMethodHasScope(ContainerSnapshotResource.class, "clearSnapshotHistory", "engine.snapshot.delete");
        }
    }

    @Nested
    @DisplayName("ContainerModuleResource")
    class ContainerModuleResourceTests {

        @Test
        @DisplayName("GET /api/containers/{id}/modules requires engine.module.read")
        void getModules_requiresReadScope() {
            assertMethodHasScope(ContainerModuleResource.class, "getModules", "engine.module.read");
        }

        @Test
        @DisplayName("POST /api/containers/{id}/modules/reload requires engine.module.reload")
        void reloadModules_requiresReloadScope() {
            assertMethodHasScope(ContainerModuleResource.class, "reloadModules", "engine.module.reload");
        }
    }

    @Nested
    @DisplayName("ContainerCommandResource")
    class ContainerCommandResourceTests {

        @Test
        @DisplayName("GET .../commands requires engine.command.read")
        void getCommands_requiresReadScope() {
            assertMethodHasScope(ContainerCommandResource.class, "getCommands", "engine.command.read");
        }

        @Test
        @DisplayName("POST .../commands requires engine.command.submit")
        void enqueueCommand_requiresSubmitScope() {
            assertMethodHasScope(ContainerCommandResource.class, "enqueueCommand", "engine.command.submit");
        }
    }

    @Nested
    @DisplayName("ModuleResource")
    class ModuleResourceTests {

        @Test
        @DisplayName("GET /api/modules requires engine.module.read")
        void getAllModules_requiresReadScope() {
            assertMethodHasScope(ModuleResource.class, "getAllModules", "engine.module.read");
        }

        @Test
        @DisplayName("POST /api/modules/upload requires engine.module.manage")
        void uploadModule_requiresManageScope() {
            assertMethodHasScope(ModuleResource.class, "uploadModule", "engine.module.manage");
        }

        @Test
        @DisplayName("POST /api/modules/reload requires engine.module.manage")
        void reloadModules_requiresManageScope() {
            assertMethodHasScope(ModuleResource.class, "reloadModules", "engine.module.manage");
        }
    }

    @Nested
    @DisplayName("SimulationControlResource")
    class SimulationControlResourceTests {

        @Test
        @DisplayName("POST .../tick/advance requires engine.simulation.control")
        void advanceTick_requiresControlScope() {
            assertMethodHasScope(SimulationControlResource.class, "advanceTick", "engine.simulation.control");
        }

        @Test
        @DisplayName("GET .../tick requires engine.simulation.read")
        void getTick_requiresReadScope() {
            assertMethodHasScope(SimulationControlResource.class, "getTick", "engine.simulation.read");
        }
    }

    @Nested
    @DisplayName("Wildcard Scope Matching")
    class WildcardScopeMatchingTests {

        @ParameterizedTest
        @CsvSource({
            "engine.container.create, engine.*, true",
            "engine.container.create, engine.container.*, true",
            "engine.container.create, *, true",
            "engine.container.create, auth.*, false",
            "engine.container.create, engine.match.*, false",
            "engine.match.read, engine.*, true",
            "engine.snapshot.record, engine.snapshot.*, true",
            "engine.ai.manage, engine.ai.*, true",
            "engine.simulation.control, engine.*, true"
        })
        @DisplayName("wildcard scope matching")
        void wildcardMatching(String requiredScope, String userScope, boolean shouldMatch) {
            Set<String> userScopes = Set.of(userScope);
            assertThat(ScopeMatcher.matches(userScopes, requiredScope))
                .as("User with '%s' accessing '%s'", userScope, requiredScope)
                .isEqualTo(shouldMatch);
        }

        @Test
        @DisplayName("admin with * scope has access to all engine endpoints")
        void adminWithFullWildcard_hasAccessToAll() {
            Set<String> adminScopes = Set.of("*");

            // Container scopes
            assertThat(ScopeMatcher.matches(adminScopes, "engine.container.create")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "engine.container.read")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "engine.container.delete")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "engine.container.lifecycle")).isTrue();

            // Match scopes
            assertThat(ScopeMatcher.matches(adminScopes, "engine.match.create")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "engine.match.read")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "engine.match.delete")).isTrue();

            // Snapshot scopes
            assertThat(ScopeMatcher.matches(adminScopes, "engine.snapshot.read")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "engine.snapshot.record")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "engine.snapshot.delete")).isTrue();

            // Other scopes
            assertThat(ScopeMatcher.matches(adminScopes, "engine.command.submit")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "engine.ai.manage")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "engine.simulation.control")).isTrue();
        }

        @Test
        @DisplayName("engine operator with engine.* has access to all engine endpoints")
        void engineOperator_hasAccessToAllEngine() {
            Set<String> operatorScopes = Set.of("engine.*");

            // Should have access to all engine operations
            assertThat(ScopeMatcher.matches(operatorScopes, "engine.container.create")).isTrue();
            assertThat(ScopeMatcher.matches(operatorScopes, "engine.match.read")).isTrue();
            assertThat(ScopeMatcher.matches(operatorScopes, "engine.snapshot.record")).isTrue();
            assertThat(ScopeMatcher.matches(operatorScopes, "engine.ai.manage")).isTrue();

            // Should NOT have access to other services
            assertThat(ScopeMatcher.matches(operatorScopes, "auth.user.read")).isFalse();
            assertThat(ScopeMatcher.matches(operatorScopes, "control-plane.module.upload")).isFalse();
        }

        @Test
        @DisplayName("viewer with read-only scopes cannot modify resources")
        void viewerReadOnly_cannotModify() {
            Set<String> viewerScopes = Set.of(
                "engine.container.read",
                "engine.match.read",
                "engine.snapshot.read",
                "engine.command.read",
                "engine.module.read",
                "engine.ai.read"
            );

            // Should have read access
            assertThat(ScopeMatcher.matches(viewerScopes, "engine.container.read")).isTrue();
            assertThat(ScopeMatcher.matches(viewerScopes, "engine.match.read")).isTrue();

            // Should NOT have write/modify access
            assertThat(ScopeMatcher.matches(viewerScopes, "engine.container.create")).isFalse();
            assertThat(ScopeMatcher.matches(viewerScopes, "engine.container.delete")).isFalse();
            assertThat(ScopeMatcher.matches(viewerScopes, "engine.match.create")).isFalse();
            assertThat(ScopeMatcher.matches(viewerScopes, "engine.command.submit")).isFalse();
            assertThat(ScopeMatcher.matches(viewerScopes, "engine.ai.manage")).isFalse();
        }
    }

    @Nested
    @DisplayName("Health Endpoint")
    class HealthEndpointTests {

        @Test
        @DisplayName("GET /api/health has no scope requirement (public)")
        void healthEndpoint_isPublic() {
            // Verify no @Scopes annotation on health endpoint
            Method healthMethod = findMethod(HealthResource.class, "health");
            assertThat(healthMethod).isNotNull();
            assertThat(healthMethod.getAnnotation(Scopes.class)).isNull();
            assertThat(HealthResource.class.getAnnotation(Scopes.class)).isNull();
        }
    }

    // Helper methods

    private void assertMethodHasScope(Class<?> resourceClass, String methodName, String expectedScope) {
        Method method = findMethod(resourceClass, methodName);
        assertThat(method)
            .as("Method %s.%s should exist", resourceClass.getSimpleName(), methodName)
            .isNotNull();

        Scopes scopes = method.getAnnotation(Scopes.class);
        if (scopes == null) {
            // Check class-level annotation
            scopes = resourceClass.getAnnotation(Scopes.class);
        }

        assertThat(scopes)
            .as("Method %s.%s should have @Scopes annotation", resourceClass.getSimpleName(), methodName)
            .isNotNull();

        Set<String> actualScopes = new HashSet<>(Arrays.asList(scopes.value()));
        assertThat(actualScopes)
            .as("Method %s.%s should require scope '%s'", resourceClass.getSimpleName(), methodName, expectedScope)
            .contains(expectedScope);
    }

    private Method findMethod(Class<?> clazz, String methodName) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }
}
