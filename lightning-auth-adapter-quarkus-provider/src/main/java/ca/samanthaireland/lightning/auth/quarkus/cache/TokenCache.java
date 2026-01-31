package ca.samanthaireland.lightning.auth.quarkus.cache;

import ca.samanthaireland.lightning.auth.quarkus.client.TokenExchangeResponse;
import ca.samanthaireland.lightning.auth.quarkus.config.LightningAuthConfig;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * In-memory cache for API token to session JWT mappings.
 *
 * <p>Uses SHA-256 hashing for cache keys to avoid storing raw API tokens.
 * Includes automatic cleanup of expired entries.
 */
@ApplicationScoped
@IfBuildProperty(name = "lightning.auth.filters.enabled", stringValue = "true", enableIfMissing = false)
public class TokenCache {

    private static final Logger LOG = Logger.getLogger(TokenCache.class);

    private final ConcurrentHashMap<String, CachedSession> cache = new ConcurrentHashMap<>();
    private final boolean enabled;
    private final int ttlBufferSeconds;
    private final int cleanupIntervalSeconds;

    private ScheduledExecutorService cleanupExecutor;

    @Inject
    public TokenCache(LightningAuthConfig config) {
        this.enabled = config.cache().enabled();
        this.ttlBufferSeconds = config.cache().ttlBufferSeconds();
        this.cleanupIntervalSeconds = config.cache().cleanupIntervalSeconds();
    }

    /**
     * Constructor for testing.
     */
    TokenCache(boolean enabled, int ttlBufferSeconds, int cleanupIntervalSeconds) {
        this.enabled = enabled;
        this.ttlBufferSeconds = ttlBufferSeconds;
        this.cleanupIntervalSeconds = cleanupIntervalSeconds;
    }

    void onStart(@Observes StartupEvent ev) {
        if (enabled) {
            startCleanupScheduler();
        }
    }

    void onStop(@Observes ShutdownEvent ev) {
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
        }
    }

    /**
     * Get a cached session for the given API token.
     *
     * @param apiToken the API token
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
            return Optional.empty();
        }

        return Optional.of(session);
    }

    /**
     * Store a session in the cache.
     *
     * @param apiToken the API token (will be hashed)
     * @param response the token exchange response
     */
    public void put(String apiToken, TokenExchangeResponse response) {
        if (!enabled) {
            return;
        }

        String key = hashToken(apiToken);

        // Adjust expiry by buffer to avoid using tokens about to expire
        Instant adjustedExpiry = response.expiresAt().minusSeconds(ttlBufferSeconds);

        // Don't cache if already expired (or within buffer)
        if (Instant.now().isAfter(adjustedExpiry)) {
            return;
        }

        CachedSession session = new CachedSession(
                response.sessionToken(),
                adjustedExpiry,
                response.scopes()
        );

        cache.put(key, session);
        LOG.debugf("Cached session for token hash %s, expires at %s", key, adjustedExpiry);
    }

    /**
     * Invalidate a cached session.
     *
     * @param apiToken the API token
     */
    public void invalidate(String apiToken) {
        if (!enabled) {
            return;
        }

        String key = hashToken(apiToken);
        cache.remove(key);
    }

    /**
     * Clear all cached sessions.
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Get the current number of cached entries.
     */
    public int size() {
        return cache.size();
    }

    /**
     * Check if caching is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    private void startCleanupScheduler() {
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "token-cache-cleanup");
            t.setDaemon(true);
            return t;
        });

        cleanupExecutor.scheduleAtFixedRate(
                this::evictExpired,
                cleanupIntervalSeconds,
                cleanupIntervalSeconds,
                TimeUnit.SECONDS
        );

        LOG.infof("Token cache cleanup scheduled every %d seconds", cleanupIntervalSeconds);
    }

    void evictExpired() {
        int before = cache.size();
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        int removed = before - cache.size();

        if (removed > 0) {
            LOG.debugf("Evicted %d expired cache entries", removed);
        }
    }

    private String hashToken(String apiToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
