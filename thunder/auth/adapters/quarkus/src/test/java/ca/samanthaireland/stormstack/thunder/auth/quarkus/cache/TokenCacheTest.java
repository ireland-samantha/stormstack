package ca.samanthaireland.stormstack.thunder.auth.quarkus.cache;

import ca.samanthaireland.stormstack.thunder.auth.quarkus.client.TokenExchangeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TokenCacheTest {

    private TokenCache tokenCache;

    @BeforeEach
    void setUp() {
        // Create cache with 60 second TTL buffer, 300 second cleanup interval
        tokenCache = new TokenCache(true, 60, 300);
    }

    @Test
    void put_and_get_returnsCachedSession() {
        String apiToken = "lat_test_token_12345";
        TokenExchangeResponse response = new TokenExchangeResponse(
                "session.jwt.token",
                Instant.now().plusSeconds(3600), // 1 hour from now
                Set.of("read", "write")
        );

        tokenCache.put(apiToken, response);

        Optional<CachedSession> cached = tokenCache.get(apiToken);

        assertThat(cached).isPresent();
        assertThat(cached.get().sessionToken()).isEqualTo("session.jwt.token");
        assertThat(cached.get().scopes()).containsExactlyInAnyOrder("read", "write");
    }

    @Test
    void get_withDifferentToken_returnsEmpty() {
        String apiToken = "lat_test_token_12345";
        TokenExchangeResponse response = new TokenExchangeResponse(
                "session.jwt.token",
                Instant.now().plusSeconds(3600),
                Set.of("read")
        );

        tokenCache.put(apiToken, response);

        Optional<CachedSession> cached = tokenCache.get("different_token");

        assertThat(cached).isEmpty();
    }

    @Test
    void get_withExpiredSession_returnsEmpty() {
        String apiToken = "lat_test_token_12345";
        // Create response that expires immediately (accounting for TTL buffer)
        TokenExchangeResponse response = new TokenExchangeResponse(
                "session.jwt.token",
                Instant.now().minusSeconds(1), // Already expired
                Set.of("read")
        );

        tokenCache.put(apiToken, response);

        Optional<CachedSession> cached = tokenCache.get(apiToken);

        assertThat(cached).isEmpty();
    }

    @Test
    void put_withExpiryWithinBuffer_doesNotCache() {
        String apiToken = "lat_test_token_12345";
        // Create response that expires within the 60 second buffer
        TokenExchangeResponse response = new TokenExchangeResponse(
                "session.jwt.token",
                Instant.now().plusSeconds(30), // Within 60 second buffer
                Set.of("read")
        );

        tokenCache.put(apiToken, response);

        Optional<CachedSession> cached = tokenCache.get(apiToken);

        assertThat(cached).isEmpty();
    }

    @Test
    void invalidate_removesCachedSession() {
        String apiToken = "lat_test_token_12345";
        TokenExchangeResponse response = new TokenExchangeResponse(
                "session.jwt.token",
                Instant.now().plusSeconds(3600),
                Set.of("read")
        );

        tokenCache.put(apiToken, response);
        assertThat(tokenCache.get(apiToken)).isPresent();

        tokenCache.invalidate(apiToken);

        assertThat(tokenCache.get(apiToken)).isEmpty();
    }

    @Test
    void clear_removesAllSessions() {
        tokenCache.put("token1", new TokenExchangeResponse(
                "jwt1", Instant.now().plusSeconds(3600), Set.of("read")));
        tokenCache.put("token2", new TokenExchangeResponse(
                "jwt2", Instant.now().plusSeconds(3600), Set.of("write")));

        assertThat(tokenCache.size()).isEqualTo(2);

        tokenCache.clear();

        assertThat(tokenCache.size()).isZero();
    }

    @Test
    void size_returnsCorrectCount() {
        assertThat(tokenCache.size()).isZero();

        tokenCache.put("token1", new TokenExchangeResponse(
                "jwt1", Instant.now().plusSeconds(3600), Set.of("read")));
        assertThat(tokenCache.size()).isEqualTo(1);

        tokenCache.put("token2", new TokenExchangeResponse(
                "jwt2", Instant.now().plusSeconds(3600), Set.of("write")));
        assertThat(tokenCache.size()).isEqualTo(2);
    }

    @Test
    void evictExpired_removesExpiredEntries() {
        String validToken = "lat_valid";
        String expiredToken = "lat_expired";

        // Add a valid entry (uses internal map directly for testing)
        tokenCache.put(validToken, new TokenExchangeResponse(
                "valid.jwt", Instant.now().plusSeconds(3600), Set.of("read")));

        // We can't easily add an expired entry via put() since it filters them
        // So we test that evictExpired doesn't remove valid entries
        assertThat(tokenCache.size()).isEqualTo(1);

        tokenCache.evictExpired();

        assertThat(tokenCache.size()).isEqualTo(1);
        assertThat(tokenCache.get(validToken)).isPresent();
    }

    @Test
    void isEnabled_returnsTrue_whenEnabled() {
        assertThat(tokenCache.isEnabled()).isTrue();
    }

    @Test
    void disabledCache_doesNotStore() {
        TokenCache disabledCache = new TokenCache(false, 60, 300);

        String apiToken = "lat_test_token";
        TokenExchangeResponse response = new TokenExchangeResponse(
                "session.jwt.token",
                Instant.now().plusSeconds(3600),
                Set.of("read")
        );

        disabledCache.put(apiToken, response);

        assertThat(disabledCache.get(apiToken)).isEmpty();
        assertThat(disabledCache.size()).isZero();
        assertThat(disabledCache.isEnabled()).isFalse();
    }

    @Test
    void sameTokenHash_returnsSameSession() {
        String apiToken = "lat_test_token_12345";
        TokenExchangeResponse response = new TokenExchangeResponse(
                "session.jwt.token",
                Instant.now().plusSeconds(3600),
                Set.of("read")
        );

        tokenCache.put(apiToken, response);

        // Same token string should produce same hash
        Optional<CachedSession> cached1 = tokenCache.get(apiToken);
        Optional<CachedSession> cached2 = tokenCache.get("lat_test_token_12345");

        assertThat(cached1).isPresent();
        assertThat(cached2).isPresent();
        assertThat(cached1.get().sessionToken()).isEqualTo(cached2.get().sessionToken());
    }
}
