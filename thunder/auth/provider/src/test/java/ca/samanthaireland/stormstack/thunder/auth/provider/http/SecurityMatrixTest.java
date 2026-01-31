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

package ca.samanthaireland.stormstack.thunder.auth.provider.http;

import ca.samanthaireland.stormstack.thunder.auth.quarkus.annotation.Scopes;
import ca.samanthaireland.stormstack.thunder.auth.quarkus.security.ScopeMatcher;
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
 * Security matrix tests for the Lightning Auth REST API.
 *
 * <p>This test verifies that all REST endpoints have appropriate scope annotations
 * and that the scope matching logic works correctly with wildcards.
 *
 * <h2>Auth Service Scope Matrix</h2>
 * <table>
 *   <tr><th>Scope</th><th>Description</th></tr>
 *   <tr><td>auth.user.create</td><td>Create users</td></tr>
 *   <tr><td>auth.user.read</td><td>View users</td></tr>
 *   <tr><td>auth.user.update</td><td>Update users</td></tr>
 *   <tr><td>auth.user.delete</td><td>Delete users</td></tr>
 *   <tr><td>auth.role.create</td><td>Create roles</td></tr>
 *   <tr><td>auth.role.read</td><td>View roles</td></tr>
 *   <tr><td>auth.role.update</td><td>Update roles</td></tr>
 *   <tr><td>auth.role.delete</td><td>Delete roles</td></tr>
 *   <tr><td>auth.token.create</td><td>Create API tokens</td></tr>
 *   <tr><td>auth.token.read</td><td>View API tokens</td></tr>
 *   <tr><td>auth.token.revoke</td><td>Revoke API tokens</td></tr>
 *   <tr><td>auth.match-token.issue</td><td>Issue match tokens</td></tr>
 *   <tr><td>auth.match-token.read</td><td>View match tokens</td></tr>
 *   <tr><td>auth.match-token.revoke</td><td>Revoke match tokens</td></tr>
 * </table>
 *
 * <h2>Public Endpoints (No Auth)</h2>
 * <ul>
 *   <li>POST /oauth2/token - OAuth2 token endpoint (all grant types)</li>
 *   <li>POST /api/validate - Service-to-service JWT validation</li>
 *   <li>POST /api/match-tokens/validate - Match token validation</li>
 *   <li>GET /.well-known/openid-configuration - OIDC discovery</li>
 *   <li>GET /.well-known/jwks.json - JWKS endpoint</li>
 * </ul>
 */
@DisplayName("Security Matrix - Auth Service")
class SecurityMatrixTest {

    @Nested
    @DisplayName("UserResource")
    class UserResourceTests {

        @Test
        @DisplayName("GET /api/users requires auth.user.read")
        void listUsers_requiresReadScope() {
            assertMethodHasScope(UserResource.class, "listUsers", "auth.user.read");
        }

        @Test
        @DisplayName("POST /api/users requires auth.user.create")
        void createUser_requiresCreateScope() {
            assertMethodHasScope(UserResource.class, "createUser", "auth.user.create");
        }

        @Test
        @DisplayName("GET /api/users/{id} requires auth.user.read")
        void getUser_requiresReadScope() {
            assertMethodHasScope(UserResource.class, "getUser", "auth.user.read");
        }

        @Test
        @DisplayName("PUT /api/users/{id} requires auth.user.update")
        void updateUser_requiresUpdateScope() {
            assertMethodHasScope(UserResource.class, "updateUser", "auth.user.update");
        }

        @Test
        @DisplayName("DELETE /api/users/{id} requires auth.user.delete")
        void deleteUser_requiresDeleteScope() {
            assertMethodHasScope(UserResource.class, "deleteUser", "auth.user.delete");
        }
    }

    @Nested
    @DisplayName("RoleResource")
    class RoleResourceTests {

        @Test
        @DisplayName("GET /api/roles requires auth.role.read")
        void listRoles_requiresReadScope() {
            assertMethodHasScope(RoleResource.class, "listRoles", "auth.role.read");
        }

        @Test
        @DisplayName("POST /api/roles requires auth.role.create")
        void createRole_requiresCreateScope() {
            assertMethodHasScope(RoleResource.class, "createRole", "auth.role.create");
        }

        @Test
        @DisplayName("GET /api/roles/{id} requires auth.role.read")
        void getRole_requiresReadScope() {
            assertMethodHasScope(RoleResource.class, "getRole", "auth.role.read");
        }

        @Test
        @DisplayName("PUT /api/roles/{id} requires auth.role.update")
        void updateRole_requiresUpdateScope() {
            assertMethodHasScope(RoleResource.class, "updateRole", "auth.role.update");
        }

        @Test
        @DisplayName("DELETE /api/roles/{id} requires auth.role.delete")
        void deleteRole_requiresDeleteScope() {
            assertMethodHasScope(RoleResource.class, "deleteRole", "auth.role.delete");
        }
    }

    @Nested
    @DisplayName("ApiTokenResource")
    class ApiTokenResourceTests {

