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

package ca.samanthaireland.lightning.auth.quarkus.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ScopeMatcher}.
 */
class ScopeMatcherTest {

    @Nested
    @DisplayName("matches(userScopes, requiredScope)")
    class MatchesTests {

        @Test
        @DisplayName("should return true for exact scope match")
        void exactMatch() {
            Set<String> userScopes = Set.of("engine.container.create");
            assertThat(ScopeMatcher.matches(userScopes, "engine.container.create")).isTrue();
        }

        @Test
        @DisplayName("should return false when scope is not present")
        void noMatch() {
            Set<String> userScopes = Set.of("engine.container.read");
            assertThat(ScopeMatcher.matches(userScopes, "engine.container.create")).isFalse();
        }

        @Test
        @DisplayName("should return true for full wildcard (*)")
        void fullWildcard() {
            Set<String> userScopes = Set.of("*");
            assertThat(ScopeMatcher.matches(userScopes, "engine.container.create")).isTrue();
            assertThat(ScopeMatcher.matches(userScopes, "auth.user.read")).isTrue();
            assertThat(ScopeMatcher.matches(userScopes, "control-plane.module.upload")).isTrue();
            assertThat(ScopeMatcher.matches(userScopes, "anything.at.all")).isTrue();
        }

        @Test
        @DisplayName("should return true for service-level wildcard (service.*)")
        void serviceLevelWildcard() {
            Set<String> userScopes = Set.of("engine.*");
            assertThat(ScopeMatcher.matches(userScopes, "engine.container.create")).isTrue();
            assertThat(ScopeMatcher.matches(userScopes, "engine.match.read")).isTrue();
            assertThat(ScopeMatcher.matches(userScopes, "engine.anything.anything")).isTrue();
        }

        @Test
        @DisplayName("should return false for service-level wildcard when different service")
        void serviceLevelWildcardDifferentService() {
            Set<String> userScopes = Set.of("engine.*");
            assertThat(ScopeMatcher.matches(userScopes, "auth.user.read")).isFalse();
            assertThat(ScopeMatcher.matches(userScopes, "control-plane.module.upload")).isFalse();
        }

        @Test
        @DisplayName("should return true for resource-level wildcard (service.resource.*)")
        void resourceLevelWildcard() {
            Set<String> userScopes = Set.of("engine.container.*");
            assertThat(ScopeMatcher.matches(userScopes, "engine.container.create")).isTrue();
            assertThat(ScopeMatcher.matches(userScopes, "engine.container.read")).isTrue();
            assertThat(ScopeMatcher.matches(userScopes, "engine.container.delete")).isTrue();
        }

        @Test
        @DisplayName("should return false for resource-level wildcard when different resource")
        void resourceLevelWildcardDifferentResource() {
            Set<String> userScopes = Set.of("engine.container.*");
            assertThat(ScopeMatcher.matches(userScopes, "engine.match.create")).isFalse();
            assertThat(ScopeMatcher.matches(userScopes, "engine.session.read")).isFalse();
        }

        @Test
        @DisplayName("should match with multiple user scopes")
        void multipleUserScopes() {
            Set<String> userScopes = Set.of(
                    "engine.container.read",
                    "engine.match.*",
                    "auth.user.read"
            );
            assertThat(ScopeMatcher.matches(userScopes, "engine.container.read")).isTrue();
            assertThat(ScopeMatcher.matches(userScopes, "engine.match.create")).isTrue();
            assertThat(ScopeMatcher.matches(userScopes, "engine.match.delete")).isTrue();
            assertThat(ScopeMatcher.matches(userScopes, "auth.user.read")).isTrue();
            assertThat(ScopeMatcher.matches(userScopes, "engine.container.create")).isFalse();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should return false for null or empty user scopes")
        void nullOrEmptyUserScopes(Set<String> userScopes) {
            assertThat(ScopeMatcher.matches(userScopes, "engine.container.create")).isFalse();
        }

        @ParameterizedTest
        @CsvSource({
                ", false",
                "'', false",
                "'  ', false"
        })
        @DisplayName("should return false for null, empty, or blank required scope")
        void nullOrEmptyRequiredScope(String requiredScope, boolean expected) {
            Set<String> userScopes = Set.of("*");
            assertThat(ScopeMatcher.matches(userScopes, requiredScope)).isEqualTo(expected);
        }

        @Test
        @DisplayName("should handle scopes with hyphens")
        void scopesWithHyphens() {
            Set<String> userScopes = Set.of("control-plane.module.*");
            assertThat(ScopeMatcher.matches(userScopes, "control-plane.module.upload")).isTrue();
            assertThat(ScopeMatcher.matches(userScopes, "control-plane.module.delete")).isTrue();
        }

        @Test
        @DisplayName("should not match partial scope segments")
        void noPartialSegmentMatching() {
            Set<String> userScopes = Set.of("engine.cont");
            assertThat(ScopeMatcher.matches(userScopes, "engine.container.create")).isFalse();
        }

        @Test
        @DisplayName("should not match when user scope is more specific than required")
        void userScopeMoreSpecific() {
            Set<String> userScopes = Set.of("engine.container.create.special");
            assertThat(ScopeMatcher.matches(userScopes, "engine.container.create")).isFalse();
        }

        @Test
        @DisplayName("wildcard should match all deeper levels")
        void wildcardMatchesDeeperLevels() {
            Set<String> userScopes = Set.of("engine.*");
            assertThat(ScopeMatcher.matches(userScopes, "engine.a.b.c.d")).isTrue();
        }
    }

