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

package ca.samanthaireland.lightning.auth.spring.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LightningAuthenticationTest {

    @Test
    void constructor_createsAuthenticationWithAllFields() {
        LightningAuthentication auth = new LightningAuthentication(
                "user-123",
                "testuser",
                Set.of("view_snapshots", "submit_commands"),
                "jwt.token.here",
                "api-token-456",
                Instant.now().plusSeconds(3600)
        );

        assertThat(auth.getUserId()).isEqualTo("user-123");
        assertThat(auth.getUsername()).isEqualTo("testuser");
        assertThat(auth.getScopes()).containsExactlyInAnyOrder("view_snapshots", "submit_commands");
        assertThat(auth.getJwtToken()).isEqualTo("jwt.token.here");
        assertThat(auth.getApiTokenId()).isEqualTo("api-token-456");
        assertThat(auth.isAuthenticated()).isTrue();
    }

    @Test
    void constructor_throwsOnNullUserId() {
        assertThatThrownBy(() -> new LightningAuthentication(
                null,
                "testuser",
                Set.of(),
                "jwt",
                null,
                Instant.now().plusSeconds(3600)
        )).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("User ID");
    }

    @Test
    void constructor_throwsOnNullUsername() {
        assertThatThrownBy(() -> new LightningAuthentication(
                "user-123",
                null,
                Set.of(),
                "jwt",
                null,
                Instant.now().plusSeconds(3600)
        )).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Username");
    }

    @Test
    void constructor_handlesNullScopes() {
        LightningAuthentication auth = new LightningAuthentication(
                "user-123",
                "testuser",
                null,
                "jwt",
                null,
                Instant.now().plusSeconds(3600)
        );

        assertThat(auth.getScopes()).isEmpty();
    }

    @Test
    void getName_returnsUsername() {
        LightningAuthentication auth = createAuth(Set.of());

        assertThat(auth.getName()).isEqualTo("testuser");
    }

    @Test
    void getPrincipal_returnsUserId() {
        LightningAuthentication auth = createAuth(Set.of());

        assertThat(auth.getPrincipal()).isEqualTo("user-123");
    }

    @Test
    void getCredentials_returnsJwtToken() {
        LightningAuthentication auth = new LightningAuthentication(
                "user-123",
                "testuser",
                Set.of(),
                "my.jwt.token",
                null,
                Instant.now().plusSeconds(3600)
        );

        assertThat(auth.getCredentials()).isEqualTo("my.jwt.token");
    }

    @Test
    void getAuthorities_containsScopeAuthorities() {
        LightningAuthentication auth = createAuth(Set.of("view_snapshots", "admin"));

        assertThat(auth.getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactlyInAnyOrder("SCOPE_view_snapshots", "SCOPE_admin");
    }

    @Test
    void hasScope_returnsTrue_whenScopePresent() {
        LightningAuthentication auth = createAuth(Set.of("view_snapshots", "submit_commands"));

        assertThat(auth.hasScope("view_snapshots")).isTrue();
        assertThat(auth.hasScope("submit_commands")).isTrue();
    }

    @Test
    void hasScope_returnsFalse_whenScopeMissing() {
        LightningAuthentication auth = createAuth(Set.of("view_snapshots"));

        assertThat(auth.hasScope("admin")).isFalse();
    }

    @Test
    void hasAnyScope_returnsTrue_whenAtLeastOneScopePresent() {
        LightningAuthentication auth = createAuth(Set.of("view_snapshots"));

        assertThat(auth.hasAnyScope("admin", "view_snapshots")).isTrue();
    }

    @Test
    void hasAnyScope_returnsFalse_whenNoScopesPresent() {
        LightningAuthentication auth = createAuth(Set.of("view_snapshots"));

        assertThat(auth.hasAnyScope("admin", "super_admin")).isFalse();
    }

    @Test
    void hasAllScopes_returnsTrue_whenAllScopesPresent() {
        LightningAuthentication auth = createAuth(Set.of("view_snapshots", "submit_commands", "admin"));

        assertThat(auth.hasAllScopes("view_snapshots", "submit_commands")).isTrue();
    }

    @Test
    void hasAllScopes_returnsFalse_whenSomeScopesMissing() {
        LightningAuthentication auth = createAuth(Set.of("view_snapshots"));

        assertThat(auth.hasAllScopes("view_snapshots", "admin")).isFalse();
    }

    @Test
    void isExpired_returnsTrue_afterExpiryTime() {
        LightningAuthentication auth = new LightningAuthentication(
                "user-123",
                "testuser",
                Set.of(),
                "jwt",
                null,
                Instant.now().minusSeconds(10)
        );

        assertThat(auth.isExpired()).isTrue();
        assertThat(auth.isAuthenticated()).isFalse();
    }

    @Test
    void isExpired_returnsFalse_beforeExpiryTime() {
        LightningAuthentication auth = createAuth(Set.of());

        assertThat(auth.isExpired()).isFalse();
    }

    @Test
    void setAuthenticated_updatesStatus() {
        LightningAuthentication auth = createAuth(Set.of());

        auth.setAuthenticated(false);

        assertThat(auth.isAuthenticated()).isFalse();
    }

    @Test
    void scopesAreImmutable() {
        Set<String> originalScopes = Set.of("view_snapshots");
        LightningAuthentication auth = createAuth(originalScopes);

        Set<String> returnedScopes = auth.getScopes();

        assertThatThrownBy(() -> returnedScopes.add("admin"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void toString_containsRelevantInfo() {
        LightningAuthentication auth = new LightningAuthentication(
                "user-123",
                "testuser",
                Set.of("admin"),
                "jwt",
                "api-token-456",
                Instant.now().plusSeconds(3600)
        );

        String str = auth.toString();

        assertThat(str).contains("user-123");
        assertThat(str).contains("testuser");
        assertThat(str).contains("admin");
        assertThat(str).contains("api-token-456");
    }

    private LightningAuthentication createAuth(Set<String> scopes) {
        return new LightningAuthentication(
                "user-123",
                "testuser",
                scopes,
                "jwt.token",
                null,
                Instant.now().plusSeconds(3600)
        );
    }
}
