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

package ca.samanthaireland.stormstack.thunder.auth.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Lightning Auth Spring integration.
 *
 * <p>Configure these properties in your application.yml or application.properties:
 * <pre>
 * lightning:
 *   auth:
 *     enabled: true
 *     service-url: http://localhost:8082
 *     connect-timeout-ms: 5000
 *     request-timeout-ms: 10000
 *     cache:
 *       enabled: true
 *       ttl-buffer-seconds: 60
 *     jwt:
 *       issuer: https://lightningfirefly.com
 *       secret: ${JWT_SECRET:}
 * </pre>
 */
@ConfigurationProperties(prefix = "lightning.auth")
public class LightningAuthProperties {

    /**
     * Whether Lightning Auth integration is enabled.
     */
    private boolean enabled = true;

    /**
     * Base URL of the Lightning Auth service.
     */
    private String serviceUrl = "http://localhost:8082";

    /**
     * HTTP client connect timeout in milliseconds.
     */
    private int connectTimeoutMs = 5000;

    /**
     * HTTP client request timeout in milliseconds.
     */
    private int requestTimeoutMs = 10000;

    /**
     * Token cache configuration.
     */
    private CacheProperties cache = new CacheProperties();

    /**
     * JWT configuration.
     */
    private JwtProperties jwt = new JwtProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServiceUrl() {
        return serviceUrl;
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public CacheProperties getCache() {
        return cache;
    }

    public void setCache(CacheProperties cache) {
        this.cache = cache;
    }

    public JwtProperties getJwt() {
        return jwt;
    }

    public void setJwt(JwtProperties jwt) {
        this.jwt = jwt;
    }

    /**
     * Token cache configuration properties.
     */
    public static class CacheProperties {

        /**
         * Whether token caching is enabled.
         */
        private boolean enabled = true;

        /**
         * Number of seconds before JWT expiry to consider cache entry expired.
         * This ensures we don't use a cached token that's about to expire.
         */
        private int ttlBufferSeconds = 60;

        /**
         * Interval in seconds between cache cleanup runs.
         */
        private int cleanupIntervalSeconds = 300;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTtlBufferSeconds() {
            return ttlBufferSeconds;
        }

        public void setTtlBufferSeconds(int ttlBufferSeconds) {
            this.ttlBufferSeconds = ttlBufferSeconds;
        }

        public int getCleanupIntervalSeconds() {
            return cleanupIntervalSeconds;
        }

        public void setCleanupIntervalSeconds(int cleanupIntervalSeconds) {
            this.cleanupIntervalSeconds = cleanupIntervalSeconds;
        }
    }

    /**
     * JWT configuration properties.
     */
    public static class JwtProperties {

        /**
         * Expected JWT issuer (optional). If set, tokens with different issuers will be rejected.
         */
        private String issuer;

        /**
         * HMAC secret for JWT validation. Must match the secret used by the auth service.
         */
        private String secret;

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }
}