        @Test
        @DisplayName("GET /api/tokens requires auth.token.read")
        void listTokens_requiresReadScope() {
            assertMethodHasScope(ApiTokenResource.class, "listTokens", "auth.token.read");
        }

        @Test
        @DisplayName("POST /api/tokens requires auth.token.create")
        void createToken_requiresCreateScope() {
            assertMethodHasScope(ApiTokenResource.class, "createToken", "auth.token.create");
        }

        @Test
        @DisplayName("GET /api/tokens/{id} requires auth.token.read")
        void getToken_requiresReadScope() {
            assertMethodHasScope(ApiTokenResource.class, "getToken", "auth.token.read");
        }

        @Test
        @DisplayName("DELETE /api/tokens/{id} requires auth.token.revoke")
        void revokeToken_requiresRevokeScope() {
            assertMethodHasScope(ApiTokenResource.class, "revokeToken", "auth.token.revoke");
        }
    }

    @Nested
    @DisplayName("MatchTokenResource")
    class MatchTokenResourceTests {

        @Test
        @DisplayName("POST /api/match-tokens requires auth.match-token.issue")
        void issueToken_requiresIssueScope() {
            assertMethodHasScope(MatchTokenResource.class, "issueToken", "auth.match-token.issue");
        }

        @Test
        @DisplayName("GET /api/match-tokens/{id} requires auth.match-token.read")
        void getToken_requiresReadScope() {
            assertMethodHasScope(MatchTokenResource.class, "getToken", "auth.match-token.read");
        }

        @Test
        @DisplayName("GET /api/match-tokens/match/{matchId} requires auth.match-token.read")
        void listTokensForMatch_requiresReadScope() {
            assertMethodHasScope(MatchTokenResource.class, "listTokensForMatch", "auth.match-token.read");
        }

        @Test
        @DisplayName("GET .../active requires auth.match-token.read")
        void listActiveTokensForMatch_requiresReadScope() {
            assertMethodHasScope(MatchTokenResource.class, "listActiveTokensForMatch", "auth.match-token.read");
        }

        @Test
        @DisplayName("GET .../player/{playerId} requires auth.match-token.read")
        void getActiveTokenForPlayer_requiresReadScope() {
            assertMethodHasScope(MatchTokenResource.class, "getActiveTokenForPlayer", "auth.match-token.read");
        }

        @Test
        @DisplayName("POST /api/match-tokens/{id}/revoke requires auth.match-token.revoke")
        void revokeToken_requiresRevokeScope() {
            assertMethodHasScope(MatchTokenResource.class, "revokeToken", "auth.match-token.revoke");
        }

        @Test
        @DisplayName("POST .../player/{playerId}/revoke requires auth.match-token.revoke")
        void revokeTokensForPlayer_requiresRevokeScope() {
            assertMethodHasScope(MatchTokenResource.class, "revokeTokensForPlayer", "auth.match-token.revoke");
        }

        @Test
        @DisplayName("POST .../match/{matchId}/revoke requires auth.match-token.revoke")
        void revokeTokensForMatch_requiresRevokeScope() {
            assertMethodHasScope(MatchTokenResource.class, "revokeTokensForMatch", "auth.match-token.revoke");
        }

        @Test
        @DisplayName("GET .../count requires auth.match-token.read")
        void countActiveTokens_requiresReadScope() {
            assertMethodHasScope(MatchTokenResource.class, "countActiveTokens", "auth.match-token.read");
        }
    }

    @Nested
    @DisplayName("Public Endpoints (No Auth)")
    class PublicEndpointsTests {

        @Test
        @DisplayName("POST /oauth2/token has no scope requirement")
        void tokenEndpoint_isPublic() {
            // The token endpoint should not require auth (it IS the auth mechanism)
            Method method = findMethod(TokenEndpoint.class, "token");
            assertThat(method).isNotNull();
            Scopes scopes = method.getAnnotation(Scopes.class);
            assertThat(scopes).isNull();
        }

        @Test
        @DisplayName("POST /api/validate has no scope requirement")
        void validate_isPublic() {
            Method method = findMethod(ValidationResource.class, "validateToken");
            assertThat(method).isNotNull();
            Scopes scopes = method.getAnnotation(Scopes.class);
            assertThat(scopes).isNull();
        }

        @Test
        @DisplayName("GET /.well-known/openid-configuration has no scope requirement")
        void discovery_isPublic() {
            Method method = findMethod(DiscoveryEndpoint.class, "getOpenIdConfiguration");
            assertThat(method).isNotNull();
            Scopes scopes = method.getAnnotation(Scopes.class);
            assertThat(scopes).isNull();
        }

        @Test
        @DisplayName("GET /.well-known/jwks.json has no scope requirement")
        void jwks_isPublic() {
            Method method = findMethod(JwksEndpoint.class, "getJwks");
            assertThat(method).isNotNull();
            Scopes scopes = method.getAnnotation(Scopes.class);
            assertThat(scopes).isNull();
        }
    }

