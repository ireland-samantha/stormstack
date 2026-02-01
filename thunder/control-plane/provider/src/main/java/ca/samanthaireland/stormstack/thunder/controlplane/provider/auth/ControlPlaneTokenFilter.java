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

package ca.samanthaireland.stormstack.thunder.controlplane.provider.auth;

import ca.samanthaireland.stormstack.thunder.auth.quarkus.security.LightningPrincipal;
import ca.samanthaireland.stormstack.thunder.auth.quarkus.security.LightningSecurityContext;
import ca.samanthaireland.stormstack.thunder.controlplane.provider.config.QuarkusControlPlaneConfig;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.Set;

/**
 * Authentication filter for node registration and heartbeat endpoints.
 *
 * <p>This filter validates X-Control-Plane-Token headers for requests to
 * /api/nodes/* endpoints. When a valid token is provided, it sets up a
 * LightningSecurityContext with the appropriate node registration scopes.
 *
 * <p>This allows engine nodes to authenticate with a shared token rather
 * than requiring full OAuth2 authentication for internal cluster operations.
 */
@Provider
@Priority(Priorities.AUTHENTICATION - 100) // Run before other auth filters
@ApplicationScoped
public class ControlPlaneTokenFilter implements ContainerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(ControlPlaneTokenFilter.class);
    private static final String TOKEN_HEADER = "X-Control-Plane-Token";

    /**
     * Scopes granted to node registrations via X-Control-Plane-Token.
     * These cover all node management operations.
     */
    private static final Set<String> NODE_SCOPES = Set.of(
            "control-plane.node.register",
            "control-plane.node.manage"
    );

    private final QuarkusControlPlaneConfig config;

    @Inject
    public ControlPlaneTokenFilter(QuarkusControlPlaneConfig config) {
        this.config = config;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();

        // Only process node endpoints
        // Note: getPath() returns path without leading slash (e.g., "api/nodes/register")
        if (!isNodeEndpoint(path)) {
            return;
        }

        // Check for X-Control-Plane-Token header
        String token = requestContext.getHeaderString(TOKEN_HEADER);
        if (token == null || token.isBlank()) {
            // No token provided - let other filters handle auth
            log.debug("No X-Control-Plane-Token header on node endpoint: {}", path);
            return;
        }

        // Validate token
        if (!isValidToken(token)) {
            log.warn("Invalid X-Control-Plane-Token on node endpoint: {}", path);
            abortWithUnauthorized(requestContext, "Invalid control plane token");
            return;
        }

        // Token is valid - set up security context with node scopes
        LightningPrincipal principal = new LightningPrincipal(
                "node-service",
                "control-plane-node",
                NODE_SCOPES,
                null
        );

        LightningSecurityContext securityContext = new LightningSecurityContext(
                principal,
                requestContext.getSecurityContext().isSecure()
        );

        requestContext.setSecurityContext(securityContext);
        log.debug("Authenticated node request via X-Control-Plane-Token: {}", path);
    }

    /**
     * Validates the provided token against the configured auth token.
     * Uses constant-time comparison to prevent timing attacks.
     */
    private boolean isValidToken(String providedToken) {
        if (!config.requireAuth()) {
            return true;
        }

        String expectedToken = config.authToken().orElse(null);
        if (expectedToken == null || expectedToken.isBlank()) {
            // No token configured but auth required - deny
            log.warn("Auth required but no control-plane.auth-token configured");
            return false;
        }

        return constantTimeEquals(providedToken, expectedToken);
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(), b.getBytes());
    }

    /**
     * Checks if the path is a node endpoint.
     * Handles both with and without leading slash since JAX-RS getPath() behavior
     * can vary (typically returns without leading slash).
     */
    private boolean isNodeEndpoint(String path) {
        return path.startsWith("api/nodes") || path.startsWith("/api/nodes");
    }

    private void abortWithUnauthorized(ContainerRequestContext context, String message) {
        context.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("UNAUTHORIZED", message))
                .build());
    }

    private record ErrorResponse(String code, String message) {}
}