    @Nested
    @DisplayName("matchesAny(userScopes, requiredScopes)")
    class MatchesAnyTests {

        @Test
        @DisplayName("should return true when any required scope matches")
        void anyMatch() {
            Set<String> userScopes = Set.of("engine.container.read");
            Set<String> requiredScopes = Set.of("engine.container.read", "engine.container.create");
            assertThat(ScopeMatcher.matchesAny(userScopes, requiredScopes)).isTrue();
        }

        @Test
        @DisplayName("should return true when wildcard matches any required scope")
        void wildcardMatchesAny() {
            Set<String> userScopes = Set.of("engine.*");
            Set<String> requiredScopes = Set.of("auth.user.read", "engine.container.create");
            assertThat(ScopeMatcher.matchesAny(userScopes, requiredScopes)).isTrue();
        }

        @Test
        @DisplayName("should return false when no required scopes match")
        void noMatch() {
            Set<String> userScopes = Set.of("auth.user.read");
            Set<String> requiredScopes = Set.of("engine.container.read", "engine.container.create");
            assertThat(ScopeMatcher.matchesAny(userScopes, requiredScopes)).isFalse();
        }

        @Test
        @DisplayName("should return true for null required scopes")
        void nullRequiredScopes() {
            Set<String> userScopes = Set.of("engine.container.read");
            assertThat(ScopeMatcher.matchesAny(userScopes, null)).isTrue();
        }

        @Test
        @DisplayName("should return true for empty required scopes")
        void emptyRequiredScopes() {
            Set<String> userScopes = Set.of("engine.container.read");
            assertThat(ScopeMatcher.matchesAny(userScopes, Set.of())).isTrue();
        }
    }

    @Nested
    @DisplayName("matchesAll(userScopes, requiredScopes)")
    class MatchesAllTests {

        @Test
        @DisplayName("should return true when all required scopes match exactly")
        void allExactMatch() {
            Set<String> userScopes = Set.of("engine.container.read", "engine.container.create");
            Set<String> requiredScopes = Set.of("engine.container.read", "engine.container.create");
            assertThat(ScopeMatcher.matchesAll(userScopes, requiredScopes)).isTrue();
        }

        @Test
        @DisplayName("should return true when wildcard covers all required scopes")
        void wildcardCoversAll() {
            Set<String> userScopes = Set.of("*");
            Set<String> requiredScopes = Set.of("engine.container.read", "auth.user.create");
            assertThat(ScopeMatcher.matchesAll(userScopes, requiredScopes)).isTrue();
        }

        @Test
        @DisplayName("should return true when service wildcard covers all required scopes for that service")
        void serviceWildcardCoversAll() {
            Set<String> userScopes = Set.of("engine.*");
            Set<String> requiredScopes = Set.of("engine.container.read", "engine.match.create");
            assertThat(ScopeMatcher.matchesAll(userScopes, requiredScopes)).isTrue();
        }

        @Test
        @DisplayName("should return false when not all required scopes are covered")
        void notAllCovered() {
            Set<String> userScopes = Set.of("engine.*");
            Set<String> requiredScopes = Set.of("engine.container.read", "auth.user.create");
            assertThat(ScopeMatcher.matchesAll(userScopes, requiredScopes)).isFalse();
        }

