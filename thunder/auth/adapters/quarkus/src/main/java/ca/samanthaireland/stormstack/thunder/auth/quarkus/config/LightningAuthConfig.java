package ca.samanthaireland.stormstack.thunder.auth.quarkus.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.Optional;

/**
 * Configuration for the Lightning Auth Quarkus adapter.
 *
 * <p>Example configuration in application.properties:
 * <pre>
 * lightning.auth.enabled=true
 * lightning.auth.service-url=http://localhost:8082
 * lightning.auth.connect-timeout-ms=5000
 * lightning.auth.request-timeout-ms=10000
 * lightning.auth.cache.enabled=true
 * lightning.auth.cache.ttl-buffer-seconds=60
 * lightning.auth.jwt.secret=${JWT_SECRET}
 * lightning.auth.jwt.issuer=https://lightningfirefly.com
 * </pre>
 */
@ConfigMapping(prefix = "lightning.auth")
public interface LightningAuthConfig {

    /**
     * Whether Lightning Auth integration is enabled.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Base URL of the Lightning Auth service.
     */
    @WithName("service-url")
    @WithDefault("http://localhost:8082")
    String serviceUrl();

    /**
     * HTTP client connection timeout in milliseconds.
     */
    @WithName("connect-timeout-ms")
    @WithDefault("5000")
    int connectTimeoutMs();

    /**
     * HTTP client request timeout in milliseconds.
     */
    @WithName("request-timeout-ms")
    @WithDefault("10000")
    int requestTimeoutMs();

    /**
     * Token cache configuration.
     */
    CacheConfig cache();

    /**
     * JWT validation configuration.
     */
    JwtConfig jwt();

    /**
     * Cache configuration for API token to session JWT mapping.
     */
    interface CacheConfig {

        /**
         * Whether token caching is enabled.
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * Buffer in seconds to expire cache entries before JWT actually expires.
         * This prevents using tokens that are about to expire.
         */
        @WithName("ttl-buffer-seconds")
        @WithDefault("60")
        int ttlBufferSeconds();

        /**
         * Interval in seconds between cache cleanup runs.
         */
        @WithName("cleanup-interval-seconds")
        @WithDefault("300")
        int cleanupIntervalSeconds();
    }

    /**
     * JWT validation configuration.
     */
    interface JwtConfig {

        /**
         * HMAC secret for JWT signature validation.
         * Required for local JWT validation.
         */
        Optional<String> secret();

        /**
         * Expected JWT issuer claim for validation.
         */
        Optional<String> issuer();
    }
}
