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

package ca.samanthaireland.lightning.controlplane.provider.http;

import ca.samanthaireland.lightning.auth.quarkus.annotation.Scopes;
import ca.samanthaireland.lightning.auth.quarkus.security.ScopeMatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security matrix tests for the Lightning Control Plane REST API.
 *
 * <p>This test verifies that all REST endpoints have appropriate scope annotations
 * and that the scope matching logic works correctly with wildcards.
 *
 * <h2>Control Plane Scope Matrix</h2>
 * <table>
 *   <tr><th>Scope</th><th>Description</th></tr>
 *   <tr><td>control-plane.cluster.read</td><td>View cluster status and nodes</td></tr>
 *   <tr><td>control-plane.node.register</td><td>Register nodes and heartbeats</td></tr>
 *   <tr><td>control-plane.node.manage</td><td>Drain/deregister nodes</td></tr>
 *   <tr><td>control-plane.match.create</td><td>Create matches</td></tr>
 *   <tr><td>control-plane.match.read</td><td>View matches</td></tr>
 *   <tr><td>control-plane.match.update</td><td>Update match (players, finish)</td></tr>
 *   <tr><td>control-plane.match.delete</td><td>Delete matches</td></tr>
 *   <tr><td>control-plane.module.upload</td><td>Upload modules</td></tr>
 *   <tr><td>control-plane.module.read</td><td>View/download modules</td></tr>
 *   <tr><td>control-plane.module.delete</td><td>Delete modules</td></tr>
 *   <tr><td>control-plane.module.distribute</td><td>Distribute modules to nodes</td></tr>
 *   <tr><td>control-plane.deploy.create</td><td>Deploy matches</td></tr>
 *   <tr><td>control-plane.deploy.read</td><td>View deployments</td></tr>
 *   <tr><td>control-plane.deploy.delete</td><td>Undeploy matches</td></tr>
 *   <tr><td>control-plane.autoscaler.read</td><td>View autoscaler status</td></tr>
 *   <tr><td>control-plane.autoscaler.manage</td><td>Configure autoscaler</td></tr>
 *   <tr><td>control-plane.dashboard.read</td><td>View dashboard</td></tr>
 *   <tr><td>control-plane.node.proxy</td><td>Proxy requests to nodes</td></tr>
 * </table>
 */
@DisplayName("Security Matrix - Control Plane")
class SecurityMatrixTest {

    @Nested
    @DisplayName("NodeResource")
    class NodeResourceTests {

        @Test
        @DisplayName("POST /api/nodes/register requires control-plane.node.register")
        void register_requiresRegisterScope() {
            assertMethodHasScope(NodeResource.class, "register", "control-plane.node.register");
        }

        @Test
        @DisplayName("PUT /api/nodes/{nodeId}/heartbeat requires control-plane.node.register")
        void heartbeat_requiresRegisterScope() {
            assertMethodHasScope(NodeResource.class, "heartbeat", "control-plane.node.register");
        }

        @Test
        @DisplayName("POST /api/nodes/{nodeId}/drain requires control-plane.node.manage")
        void drain_requiresManageScope() {
            assertMethodHasScope(NodeResource.class, "drain", "control-plane.node.manage");
        }

        @Test
        @DisplayName("DELETE /api/nodes/{nodeId} requires control-plane.node.manage")
        void deregister_requiresManageScope() {
            assertMethodHasScope(NodeResource.class, "deregister", "control-plane.node.manage");
        }
    }

    @Nested
    @DisplayName("NodeProxyResource")
    class NodeProxyResourceTests {

        @Test
        @DisplayName("GET /api/nodes/{nodeId}/proxy/{path} requires control-plane.node.proxy")
        void proxyGet_requiresProxyScope() {
            assertMethodHasScope(NodeProxyResource.class, "proxyGet", "control-plane.node.proxy");
        }

        @Test
        @DisplayName("POST /api/nodes/{nodeId}/proxy/{path} requires control-plane.node.proxy")
        void proxyPost_requiresProxyScope() {
            assertMethodHasScope(NodeProxyResource.class, "proxyPost", "control-plane.node.proxy");
        }

