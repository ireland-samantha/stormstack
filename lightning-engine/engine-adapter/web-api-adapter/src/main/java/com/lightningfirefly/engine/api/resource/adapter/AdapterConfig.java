package com.lightningfirefly.engine.api.resource.adapter;

import java.time.Duration;

/**
 * Configuration for HTTP adapters.
 *
 * <p>Provides sensible defaults for HTTP client configuration
 * that can be overridden when creating adapter instances.
 */
public final class AdapterConfig {

    /**
     * Default HTTP connection timeout.
     */
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Default HTTP request timeout.
     */
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final Duration connectTimeout;
    private final Duration requestTimeout;

    private AdapterConfig(Duration connectTimeout, Duration requestTimeout) {
        this.connectTimeout = connectTimeout;
        this.requestTimeout = requestTimeout;
    }

    /**
     * Create configuration with default values.
     */
    public static AdapterConfig defaults() {
        return new AdapterConfig(DEFAULT_CONNECT_TIMEOUT, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * Create configuration with custom connect timeout.
     */
    public static AdapterConfig withConnectTimeout(Duration connectTimeout) {
        return new AdapterConfig(connectTimeout, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * Create configuration with custom timeouts.
     */
    public static AdapterConfig of(Duration connectTimeout, Duration requestTimeout) {
        return new AdapterConfig(connectTimeout, requestTimeout);
    }

    /**
     * Get the connection timeout.
     */
    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Get the request timeout.
     */
    public Duration getRequestTimeout() {
        return requestTimeout;
    }
}
