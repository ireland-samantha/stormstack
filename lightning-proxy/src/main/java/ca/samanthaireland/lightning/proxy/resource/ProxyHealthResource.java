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

package ca.samanthaireland.lightning.proxy.resource;

import ca.samanthaireland.lightning.proxy.config.GatewayConfig;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

/**
 * Health and status resource for the API gateway.
 */
@Path("/gateway")
@Produces(MediaType.APPLICATION_JSON)
public class ProxyHealthResource {

    private final GatewayConfig config;

    @Inject
    public ProxyHealthResource(GatewayConfig config) {
        this.config = config;
    }

    /**
     * Returns gateway status and configuration.
     */
    @GET
    @Path("/status")
    public Map<String, Object> getStatus() {
        return Map.of(
                "status", "UP",
                "service", "lightning-proxy",
                "backends", Map.of(
                        "auth", config.authUrl(),
                        "control-plane", config.controlPlaneUrl(),
                        "engine", config.engineUrl()
                ),
                "timeouts", Map.of(
                        "connectMs", config.connectTimeoutMs(),
                        "requestMs", config.requestTimeoutMs()
                )
        );
    }

    /**
     * Simple health check for load balancers.
     */
    @GET
    @Path("/ping")
    public Map<String, String> ping() {
        return Map.of("status", "ok");
    }
}