        @Test
        @DisplayName("PUT /api/nodes/{nodeId}/proxy/{path} requires control-plane.node.proxy")
        void proxyPut_requiresProxyScope() {
            assertMethodHasScope(NodeProxyResource.class, "proxyPut", "control-plane.node.proxy");
        }

        @Test
        @DisplayName("DELETE /api/nodes/{nodeId}/proxy/{path} requires control-plane.node.proxy")
        void proxyDelete_requiresProxyScope() {
            assertMethodHasScope(NodeProxyResource.class, "proxyDelete", "control-plane.node.proxy");
        }
    }

    @Nested
    @DisplayName("ClusterResource")
    class ClusterResourceTests {

        @Test
        @DisplayName("GET /api/cluster/nodes requires control-plane.cluster.read")
        void getAllNodes_requiresReadScope() {
            assertMethodHasScope(ClusterResource.class, "getAllNodes", "control-plane.cluster.read");
        }

        @Test
        @DisplayName("GET /api/cluster/nodes/{nodeId} requires control-plane.cluster.read")
        void getNode_requiresReadScope() {
            assertMethodHasScope(ClusterResource.class, "getNode", "control-plane.cluster.read");
        }

        @Test
        @DisplayName("GET /api/cluster/status requires control-plane.cluster.read")
        void getClusterStatus_requiresReadScope() {
            assertMethodHasScope(ClusterResource.class, "getClusterStatus", "control-plane.cluster.read");
        }
    }

    @Nested
    @DisplayName("MatchResource")
    class MatchResourceTests {

        @Test
        @DisplayName("POST /api/matches/create requires control-plane.match.create")
        void create_requiresCreateScope() {
            assertMethodHasScope(MatchResource.class, "create", "control-plane.match.create");
        }

        @Test
        @DisplayName("GET /api/matches/{matchId} requires control-plane.match.read")
        void getById_requiresReadScope() {
            assertMethodHasScope(MatchResource.class, "getById", "control-plane.match.read");
        }

        @Test
        @DisplayName("GET /api/matches requires control-plane.match.read")
        void list_requiresReadScope() {
            assertMethodHasScope(MatchResource.class, "list", "control-plane.match.read");
        }

        @Test
        @DisplayName("DELETE /api/matches/{matchId} requires control-plane.match.delete")
        void delete_requiresDeleteScope() {
            assertMethodHasScope(MatchResource.class, "delete", "control-plane.match.delete");
        }

        @Test
        @DisplayName("POST /api/matches/{matchId}/finish requires control-plane.match.update")
        void finish_requiresUpdateScope() {
            assertMethodHasScope(MatchResource.class, "finish", "control-plane.match.update");
        }

        @Test
        @DisplayName("PUT /api/matches/{matchId}/players requires control-plane.match.update")
        void updatePlayerCount_requiresUpdateScope() {
            assertMethodHasScope(MatchResource.class, "updatePlayerCount", "control-plane.match.update");
        }
    }

    @Nested
    @DisplayName("ModuleResource")
    class ModuleResourceTests {

        @Test
        @DisplayName("POST /api/modules requires control-plane.module.upload")
        void uploadModule_requiresUploadScope() {
            assertMethodHasScope(ModuleResource.class, "uploadModule", "control-plane.module.upload");
        }

        @Test
        @DisplayName("GET /api/modules requires control-plane.module.read")
        void listModules_requiresReadScope() {
            assertMethodHasScope(ModuleResource.class, "listModules", "control-plane.module.read");
        }

        @Test
        @DisplayName("GET /api/modules/{name} requires control-plane.module.read")
        void listModuleVersions_requiresReadScope() {
            assertMethodHasScope(ModuleResource.class, "listModuleVersions", "control-plane.module.read");
        }

        @Test
        @DisplayName("GET /api/modules/{name}/{version} requires control-plane.module.read")
        void getModule_requiresReadScope() {
            assertMethodHasScope(ModuleResource.class, "getModule", "control-plane.module.read");
        }

        @Test
        @DisplayName("GET /api/modules/{name}/{version}/download requires control-plane.module.read")
        void downloadModule_requiresReadScope() {
            assertMethodHasScope(ModuleResource.class, "downloadModule", "control-plane.module.read");
        }

        @Test
        @DisplayName("DELETE /api/modules/{name}/{version} requires control-plane.module.delete")
        void deleteModule_requiresDeleteScope() {
            assertMethodHasScope(ModuleResource.class, "deleteModule", "control-plane.module.delete");
        }