    @Nested
    @DisplayName("Wildcard Scope Matching")
    class WildcardScopeMatchingTests {

        @ParameterizedTest
        @CsvSource({
            "auth.user.create, auth.*, true",
            "auth.user.create, auth.user.*, true",
            "auth.user.create, *, true",
            "auth.user.create, engine.*, false",
            "auth.user.create, auth.role.*, false",
            "auth.role.read, auth.*, true",
            "auth.token.create, auth.token.*, true",
            "auth.match-token.issue, auth.match-token.*, true",
            "auth.match-token.revoke, auth.*, true"
        })
        @DisplayName("wildcard scope matching")
        void wildcardMatching(String requiredScope, String userScope, boolean shouldMatch) {
            Set<String> userScopes = Set.of(userScope);
            assertThat(ScopeMatcher.matches(userScopes, requiredScope))
                .as("User with '%s' accessing '%s'", userScope, requiredScope)
                .isEqualTo(shouldMatch);
        }

        @Test
        @DisplayName("admin with * scope has access to all auth endpoints")
        void adminWithFullWildcard_hasAccessToAll() {
            Set<String> adminScopes = Set.of("*");

            // User scopes
            assertThat(ScopeMatcher.matches(adminScopes, "auth.user.create")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "auth.user.read")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "auth.user.update")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "auth.user.delete")).isTrue();

            // Role scopes
            assertThat(ScopeMatcher.matches(adminScopes, "auth.role.create")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "auth.role.read")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "auth.role.update")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "auth.role.delete")).isTrue();

            // Token scopes
            assertThat(ScopeMatcher.matches(adminScopes, "auth.token.create")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "auth.token.read")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "auth.token.revoke")).isTrue();

            // Match token scopes
            assertThat(ScopeMatcher.matches(adminScopes, "auth.match-token.issue")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "auth.match-token.read")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "auth.match-token.revoke")).isTrue();
        }

        @Test
        @DisplayName("auth operator with auth.* has access to all auth endpoints")
        void authOperator_hasAccessToAllAuth() {
            Set<String> operatorScopes = Set.of("auth.*");

            // Should have access to all auth operations
            assertThat(ScopeMatcher.matches(operatorScopes, "auth.user.create")).isTrue();
            assertThat(ScopeMatcher.matches(operatorScopes, "auth.role.read")).isTrue();
            assertThat(ScopeMatcher.matches(operatorScopes, "auth.token.revoke")).isTrue();
            assertThat(ScopeMatcher.matches(operatorScopes, "auth.match-token.issue")).isTrue();

            // Should NOT have access to other services
            assertThat(ScopeMatcher.matches(operatorScopes, "engine.container.create")).isFalse();
            assertThat(ScopeMatcher.matches(operatorScopes, "control-plane.module.upload")).isFalse();
        }

        @Test
        @DisplayName("user manager with auth.user.* can manage users only")
        void userManager_canManageUsersOnly() {
            Set<String> userManagerScopes = Set.of("auth.user.*");

            // Should have all user operations
            assertThat(ScopeMatcher.matches(userManagerScopes, "auth.user.create")).isTrue();
            assertThat(ScopeMatcher.matches(userManagerScopes, "auth.user.read")).isTrue();
            assertThat(ScopeMatcher.matches(userManagerScopes, "auth.user.update")).isTrue();
            assertThat(ScopeMatcher.matches(userManagerScopes, "auth.user.delete")).isTrue();

            // Should NOT have role or token operations
            assertThat(ScopeMatcher.matches(userManagerScopes, "auth.role.create")).isFalse();
            assertThat(ScopeMatcher.matches(userManagerScopes, "auth.token.create")).isFalse();
            assertThat(ScopeMatcher.matches(userManagerScopes, "auth.match-token.issue")).isFalse();
        }

        @Test
        @DisplayName("viewer with read-only scopes cannot modify resources")
        void viewerReadOnly_cannotModify() {
            Set<String> viewerScopes = Set.of(
                "auth.user.read",
                "auth.role.read",
                "auth.token.read",
                "auth.match-token.read"
            );

            // Should have read access
            assertThat(ScopeMatcher.matches(viewerScopes, "auth.user.read")).isTrue();
            assertThat(ScopeMatcher.matches(viewerScopes, "auth.role.read")).isTrue();
            assertThat(ScopeMatcher.matches(viewerScopes, "auth.token.read")).isTrue();

            // Should NOT have write/modify access
            assertThat(ScopeMatcher.matches(viewerScopes, "auth.user.create")).isFalse();
            assertThat(ScopeMatcher.matches(viewerScopes, "auth.user.delete")).isFalse();
            assertThat(ScopeMatcher.matches(viewerScopes, "auth.role.create")).isFalse();
            assertThat(ScopeMatcher.matches(viewerScopes, "auth.token.create")).isFalse();
            assertThat(ScopeMatcher.matches(viewerScopes, "auth.match-token.issue")).isFalse();
            assertThat(ScopeMatcher.matches(viewerScopes, "auth.match-token.revoke")).isFalse();
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
