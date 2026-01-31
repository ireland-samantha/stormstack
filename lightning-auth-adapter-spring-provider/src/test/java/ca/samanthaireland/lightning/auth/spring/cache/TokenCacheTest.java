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

package ca.samanthaireland.lightning.auth.spring.cache;

import ca.samanthaireland.lightning.auth.spring.LightningAuthProperties;
import ca.samanthaireland.lightning.auth.spring.client.dto.TokenExchangeResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TokenCacheTest {

    private TokenCache cache;
    private LightningAuthProperties.CacheProperties cacheProperties;

    @BeforeEach
    void setUp() {
        cacheProperties = new LightningAuthProperties.CacheProperties();
        cacheProperties.setEnabled(true);
        cacheProperties.setTtlBufferSeconds(60);
        cacheProperties.setCleanupIntervalSeconds(300);
        cache = new TokenCache(cacheProperties);
    }

    @AfterEach
    void tearDown() {
        if (cache != null) {
            cache.shutdown();
        }
    }

    @Test
    void put_storesSession() {
        String apiToken = "lat_test_token_123";
        TokenExchangeResponse response = new TokenExchangeResponse(
                "jwt.token.here",
                Instant.now().plusSeconds(3600),
                Set.of("view_snapshots", "submit_commands")
        );

        cache.put(apiToken, response);

        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void get_returnsStoredSession() {
        String apiToken = "lat_test_token_456";
        TokenExchangeResponse response = new TokenExchangeResponse(
                "jwt.token.here",
                Instant.now().plusSeconds(3600),
                Set.of("view_snapshots")
        );

        cache.put(apiToken, response);
        Optional<TokenCache.CachedSession> result = cache.get(apiToken);

        assertThat(result).isPresent();
        assertThat(result.get().sessionToken()).isEqualTo("jwt.token.here");
        assertThat(result.get().scopes()).containsExactly("view_snapshots");
    }

    @Test
    void get_returnsMissForUnknownToken() {
        Optional<TokenCache.CachedSession> result = cache.get("unknown_token");

        assertThat(result).isEmpty();
    }

    @Test
    void get_returnsEmptyForExpiredSession() {
        String apiToken = "lat_expired_token";
        // Token expires in 30 seconds, but TTL buffer is 60 seconds, so effectively expired
        TokenExchangeResponse response = new TokenExchangeResponse(
                "jwt.token.here",
                Instant.now().plusSeconds(30),
                Set.of("view_snapshots")
        );

        cache.put(apiToken, response);
        Optional<TokenCache.CachedSession> result = cache.get(apiToken);

        // Should not cache tokens that are already within the buffer
        assertThat(cache.size()).isEqualTo(0);
        assertThat(result).isEmpty();
    }

    @Test
    void invalidate_removesSession() {
        String apiToken = "lat_to_invalidate";
        TokenExchangeResponse response = new TokenExchangeResponse(
                "jwt.token.here",
                Instant.now().plusSeconds(3600),
                Set.of("admin")
        );

        cache.put(apiToken, response);
        assertThat(cache.size()).isEqualTo(1);

        cache.invalidate(apiToken);

        assertThat(cache.size()).isEqualTo(0);
        assertThat(cache.get(apiToken)).isEmpty();
    }

    @Test
    void clear_removesAllSessions() {
        cache.put("token1", new TokenExchangeResponse("jwt1", Instant.now().plusSeconds(3600), Set.of()));
        cache.put("token2", new TokenExchangeResponse("jwt2", Instant.now().plusSeconds(3600), Set.of()));
        cache.put("token3", new TokenExchangeResponse("jwt3", Instant.now().plusSeconds(3600), Set.of()));

        assertThat(cache.size()).isEqualTo(3);

        cache.clear();

        assertThat(cache.size()).isEqualTo(0);
    }

    @Test
    void disabledCache_doesNotStore() {
        cacheProperties.setEnabled(false);
        cache.shutdown();
        cache = new TokenCache(cacheProperties);

        String apiToken = "lat_disabled_cache";
        TokenExchangeResponse response = new TokenExchangeResponse(
                "jwt.token.here",
                Instant.now().plusSeconds(3600),
                Set.of("view_snapshots")
        );

        cache.put(apiToken, response);

        assertThat(cache.size()).isEqualTo(0);
        assertThat(cache.get(apiToken)).isEmpty();
    }

    @Test
    void differentTokens_haveDifferentCacheKeys() {
        TokenExchangeResponse response1 = new TokenExchangeResponse(
                "jwt1",
                Instant.now().plusSeconds(3600),
                Set.of("scope1")
        );
        TokenExchangeResponse response2 = new TokenExchangeResponse(
                "jwt2",
                Instant.now().plusSeconds(3600),
                Set.of("scope2")
        );

        cache.put("token_a", response1);
        cache.put("token_b", response2);

        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.get("token_a").get().sessionToken()).isEqualTo("jwt1");
        assertThat(cache.get("token_b").get().sessionToken()).isEqualTo("jwt2");
    }

    @Test
    void cachedSession_isExpired_returnsTrue_afterExpiry() {
        Instant pastTime = Instant.now().minusSeconds(10);
        TokenCache.CachedSession session = new TokenCache.CachedSession(
                "expired.jwt",
                pastTime,
                Set.of()
        );

        assertThat(session.isExpired()).isTrue();
    }

    @Test
    void cachedSession_isExpired_returnsFalse_beforeExpiry() {
        Instant futureTime = Instant.now().plusSeconds(3600);
        TokenCache.CachedSession session = new TokenCache.CachedSession(
                "valid.jwt",
                futureTime,
                Set.of()
        );

        assertThat(session.isExpired()).isFalse();
    }
}
