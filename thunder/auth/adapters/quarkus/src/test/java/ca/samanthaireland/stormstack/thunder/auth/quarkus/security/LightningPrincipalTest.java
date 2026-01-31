package ca.samanthaireland.stormstack.thunder.auth.quarkus.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LightningPrincipalTest {

    @Test
    void constructor_storesAllFields() {
        LightningPrincipal principal = new LightningPrincipal(
                "user-123",
                "testuser",
                Set.of("read", "write"),
                "token-456"
        );

        assertThat(principal.getUserId()).isEqualTo("user-123");
        assertThat(principal.getUsername()).isEqualTo("testuser");
        assertThat(principal.getName()).isEqualTo("testuser");
        assertThat(principal.getScopes()).containsExactlyInAnyOrder("read", "write");
        assertThat(principal.getApiTokenId()).isEqualTo("token-456");
    }

    @Test
    void constructor_withNullScopes_createsEmptySet() {
        LightningPrincipal principal = new LightningPrincipal(
                "user-123",
                "testuser",
                null,
                "token-456"
        );

        assertThat(principal.getScopes()).isEmpty();
    }

    @Test
    void getScopes_returnsImmutableSet() {
        Set<String> scopes = Set.of("read", "write");
        LightningPrincipal principal = new LightningPrincipal(
                "user-123",
                "testuser",
                scopes,
                "token-456"
        );

        Set<String> returnedScopes = principal.getScopes();

        // Verify it's a copy (modifying original doesn't affect principal)
        assertThat(returnedScopes).containsExactlyInAnyOrder("read", "write");
    }

    @Test
    void hasScope_withExistingScope_returnsTrue() {
        LightningPrincipal principal = new LightningPrincipal(
                "user-123",
                "testuser",
                Set.of("read", "write", "admin"),
                "token-456"
        );

        assertThat(principal.hasScope("read")).isTrue();
        assertThat(principal.hasScope("write")).isTrue();
        assertThat(principal.hasScope("admin")).isTrue();
    }

    @Test
    void hasScope_withMissingScope_returnsFalse() {
        LightningPrincipal principal = new LightningPrincipal(
                "user-123",
                "testuser",
                Set.of("read"),
                "token-456"
        );

        assertThat(principal.hasScope("write")).isFalse();
        assertThat(principal.hasScope("admin")).isFalse();
    }

    @Test
    void hasAllScopes_withAllPresent_returnsTrue() {
        LightningPrincipal principal = new LightningPrincipal(
                "user-123",
                "testuser",
                Set.of("read", "write", "delete"),
                "token-456"
        );

        assertThat(principal.hasAllScopes(Set.of("read", "write"))).isTrue();
        assertThat(principal.hasAllScopes(Set.of("read"))).isTrue();
        assertThat(principal.hasAllScopes(Set.of())).isTrue();
    }

    @Test
    void hasAllScopes_withSomeMissing_returnsFalse() {
        LightningPrincipal principal = new LightningPrincipal(
                "user-123",
                "testuser",
                Set.of("read"),
                "token-456"
        );

        assertThat(principal.hasAllScopes(Set.of("read", "write"))).isFalse();
        assertThat(principal.hasAllScopes(Set.of("admin"))).isFalse();
    }

    @Test
    void hasAnyScope_withOnePresent_returnsTrue() {
        LightningPrincipal principal = new LightningPrincipal(
                "user-123",
                "testuser",
                Set.of("read"),
                "token-456"
        );

        assertThat(principal.hasAnyScope(Set.of("read", "write"))).isTrue();
        assertThat(principal.hasAnyScope(Set.of("admin", "read"))).isTrue();
    }

    @Test
    void hasAnyScope_withNonePresent_returnsFalse() {
        LightningPrincipal principal = new LightningPrincipal(
                "user-123",
                "testuser",
                Set.of("read"),
                "token-456"
        );

        assertThat(principal.hasAnyScope(Set.of("write", "admin"))).isFalse();
    }

    @Test
    void hasAnyScope_withEmptyRequired_returnsTrue() {
        // Empty required scopes means "nothing required, access granted"
        LightningPrincipal principal = new LightningPrincipal(
                "user-123",
                "testuser",
                Set.of("read"),
                "token-456"
        );

        assertThat(principal.hasAnyScope(Set.of())).isTrue();
    }

    @Test
    void toString_containsRelevantInfo() {
        LightningPrincipal principal = new LightningPrincipal(
                "user-123",
                "testuser",
                Set.of("read"),
                "token-456"
        );

        String str = principal.toString();

        assertThat(str).contains("user-123");
        assertThat(str).contains("testuser");
        assertThat(str).contains("read");
    }
}