        @Test
        @DisplayName("should return false when one required scope is missing")
        void oneMissing() {
            Set<String> userScopes = Set.of("engine.container.read");
            Set<String> requiredScopes = Set.of("engine.container.read", "engine.container.create");
            assertThat(ScopeMatcher.matchesAll(userScopes, requiredScopes)).isFalse();
        }

        @Test
        @DisplayName("should return true for null required scopes")
        void nullRequiredScopes() {
            Set<String> userScopes = Set.of("engine.container.read");
            assertThat(ScopeMatcher.matchesAll(userScopes, null)).isTrue();
        }

        @Test
        @DisplayName("should return true for empty required scopes")
        void emptyRequiredScopes() {
            Set<String> userScopes = Set.of("engine.container.read");
            assertThat(ScopeMatcher.matchesAll(userScopes, Set.of())).isTrue();
        }

        @Test
        @DisplayName("should handle combination of exact and wildcard scopes")
        void combinationOfScopes() {
            Set<String> userScopes = Set.of("engine.container.*", "auth.user.read");
            Set<String> requiredScopes = Set.of("engine.container.create", "auth.user.read");
            assertThat(ScopeMatcher.matchesAll(userScopes, requiredScopes)).isTrue();
        }
    }

    @Nested
    @DisplayName("Real-world scope patterns")
    class RealWorldPatterns {

        @Test
        @DisplayName("admin with * scope should have access to everything")
        void adminFullAccess() {
            Set<String> adminScopes = Set.of("*");

            // Engine scopes
            assertThat(ScopeMatcher.matches(adminScopes, "engine.container.create")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "engine.container.read")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "engine.container.delete")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "engine.match.create")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "engine.command.submit")).isTrue();

            // Auth scopes
            assertThat(ScopeMatcher.matches(adminScopes, "auth.user.create")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "auth.user.read")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "auth.role.delete")).isTrue();

            // Control-plane scopes
            assertThat(ScopeMatcher.matches(adminScopes, "control-plane.module.upload")).isTrue();
            assertThat(ScopeMatcher.matches(adminScopes, "control-plane.cluster.read")).isTrue();
        }

        @Test
        @DisplayName("engine operator with engine.* should only access engine")
        void engineOperator() {
            Set<String> operatorScopes = Set.of("engine.*");

            // Engine - should have access
            assertThat(ScopeMatcher.matches(operatorScopes, "engine.container.create")).isTrue();
            assertThat(ScopeMatcher.matches(operatorScopes, "engine.match.delete")).isTrue();

            // Auth - should NOT have access
            assertThat(ScopeMatcher.matches(operatorScopes, "auth.user.read")).isFalse();

            // Control-plane - should NOT have access
            assertThat(ScopeMatcher.matches(operatorScopes, "control-plane.module.upload")).isFalse();
        }

        @Test
        @DisplayName("viewer with *.*.read pattern should only have read access")
        void viewerReadOnly() {
            Set<String> viewerScopes = Set.of(
                    "engine.container.read",
                    "engine.match.read",
                    "engine.snapshot.read",
                    "auth.user.read",
                    "control-plane.cluster.read"
            );

            // Read operations - should have access
            assertThat(ScopeMatcher.matches(viewerScopes, "engine.container.read")).isTrue();
            assertThat(ScopeMatcher.matches(viewerScopes, "auth.user.read")).isTrue();

            // Write operations - should NOT have access
            assertThat(ScopeMatcher.matches(viewerScopes, "engine.container.create")).isFalse();
            assertThat(ScopeMatcher.matches(viewerScopes, "auth.user.create")).isFalse();
        }

        @Test
        @DisplayName("module manager with specific module scopes")
        void moduleManager() {
            Set<String> moduleScopes = Set.of("control-plane.module.*");

            // Module operations - should have access
            assertThat(ScopeMatcher.matches(moduleScopes, "control-plane.module.upload")).isTrue();
            assertThat(ScopeMatcher.matches(moduleScopes, "control-plane.module.read")).isTrue();
            assertThat(ScopeMatcher.matches(moduleScopes, "control-plane.module.delete")).isTrue();
            assertThat(ScopeMatcher.matches(moduleScopes, "control-plane.module.distribute")).isTrue();

            // Other control-plane operations - should NOT have access
            assertThat(ScopeMatcher.matches(moduleScopes, "control-plane.cluster.read")).isFalse();
            assertThat(ScopeMatcher.matches(moduleScopes, "control-plane.deploy.create")).isFalse();
        }
    }
}