        @Test
        @DisplayName("POST /api/modules/{name}/{version}/distribute requires control-plane.module.distribute")
        void distributeToAllNodes_requiresDistributeScope() {
            assertMethodHasScope(ModuleResource.class, "distributeToAllNodes", "control-plane.module.distribute");
        }

        @Test
        @DisplayName("POST /api/modules/{name}/{version}/distribute/{nodeId} requires control-plane.module.distribute")
        void distributeToNode_requiresDistributeScope() {
            assertMethodHasScope(ModuleResource.class, "distributeToNode", "control-plane.module.distribute");
        }
    }

    @Nested
    @DisplayName("DeployResource")
    class DeployResourceTests {

        @Test
        @DisplayName("POST /api/v1/deploy requires control-plane.deploy.create")
        void deploy_requiresCreateScope() {
            assertMethodHasScope(DeployResource.class, "deploy", "control-plane.deploy.create");
        }

        @Test
        @DisplayName("GET /api/v1/deploy/{matchId} requires control-plane.deploy.read")
        void getStatus_requiresReadScope() {
            assertMethodHasScope(DeployResource.class, "getStatus", "control-plane.deploy.read");
        }

        @Test
        @DisplayName("DELETE /api/v1/deploy/{matchId} requires control-plane.deploy.delete")
        void undeploy_requiresDeleteScope() {
            assertMethodHasScope(DeployResource.class, "undeploy", "control-plane.deploy.delete");
        }
    }

    @Nested
    @DisplayName("AutoscalerResource")
    class AutoscalerResourceTests {

        @Test
        @DisplayName("GET /api/autoscaler/recommendation requires control-plane.autoscaler.read")
        void getRecommendation_requiresReadScope() {
            assertMethodHasScope(AutoscalerResource.class, "getRecommendation", "control-plane.autoscaler.read");
        }

        @Test
        @DisplayName("POST /api/autoscaler/acknowledge requires control-plane.autoscaler.manage")
        void acknowledgeScalingAction_requiresManageScope() {
            assertMethodHasScope(AutoscalerResource.class, "acknowledgeScalingAction", "control-plane.autoscaler.manage");
        }

        @Test
        @DisplayName("GET /api/autoscaler/status requires control-plane.autoscaler.read")
        void getStatus_requiresReadScope() {
            assertMethodHasScope(AutoscalerResource.class, "getStatus", "control-plane.autoscaler.read");
        }
    }

    @Nested
    @DisplayName("DashboardResource")
    class DashboardResourceTests {

        @Test
        @DisplayName("GET /api/dashboard/overview requires control-plane.dashboard.read")
        void getOverview_requiresReadScope() {
            assertMethodHasScope(DashboardResource.class, "getOverview", "control-plane.dashboard.read");
        }

        @Test
        @DisplayName("GET /api/dashboard/nodes requires control-plane.dashboard.read")
        void getNodes_requiresReadScope() {
            assertMethodHasScope(DashboardResource.class, "getNodes", "control-plane.dashboard.read");
        }

        @Test
        @DisplayName("GET /api/dashboard/matches requires control-plane.dashboard.read")
        void getMatches_requiresReadScope() {
            assertMethodHasScope(DashboardResource.class, "getMatches", "control-plane.dashboard.read");
        }
    }

    @Nested
    @DisplayName("Wildcard Scope Matching")
    class WildcardScopeMatchingTests {

        @ParameterizedTest
        @CsvSource({
            "control-plane.cluster.read, control-plane.*, true",
            "control-plane.cluster.read, control-plane.cluster.*, true",
            "control-plane.cluster.read, *, true",
            "control-plane.cluster.read, auth.*, false",
            "control-plane.cluster.read, engine.*, false",
            "control-plane.node.register, control-plane.*, true",
            "control-plane.node.manage, control-plane.node.*, true",
            "control-plane.node.proxy, control-plane.node.*, true",
            "control-plane.match.create, control-plane.match.*, true",
            "control-plane.module.upload, control-plane.module.*, true",
            "control-plane.deploy.create, control-plane.deploy.*, true",
            "control-plane.autoscaler.read, control-plane.autoscaler.*, true",
            "control-plane.dashboard.read, control-plane.dashboard.*, true"
        })
        @DisplayName("wildcard scope matching")
        void wildcardMatching(String requiredScope, String userScope, boolean shouldMatch) {
            Set<String> userScopes = Set.of(userScope);
            assertThat(ScopeMatcher.matches(userScopes, requiredScope))
                .as("User with '%s' accessing '%s'", userScope, requiredScope)
                .isEqualTo(shouldMatch);
        }

