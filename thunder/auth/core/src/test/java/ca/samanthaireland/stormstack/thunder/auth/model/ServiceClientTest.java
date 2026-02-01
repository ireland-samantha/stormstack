/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.stormstack.thunder.auth.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class ServiceClientTest {

    // ============= Constructor Validation Tests =============

    @Test
    void constructor_withValidConfidentialClient_createsClient() {
        ServiceClient client = new ServiceClient(
                ServiceClientId.of("test-client"),
                "secret-hash",
                ClientType.CONFIDENTIAL,
                "Test Client",
                Set.of("scope1", "scope2"),
                Set.of(GrantType.CLIENT_CREDENTIALS),
                Instant.now(),
                true
        );

        assertThat(client.clientId().value()).isEqualTo("test-client");
        assertThat(client.clientType()).isEqualTo(ClientType.CONFIDENTIAL);
        assertThat(client.displayName()).isEqualTo("Test Client");
        assertThat(client.enabled()).isTrue();
    }

    @Test
    void constructor_withNullClientId_throwsNullPointer() {
        assertThatThrownBy(() -> new ServiceClient(
                null,
                "secret-hash",
                ClientType.CONFIDENTIAL,
                "Test Client",
                Set.of("scope1"),
                Set.of(GrantType.CLIENT_CREDENTIALS),
                Instant.now(),
                true
        )).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Client ID");
    }

    @Test
    void constructor_withConfidentialClientWithoutSecret_throwsIllegalArgument() {
        assertThatThrownBy(() -> new ServiceClient(
                ServiceClientId.of("test-client"),
                null,
                ClientType.CONFIDENTIAL,
                "Test Client",
                Set.of("scope1"),
                Set.of(GrantType.CLIENT_CREDENTIALS),
                Instant.now(),
                true
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Confidential clients must have a client secret");
    }

    @Test
    void constructor_withBlankDisplayName_throwsIllegalArgument() {
        assertThatThrownBy(() -> new ServiceClient(
                ServiceClientId.of("test-client"),
                "secret-hash",
                ClientType.CONFIDENTIAL,
                "   ",
                Set.of("scope1"),
                Set.of(GrantType.CLIENT_CREDENTIALS),
                Instant.now(),
                true
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Display name cannot be blank");
    }

    @Test
    void constructor_withEmptyGrantTypes_throwsIllegalArgument() {
        assertThatThrownBy(() -> new ServiceClient(
                ServiceClientId.of("test-client"),
                "secret-hash",
                ClientType.CONFIDENTIAL,
                "Test Client",
                Set.of("scope1"),
                Set.of(),
                Instant.now(),
                true
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one allowed grant type");
    }

    @Test
    void constructor_withPublicClientUsingClientCredentials_throwsIllegalArgument() {
        assertThatThrownBy(() -> new ServiceClient(
                ServiceClientId.of("test-client"),
                null,
                ClientType.PUBLIC,
                "Test Client",
                Set.of("scope1"),
                Set.of(GrantType.CLIENT_CREDENTIALS),
                Instant.now(),
                true
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Public clients cannot use client_credentials grant");
    }

    // ============= Factory Method Tests =============

    @Test
    void createConfidential_createsValidConfidentialClient() {
        ServiceClient client = ServiceClient.createConfidential(
                "my-service",
                "hash123",
                "My Service",
                Set.of("scope1"),
                Set.of(GrantType.CLIENT_CREDENTIALS)
        );

        assertThat(client.clientType()).isEqualTo(ClientType.CONFIDENTIAL);
        assertThat(client.isConfidential()).isTrue();
        assertThat(client.enabled()).isTrue();
        assertThat(client.createdAt()).isNotNull();
    }

    @Test
    void createPublic_createsValidPublicClient() {
        ServiceClient client = ServiceClient.createPublic(
                "mobile-app",
                "Mobile App",
                Set.of("*"),
                Set.of(GrantType.PASSWORD, GrantType.REFRESH_TOKEN)
        );

        assertThat(client.clientType()).isEqualTo(ClientType.PUBLIC);
        assertThat(client.isConfidential()).isFalse();
        assertThat(client.clientSecretHash()).isNull();
    }

    // ============= Scope Matching Tests =============

    @Test
    void isScopeAllowed_withExactMatch_returnsTrue() {
        ServiceClient client = ServiceClient.createPublic(
                "test",
                "Test",
                Set.of("user.read", "user.write"),
                Set.of(GrantType.PASSWORD)
        );

        assertThat(client.isScopeAllowed("user.read")).isTrue();
        assertThat(client.isScopeAllowed("user.write")).isTrue();
    }

    @Test
    void isScopeAllowed_withNonMatchingScope_returnsFalse() {
        ServiceClient client = ServiceClient.createPublic(
                "test",
                "Test",
                Set.of("user.read"),
                Set.of(GrantType.PASSWORD)
        );

        assertThat(client.isScopeAllowed("admin.full")).isFalse();
    }

    @Test
    void isScopeAllowed_withWildcardAll_matchesAnyScope() {
        ServiceClient client = ServiceClient.createPublic(
                "test",
                "Test",
                Set.of("*"),
                Set.of(GrantType.PASSWORD)
        );

        assertThat(client.isScopeAllowed("anything.here")).isTrue();
        assertThat(client.isScopeAllowed("user.read")).isTrue();
        assertThat(client.isScopeAllowed("admin.full")).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "engine.*, engine.container.create, true",
            "engine.*, engine.container.delete, true",
            "engine.*, engine.module.read, true",
            "engine.*, service.token.read, false",
            "engine.*, engine, false",
            "user.*, user.read, true",
            "user.*, user.write, true",
            "user.*, user, false"
    })
    void isScopeAllowed_withWildcardPattern_matchesPrefix(String allowedScope, String requestedScope, boolean expected) {
        ServiceClient client = ServiceClient.createPublic(
                "test",
                "Test",
                Set.of(allowedScope),
                Set.of(GrantType.PASSWORD)
        );

        assertThat(client.isScopeAllowed(requestedScope)).isEqualTo(expected);
    }

    @Test
    void isScopeAllowed_withMultipleWildcards_matchesAny() {
        ServiceClient client = ServiceClient.createPublic(
                "test",
                "Test",
                Set.of("engine.*", "service.*"),
                Set.of(GrantType.PASSWORD)
        );

        assertThat(client.isScopeAllowed("engine.container.create")).isTrue();
        assertThat(client.isScopeAllowed("service.token.read")).isTrue();
        assertThat(client.isScopeAllowed("admin.full")).isFalse();
    }

    @Test
    void isScopeAllowed_withMixedExactAndWildcard_matchesBoth() {
        ServiceClient client = ServiceClient.createPublic(
                "test",
                "Test",
                Set.of("admin.full", "engine.*"),
                Set.of(GrantType.PASSWORD)
        );

        assertThat(client.isScopeAllowed("admin.full")).isTrue();
        assertThat(client.isScopeAllowed("engine.module.read")).isTrue();
        assertThat(client.isScopeAllowed("user.read")).isFalse();
    }

    // ============= areAllScopesAllowed Tests =============

    @Test
    void areAllScopesAllowed_withAllMatching_returnsTrue() {
        ServiceClient client = ServiceClient.createPublic(
                "test",
                "Test",
                Set.of("user.read", "user.write"),
                Set.of(GrantType.PASSWORD)
        );

        assertThat(client.areAllScopesAllowed(Set.of("user.read", "user.write"))).isTrue();
    }

    @Test
    void areAllScopesAllowed_withSomeNotMatching_returnsFalse() {
        ServiceClient client = ServiceClient.createPublic(
                "test",
                "Test",
                Set.of("user.read"),
                Set.of(GrantType.PASSWORD)
        );

        assertThat(client.areAllScopesAllowed(Set.of("user.read", "admin.full"))).isFalse();
    }

    @Test
    void areAllScopesAllowed_withWildcardAll_returnsTrue() {
        ServiceClient client = ServiceClient.createPublic(
                "test",
                "Test",
                Set.of("*"),
                Set.of(GrantType.PASSWORD)
        );

        assertThat(client.areAllScopesAllowed(Set.of("anything", "at.all"))).isTrue();
    }

    @Test
    void areAllScopesAllowed_withWildcardPattern_matchesPrefix() {
        ServiceClient client = ServiceClient.createPublic(
                "test",
                "Test",
                Set.of("engine.*"),
                Set.of(GrantType.PASSWORD)
        );

        assertThat(client.areAllScopesAllowed(Set.of("engine.container.create", "engine.module.read"))).isTrue();
        assertThat(client.areAllScopesAllowed(Set.of("engine.container.create", "service.token.read"))).isFalse();
    }

    // ============= filterAllowedScopes Tests =============

    @Test
    void filterAllowedScopes_withWildcardAll_returnsAllRequested() {
        ServiceClient client = ServiceClient.createPublic(
                "test",
                "Test",
                Set.of("*"),
                Set.of(GrantType.PASSWORD)
        );

        Set<String> requested = Set.of("scope1", "scope2", "scope3");
        Set<String> filtered = client.filterAllowedScopes(requested);

        assertThat(filtered).containsExactlyInAnyOrderElementsOf(requested);
    }

    @Test
    void filterAllowedScopes_withLimitedScopes_filtersCorrectly() {
        ServiceClient client = ServiceClient.createPublic(
                "test",
                "Test",
                Set.of("user.read", "user.write"),
                Set.of(GrantType.PASSWORD)
        );

        Set<String> filtered = client.filterAllowedScopes(Set.of("user.read", "admin.full"));

        assertThat(filtered).containsExactly("user.read");
    }

    @Test
    void filterAllowedScopes_withWildcardPattern_filtersCorrectly() {
        ServiceClient client = ServiceClient.createPublic(
                "test",
                "Test",
                Set.of("engine.*"),
                Set.of(GrantType.PASSWORD)
        );

        Set<String> filtered = client.filterAllowedScopes(
                Set.of("engine.container.create", "engine.module.read", "service.token.read")
        );

        assertThat(filtered).containsExactlyInAnyOrder("engine.container.create", "engine.module.read");
    }

    // ============= Grant Type Tests =============

    @Test
    void isGrantTypeAllowed_withAllowedGrantType_returnsTrue() {
        ServiceClient client = ServiceClient.createConfidential(
                "test",
                "hash",
                "Test",
                Set.of("scope"),
                Set.of(GrantType.CLIENT_CREDENTIALS, GrantType.REFRESH_TOKEN)
        );

        assertThat(client.isGrantTypeAllowed(GrantType.CLIENT_CREDENTIALS)).isTrue();
        assertThat(client.isGrantTypeAllowed(GrantType.REFRESH_TOKEN)).isTrue();
    }

    @Test
    void isGrantTypeAllowed_withDisallowedGrantType_returnsFalse() {
        ServiceClient client = ServiceClient.createConfidential(
                "test",
                "hash",
                "Test",
                Set.of("scope"),
                Set.of(GrantType.CLIENT_CREDENTIALS)
        );

        assertThat(client.isGrantTypeAllowed(GrantType.PASSWORD)).isFalse();
    }

    // ============= State Tests =============

    @Test
    void canAuthenticate_whenEnabled_returnsTrue() {
        ServiceClient client = ServiceClient.createPublic(
                "test",
                "Test",
                Set.of("scope"),
                Set.of(GrantType.PASSWORD)
        );

        assertThat(client.canAuthenticate()).isTrue();
    }

    @Test
    void canAuthenticate_whenDisabled_returnsFalse() {
        ServiceClient client = ServiceClient.createPublic(
                "test",
                "Test",
                Set.of("scope"),
                Set.of(GrantType.PASSWORD)
        ).withEnabled(false);

        assertThat(client.canAuthenticate()).isFalse();
    }

    @Test
    void withEnabled_createsNewClientWithUpdatedState() {
        ServiceClient original = ServiceClient.createPublic(
                "test",
                "Test",
                Set.of("scope"),
                Set.of(GrantType.PASSWORD)
        );

        ServiceClient disabled = original.withEnabled(false);

        assertThat(original.enabled()).isTrue();
        assertThat(disabled.enabled()).isFalse();
        assertThat(disabled.clientId()).isEqualTo(original.clientId());
    }

    @Test
    void withSecretHash_createsNewClientWithUpdatedSecret() {
        ServiceClient original = ServiceClient.createConfidential(
                "test",
                "old-hash",
                "Test",
                Set.of("scope"),
                Set.of(GrantType.CLIENT_CREDENTIALS)
        );

        ServiceClient updated = original.withSecretHash("new-hash");

        assertThat(original.clientSecretHash()).isEqualTo("old-hash");
        assertThat(updated.clientSecretHash()).isEqualTo("new-hash");
    }

    // ============= Defensive Copy Tests =============

    @Test
    void constructor_makesDefensiveCopyOfScopes() {
        Set<String> mutableScopes = new java.util.HashSet<>();
        mutableScopes.add("scope1");

        ServiceClient client = ServiceClient.createPublic(
                "test",
                "Test",
                mutableScopes,
                Set.of(GrantType.PASSWORD)
        );

        mutableScopes.add("scope2");

        assertThat(client.allowedScopes()).containsExactly("scope1");
    }

    @Test
    void constructor_makesDefensiveCopyOfGrantTypes() {
        Set<GrantType> mutableGrants = new java.util.HashSet<>();
        mutableGrants.add(GrantType.PASSWORD);

        ServiceClient client = ServiceClient.createPublic(
                "test",
                "Test",
                Set.of("scope"),
                mutableGrants
        );

        mutableGrants.add(GrantType.REFRESH_TOKEN);

        assertThat(client.allowedGrantTypes()).containsExactly(GrantType.PASSWORD);
    }
}
