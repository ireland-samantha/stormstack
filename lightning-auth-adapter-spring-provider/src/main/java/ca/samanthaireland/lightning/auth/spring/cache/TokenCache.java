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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * In-memory cache for session tokens obtained via API token exchange.
 *
 * <p>This cache stores session JWTs keyed by a SHA-256 hash of the API token
 * (for security - we never store the plaintext API token). Entries are
 * automatically expired based on the JWT's expiry time minus a configurable
 * buffer.
 *
 * <p>A background cleanup thread periodically removes expired entries to
 * prevent memory leaks.
 */
public class TokenCache {

    private static final Logger log = LoggerFactory.getLogger(TokenCache.class);

    private final ConcurrentHashMap<String, CachedSession> cache = new ConcurrentHashMap<>();
    private final int ttlBufferSeconds;
    private final boolean enabled;
    private final ScheduledExecutorService cleanupExecutor;

    /**
     * Represents a cached session with its JWT and expiry time.
     *
     * @param sessionToken the JWT session token
     * @param expiresAt    when the session expires (with buffer applied)
     * @param scopes       the granted scopes
     */
    public record CachedSession(
            String sessionToken,
            Instant expiresAt,
            Set<String> scopes
    ) {
        /**
         * Checks if this cached session has expired.
         *
         * @return true if expired
         */
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    /**
     * Creates a new token cache.
     *
     * @param properties the cache configuration properties
     */
    public TokenCache(LightningAuthProperties.CacheProperties properties) {
        this.enabled = properties.isEnabled();
        this.ttlBufferSeconds = properties.getTtlBufferSeconds();

        if (enabled) {
            this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "token-cache-cleanup");
                thread.setDaemon(true);
                return thread;
            });

            int cleanupInterval = properties.getCleanupIntervalSeconds();
            cleanupExecutor.scheduleAtFixedRate(
                    this::cleanup,
                    cleanupInterval,
                    cleanupInterval,
                    TimeUnit.SECONDS
            );

            log.info("TokenCache initialized with TTL buffer: {}s, cleanup interval: {}s",
                    ttlBufferSeconds, cleanupInterval);
        } else {
            this.cleanupExecutor = null;
            log.info("TokenCache is disabled");
        }
    }

    /**
     * Gets a cached session for the given API token.
     *
     * @param apiToken the plaintext API token
     * @return the cached session if present and not expired
     */
    public Optional<CachedSession> get(String apiToken) {
        if (!enabled) {
            return Optional.empty();
        }

        String key = hashToken(apiToken);
        CachedSession session = cache.get(key);

        if (session == null) {
            return Optional.empty();
        }

        if (session.isExpired()) {
            cache.remove(key);
            log.debug("Cache entry expired for token hash: {}...", key.substring(0, 8));
            return Optional.empty();
        }

        log.debug("Cache hit for token hash: {}...", key.substring(0, 8));
        return Optional.of(session);
    }

    /**
     * Stores a session in the cache.
     *
     * @param apiToken the plaintext API token (used to compute cache key)
     * @param response the token exchange response to cache
     */
    public void put(String apiToken, TokenExchangeResponse response) {
        if (!enabled) {
            return;
        }

        String key = hashToken(apiToken);
        Instant effectiveExpiry = response.expiresAt().minusSeconds(ttlBufferSeconds);

        if (Instant.now().isAfter(effectiveExpiry)) {
            log.debug("Token already expired or within buffer, not caching");
            return;
        }

        CachedSession session = new CachedSession(
                response.sessionToken(),
                effectiveExpiry,
                response.scopes()
        );

        cache.put(key, session);
        log.debug("Cached session for token hash: {}..., expires: {}",
                key.substring(0, 8), effectiveExpiry);
    }

    /**
     * Invalidates a cached session.
     *
     * @param apiToken the plaintext API token
     */
    public void invalidate(String apiToken) {
        if (!enabled) {
            return;
        }

        String key = hashToken(apiToken);
        cache.remove(key);
        log.debug("Invalidated cache entry for token hash: {}...", key.substring(0, 8));
    }

    /**
     * Clears all cached sessions.
     */
    public void clear() {
        cache.clear();
        log.info("Cache cleared");
    }

    /**
     * Returns the current number of cached entries.
     *
     * @return the cache size
     */
    public int size() {
        return cache.size();
    }

    /**
     * Shuts down the cache cleanup executor.
     */
    public void shutdown() {
        if (cleanupExecutor != null) {
            cleanupExecutor.shutdown();
            try {
                if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("TokenCache shutdown complete");
        }
    }

    /**
     * Removes expired entries from the cache.
     */
    private void cleanup() {
        int beforeSize = cache.size();
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        int removed = beforeSize - cache.size();

        if (removed > 0) {
            log.debug("Cache cleanup removed {} expired entries, {} remaining", removed, cache.size());
        }
    }

    /**
     * Computes SHA-256 hash of the API token for use as cache key.
     *
     * @param apiToken the plaintext API token
     * @return the hex-encoded hash
     */
    private String hashToken(String apiToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