        @Test
        @DisplayName("admin with * scope has access to all control-plane endpoints")
        void adminWithFullWildcard_hasAccessToAll() {
            Set<String> adminScopes = Set.of("*");

            // Cluster scopes
            assertThat(ScopeMatcher.matches(adminScopes, "control-plane.cluster.read")).isTrue();

            // Node scopes
            assertThat(ScopeMatcher.matches(adminScopes, "control-plane.node.register")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "control-plane.node.manage")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "control-plane.node.proxy")).isTrue();

            // Match scopes
            assertThat(ScopeMatcher.matches(adminScopes, "control-plane.match.create")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "control-plane.match.read")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "control-plane.match.update")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "control-plane.match.delete")).isTrue();

            // Module scopes
            assertThat(ScopeMatcher.matches(adminScopes, "control-plane.module.upload")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "control-plane.module.read")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "control-plane.module.delete")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "control-plane.module.distribute")).isTrue();

            // Deploy scopes
            assertThat(ScopeMatcher.matches(adminScopes, "control-plane.deploy.create")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "control-plane.deploy.read")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "control-plane.deploy.delete")).isTrue();

            // Autoscaler scopes
            assertThat(ScopeMatcher.matches(adminScopes, "control-plane.autoscaler.read")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "control-plane.autoscaler.manage")).isTrue();

            // Dashboard scopes
            assertThat(ScopeMatcher.matches(adminScopes, "control-plane.dashboard.read")).isTrue();
        }

        @Test
        @DisplayName("control-plane operator with control-plane.* has access to all control-plane endpoints")
        void controlPlaneOperator_hasAccessToAllControlPlane() {
            Set<String> operatorScopes = Set.of("control-plane.*");

            // Should have access to all control-plane operations
            assertThat(ScopeMatcher.matches(operatorScopes, "control-plane.cluster.read")).isTrue();
            assertThat(ScopeMatcher.matches(operatorScopes, "control-plane.node.register")).isTrue();
            assertThat(ScopeMatcher.matches(operatorScopes, "control-plane.match.create")).isTrue();
            assertThat(ScopeMatcher.matches(operatorScopes, "control-plane.module.upload")).isTrue();
            assertThat(ScopeMatcher.matches(operatorScopes, "control-plane.deploy.create")).isTrue();
            assertThat(ScopeMatcher.matches(operatorScopes, "control-plane.autoscaler.manage")).isTrue();
            assertThat(ScopeMatcher.matches(operatorScopes, "control-plane.dashboard.read")).isTrue();

            // Should NOT have access to other services
            assertThat(ScopeMatcher.matches(operatorScopes, "engine.container.create")).isFalse();
            assertThat(ScopeMatcher.matches(operatorScopes, "auth.user.create")).isFalse();
        }

        @Test
        @DisplayName("module manager with control-plane.module.* can manage modules only")
        void moduleManager_canManageModulesOnly() {
            Set<String> moduleManagerScopes = Set.of("control-plane.module.*");

            // Should have all module operations
            assertThat(ScopeMatcher.matches(moduleManagerScopes, "control-plane.module.upload")).isTrue();
            assertThat(ScopeMatcher.matches(moduleManagerScopes, "control-plane.module.read")).isTrue();
            assertThat(ScopeMatcher.matches(moduleManagerScopes, "control-plane.module.delete")).isTrue();
            assertThat(ScopeMatcher.matches(moduleManagerScopes, "control-plane.module.distribute")).isTrue();

            // Should NOT have other control-plane operations
            assertThat(ScopeMatcher.matches(moduleManagerScopes, "control-plane.cluster.read")).isFalse();
            assertThat(ScopeMatcher.matches(moduleManagerScopes, "control-plane.node.register")).isFalse();
            assertThat(ScopeMatcher.matches(moduleManagerScopes, "control-plane.match.create")).isFalse();
            assertThat(ScopeMatcher.matches(moduleManagerScopes, "control-plane.deploy.create")).isFalse();
        }

        @Test
        @DisplayName("deployer with control-plane.deploy.* can deploy only")
        void deployer_canDeployOnly() {
            Set<String> deployerScopes = Set.of("control-plane.deploy.*");

            // Should have all deploy operations
            assertThat(ScopeMatcher.matches(deployerScopes, "control-plane.deploy.create")).isTrue();
            assertThat(ScopeMatcher.matches(deployerScopes, "control-plane.deploy.read")).isTrue();
            assertThat(ScopeMatcher.matches(deployerScopes, "control-plane.deploy.delete")).isTrue();

            // Should NOT have other control-plane operations
            assertThat(ScopeMatcher.matches(deployerScopes, "control-plane.cluster.read")).isFalse();
            assertThat(ScopeMatcher.matches(deployerScopes, "control-plane.module.upload")).isFalse();
            assertThat(ScopeMatcher.matches(deployerScopes, "control-plane.match.create")).isFalse();
        }

        @Test
        @DisplayName("viewer with read-only scopes cannot modify resources")
        void viewerReadOnly_cannotModify() {
            Set<String> viewerScopes = Set.of(
                "control-plane.cluster.read",
                "control-plane.match.read",
                "control-plane.module.read",
                "control-plane.deploy.read",
                "control-plane.autoscaler.read",
                "control-plane.dashboard.read"
            );

            // Should have read access
            assertThat(ScopeMatcher.matches(viewerScopes, "control-plane.cluster.read")).isTrue();
            assertThat(ScopeMatcher.matches(viewerScopes, "control-plane.match.read")).isTrue();
            assertThat(ScopeMatcher.matches(viewerScopes, "control-plane.module.read")).isTrue();
            assertThat(ScopeMatcher.matches(viewerScopes, "control-plane.deploy.read")).isTrue();
            assertThat(ScopeMatcher.matches(viewerScopes, "control-plane.autoscaler.read")).isTrue();
            assertThat(ScopeMatcher.matches(viewerScopes, "control-plane.dashboard.read")).isTrue();

            // Should NOT have write/modify access
            assertThat(ScopeMatcher.matches(viewerScopes, "control-plane.node.register")).isFalse();
            assertThat(ScopeMatcher.matches(viewerScopes, "control-plane.node.manage")).isFalse();
            assertThat(ScopeMatcher.matches(viewerScopes, "control-plane.match.create")).isFalse();
            assertThat(ScopeMatcher.matches(viewerScopes, "control-plane.match.update")).isFalse();
            assertThat(ScopeMatcher.matches(viewerScopes, "control-plane.match.delete")).isFalse();
            assertThat(ScopeMatcher.matches(viewerScopes, "control-plane.module.upload")).isFalse();
            assertThat(ScopeMatcher.matches(viewerScopes, "control-plane.module.delete")).isFalse();
            assertThat(ScopeMatcher.matches(viewerScopes, "control-plane.module.distribute")).isFalse();
            assertThat(ScopeMatcher.matches(viewerScopes, "control-plane.deploy.create")).isFalse();
            assertThat(ScopeMatcher.matches(viewerScopes, "control-plane.deploy.delete")).isFalse();
            assertThat(ScopeMatcher.matches(viewerScopes, "control-plane.autoscaler.manage")).isFalse();
        }

        @Test
        @DisplayName("node agent with node scopes can only register and heartbeat")
        void nodeAgent_canOnlyRegister() {
            Set<String> nodeAgentScopes = Set.of("control-plane.node.register");

            // Should have register/heartbeat access
            assertThat(ScopeMatcher.matches(nodeAgentScopes, "control-plane.node.register")).isTrue();

            // Should NOT have node management
            assertThat(ScopeMatcher.matches(nodeAgentScopes, "control-plane.node.manage")).isFalse();

            // Should NOT have other operations
            assertThat(ScopeMatcher.matches(nodeAgentScopes, "control-plane.cluster.read")).isFalse();
            assertThat(ScopeMatcher.matches(nodeAgentScopes, "control-plane.match.create")).isFalse();
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
