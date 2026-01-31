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

package ca.samanthaireland.lightning.controlplane.provider.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.Optional;

/**
 * Configuration for JWT authentication in the control plane.
 *
 * <p>When enabled, dashboard and admin endpoints will require JWT authentication.
 * Tokens can be validated either:
 * <ul>
 *   <li>Locally using SmallRye JWT (requires public key configuration)</li>
 *   <li>Via external lightning-auth service (service-to-service validation)</li>
 * </ul>
 *
 * <p>Node endpoints continue to use the X-Control-Plane-Token header authentication.
 */
@ConfigMapping(prefix = "control-plane.jwt")
public interface JwtAuthConfig {

    /**
     * Whether JWT authentication is enabled for admin endpoints.
     *
     * @return true if JWT auth is enabled (default: false)
     */
    @WithName("enabled")
    @WithDefault("false")
    boolean enabled();

    /**
     * URL of the lightning-auth service for remote token validation.
     * If set, tokens will be validated via the /api/validate endpoint.
     * If not set, local JWT validation will be used.
     *
     * @return auth service URL, if configured
     */
    @WithName("auth-service-url")
    Optional<String> authServiceUrl();

    /**
     * Connection timeout for auth service calls in milliseconds.
     *
     * @return connection timeout (default: 5000)
     */
    @WithName("connect-timeout-ms")
    @WithDefault("5000")
    int connectTimeoutMs();

    /**
     * Request timeout for auth service calls in milliseconds.
     *
     * @return request timeout (default: 10000)
     */
    @WithName("request-timeout-ms")
    @WithDefault("10000")
    int requestTimeoutMs();

    /**
     * Roles allowed for admin operations.
     * Comma-separated list of role names.
     *
     * @return admin roles (default: admin)
     */
    @WithName("admin-roles")
    @WithDefault("admin")
    String adminRoles();

    /**
     * Roles allowed for view-only operations.
     * Comma-separated list of role names.
     *
     * @return view roles (default: admin,view_only)
     */
    @WithName("view-roles")
    @WithDefault("admin,view_only")
    String viewRoles();

    /**
     * Checks if remote auth service validation is configured.
     *
     * @return true if auth service URL is set
     */
    default boolean useRemoteValidation() {
        return authServiceUrl().isPresent() && !authServiceUrl().get().isBlank();
    }

    // OAuth2 Client Credentials Configuration

    /**
     * OAuth2 client ID for service-to-service authentication.
     *
     * @return client ID (default: control-plane)
     */
    @WithName("oauth2-client-id")
    @WithDefault("control-plane")
    String oauth2ClientId();

    /**
     * OAuth2 client secret for service-to-service authentication.
     *
     * @return client secret
     */
    @WithName("oauth2-client-secret")
    Optional<String> oauth2ClientSecret();

    /**
     * Scopes to request when obtaining service token.
     * Space-delimited list of scopes.
     *
     * @return scopes (default: service.match-token.issue service.match-token.validate)
     */
    @WithName("oauth2-scopes")
    @WithDefault("service.match-token.issue service.match-token.validate")
    String oauth2Scopes();

    /**
     * Buffer time in seconds before token expiry to trigger refresh.
     *
     * @return buffer in seconds (default: 60)
     */
    @WithName("oauth2-token-refresh-buffer-seconds")
    @WithDefault("60")
    int oauth2TokenRefreshBufferSeconds();

    /**
     * Checks if OAuth2 client credentials are configured.
     *
     * @return true if client secret is set
     */
    default boolean isOAuth2Configured() {
        return oauth2ClientSecret().isPresent() && !oauth2ClientSecret().get().isBlank();
    }
}
