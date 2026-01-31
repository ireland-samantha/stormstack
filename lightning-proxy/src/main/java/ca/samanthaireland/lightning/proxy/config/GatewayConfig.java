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

package ca.samanthaireland.lightning.proxy.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Configuration for the API gateway backend services.
 */
@ConfigMapping(prefix = "gateway")
public interface GatewayConfig {

    /**
     * URL of the lightning-auth service.
     * @return auth service URL
     */
    @WithName("auth-url")
    @WithDefault("http://localhost:8082")
    String authUrl();

    /**
     * URL of the lightning-control-plane service.
     * @return control plane URL
     */
    @WithName("control-plane-url")
    @WithDefault("http://localhost:8081")
    String controlPlaneUrl();

    /**
     * URL of the lightning-engine service.
     * @return engine URL
     */
    @WithName("engine-url")
    @WithDefault("http://localhost:8080")
    String engineUrl();

    /**
     * HTTP request timeout in milliseconds.
     * @return timeout in ms
     */
    @WithName("request-timeout-ms")
    @WithDefault("30000")
    int requestTimeoutMs();

    /**
     * HTTP connect timeout in milliseconds.
     * @return connect timeout in ms
     */
    @WithName("connect-timeout-ms")
    @WithDefault("5000")
    int connectTimeoutMs();
}
