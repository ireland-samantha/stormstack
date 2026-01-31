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

package ca.samanthaireland.stormstack.thunder.controlplane.provider.config;

import ca.samanthaireland.stormstack.thunder.controlplane.proxy.config.ProxyConfiguration;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Quarkus configuration mapping for the node proxy feature.
 */
@ConfigMapping(prefix = "control-plane.proxy")
public interface QuarkusProxyConfig extends ProxyConfiguration {

    /**
     * Whether the proxy feature is enabled by default.
     */
    @Override
    @WithName("enabled")
    @WithDefault("true")
    boolean enabled();

    /**
     * Timeout in seconds for proxied requests.
     */
    @Override
    @WithName("timeout-seconds")
    @WithDefault("30")
    int timeoutSeconds();

    /**
     * Maximum request body size in bytes (default 10MB).
     */
    @Override
    @WithName("max-request-body-bytes")
    @WithDefault("10485760")
    long maxRequestBodyBytes();
}
