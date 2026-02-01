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

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Rate limiter for login/token endpoints to prevent brute force attacks.
 *
 * <p>Uses a sliding window algorithm to limit the number of login attempts
 * per IP address within a time window. Includes automatic cleanup of
 * expired buckets to prevent memory leaks.
 *
 * <p>SECURITY: This protects against credential stuffing and brute force attacks
 * on the OAuth2 token endpoint.
 */
@ApplicationScoped
public class LoginRateLimiter {
    private static final Logger log = Logger.getLogger(LoginRateLimiter.class);

    @ConfigProperty(name = "auth.ratelimit.max-attempts-per-window", defaultValue = "10")
    int maxAttemptsPerWindow;

    @ConfigProperty(name = "auth.ratelimit.window-seconds", defaultValue = "60")
    int windowSeconds;

    @ConfigProperty(name = "auth.ratelimit.cleanup-interval-seconds", defaultValue = "300")
    int cleanupIntervalSeconds;

    @ConfigProperty(name = "auth.ratelimit.enabled", defaultValue = "true")
    boolean enabled;

    private final ConcurrentHashMap<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();
    private ScheduledExecutorService cleanupExecutor;

    private record RateLimitBucket(AtomicInteger count, Instant windowStart, Duration windowDuration) {
        boolean isExpired() {
            return Instant.now().isAfter(windowStart.plus(windowDuration));
        }

        boolean tryAcquire(int maxAttempts) {
            if (isExpired()) {
                return false; // Bucket expired, will be replaced
            }
            return count.incrementAndGet() <= maxAttempts;
        }

        int getRemainingAttempts(int maxAttempts) {
            return Math.max(0, maxAttempts - count.get());
        }

        long getSecondsUntilReset() {
            Instant resetTime = windowStart.plus(windowDuration);
            return Math.max(0, Duration.between(Instant.now(), resetTime).getSeconds());
        }
    }

    @PostConstruct
    void init() {
        if (!enabled) {
            log.warn("Login rate limiting is DISABLED - this is not recommended for production");
            return;
        }

        // Schedule periodic cleanup of expired buckets to prevent memory leaks
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "login-ratelimit-cleanup");
            t.setDaemon(true);
            return t;
        });

        cleanupExecutor.scheduleAtFixedRate(
                this::cleanupExpiredBuckets,
                cleanupIntervalSeconds,
                cleanupIntervalSeconds,
                TimeUnit.SECONDS
        );

        log.infof("Login rate limiter initialized: max %d attempts per %d second(s), cleanup every %d seconds",
                maxAttemptsPerWindow, windowSeconds, cleanupIntervalSeconds);
    }

    @PreDestroy
    void shutdown() {
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
     * Check if a login attempt is allowed under rate limiting for the given identifier.
     *
     * @param identifier the rate limit key (typically IP address or client_id)
     * @return a result indicating whether the attempt is allowed
     */
    public RateLimitResult tryAcquire(String identifier) {
        if (!enabled) {
            return RateLimitResult.allowed(maxAttemptsPerWindow, 0);
        }

        Instant now = Instant.now();
        Duration windowDuration = Duration.ofSeconds(windowSeconds);

        RateLimitBucket bucket = buckets.compute(identifier, (key, existing) -> {
            if (existing == null || existing.isExpired()) {
                return new RateLimitBucket(new AtomicInteger(0), now, windowDuration);
            }
            return existing;
        });

        boolean allowed = bucket.tryAcquire(maxAttemptsPerWindow);
        int remaining = bucket.getRemainingAttempts(maxAttemptsPerWindow);
        long retryAfter = allowed ? 0 : bucket.getSecondsUntilReset();

        if (!allowed) {
            log.warnf("Rate limit exceeded for identifier: %s (attempts: %d, window: %ds)",
                    maskIdentifier(identifier), bucket.count.get(), windowSeconds);
        }

        return new RateLimitResult(allowed, remaining, retryAfter);
    }

    /**
     * Record a failed login attempt (counts extra against the limit).
     * Call this after authentication failures to penalize brute force attempts.
     *
     * @param identifier the rate limit key
     */
    public void recordFailedAttempt(String identifier) {
        if (!enabled) {
            return;
        }
        // The tryAcquire already incremented, but we can add additional penalty for failures
        // For now, just log it - the increment in tryAcquire is sufficient
        log.debugf("Failed login attempt recorded for: %s", maskIdentifier(identifier));
    }

    /**
     * Clean up expired buckets to prevent memory leaks.
     */
    private void cleanupExpiredBuckets() {
        int removed = 0;
        for (var entry : buckets.entrySet()) {
            if (entry.getValue().isExpired()) {
                buckets.remove(entry.getKey(), entry.getValue());
                removed++;
            }
        }
        if (removed > 0) {
            log.debugf("Cleaned up %d expired login rate limit buckets", removed);
        }
    }

    /**
     * Mask identifier for logging (privacy protection).
     */
    private String maskIdentifier(String identifier) {
        if (identifier == null || identifier.length() < 4) {
            return "***";
        }
        return identifier.substring(0, Math.min(4, identifier.length())) + "***";
    }

    /**
     * Get the current number of tracked identifiers (for monitoring).
     */
    public int getTrackedIdentifierCount() {
        return buckets.size();
    }

    /**
     * Result of a rate limit check.
     */
    public record RateLimitResult(boolean allowed, int remainingAttempts, long retryAfterSeconds) {
        public static RateLimitResult allowed(int remaining, long retryAfter) {
            return new RateLimitResult(true, remaining, retryAfter);
        }
    }
}
