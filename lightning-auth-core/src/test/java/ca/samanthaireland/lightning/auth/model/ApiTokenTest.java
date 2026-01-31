/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.lightning.auth.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class ApiTokenTest {

    @Test
    void create_generatesIdAndTimestamp() {
        UserId userId = UserId.generate();
        ApiToken token = ApiToken.create(userId, "test-token", "hash123", Set.of("read"), null);

        assertThat(token.id()).isNotNull();
        assertThat(token.userId()).isEqualTo(userId);
        assertThat(token.name()).isEqualTo("test-token");
        assertThat(token.tokenHash()).isEqualTo("hash123");
        assertThat(token.scopes()).containsExactly("read");
        assertThat(token.createdAt()).isNotNull();
        assertThat(token.expiresAt()).isNull();
        assertThat(token.revokedAt()).isNull();
        assertThat(token.lastUsedAt()).isNull();
        assertThat(token.lastUsedIp()).isNull();
    }

    @Test
    void isActive_returnsTrueForActiveToken() {
        ApiToken token = ApiToken.create(UserId.generate(), "test", "hash", Set.of(), null);

        assertThat(token.isActive()).isTrue();
    }

    @Test
    void isActive_returnsFalseForRevokedToken() {
        ApiToken token = ApiToken.create(UserId.generate(), "test", "hash", Set.of(), null);
        ApiToken revoked = token.revoke();

        assertThat(revoked.isActive()).isFalse();
    }

    @Test
    void isActive_returnsFalseForExpiredToken() {
        Instant pastExpiry = Instant.now().minus(1, ChronoUnit.HOURS);
        ApiToken token = ApiToken.create(UserId.generate(), "test", "hash", Set.of(), pastExpiry);

        assertThat(token.isActive()).isFalse();
    }

    @Test
    void isExpired_returnsTrueWhenPastExpiry() {
        Instant pastExpiry = Instant.now().minus(1, ChronoUnit.HOURS);
        ApiToken token = ApiToken.create(UserId.generate(), "test", "hash", Set.of(), pastExpiry);

        assertThat(token.isExpired()).isTrue();
    }

    @Test
    void isExpired_returnsFalseWhenNoExpiry() {
        ApiToken token = ApiToken.create(UserId.generate(), "test", "hash", Set.of(), null);

        assertThat(token.isExpired()).isFalse();
    }

    @Test
    void revoke_setsRevokedAt() {
        ApiToken token = ApiToken.create(UserId.generate(), "test", "hash", Set.of(), null);
        ApiToken revoked = token.revoke();

        assertThat(revoked.revokedAt()).isNotNull();
        assertThat(revoked.isRevoked()).isTrue();
    }

    @Test
    void recordUsage_updatesLastUsedInfo() {
        ApiToken token = ApiToken.create(UserId.generate(), "test", "hash", Set.of(), null);
        ApiToken used = token.recordUsage("192.168.1.1");

        assertThat(used.lastUsedAt()).isNotNull();
        assertThat(used.lastUsedIp()).isEqualTo("192.168.1.1");
    }

    @Test
    void hasScope_returnsTrueWhenHasScope() {
        ApiToken token = ApiToken.create(UserId.generate(), "test", "hash", Set.of("read", "write"), null);

        assertThat(token.hasScope("read")).isTrue();
        assertThat(token.hasScope("write")).isTrue();
    }

    @Test
    void hasScope_returnsFalseWhenMissingScope() {
        ApiToken token = ApiToken.create(UserId.generate(), "test", "hash", Set.of("read"), null);

        assertThat(token.hasScope("admin")).isFalse();
    }

    @Test
    void scopes_areImmutable() {
        ApiToken token = ApiToken.create(UserId.generate(), "test", "hash", Set.of("read"), null);

        assertThatThrownBy(() -> token.scopes().add("admin"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
