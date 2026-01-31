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

package ca.samanthaireland.lightning.auth.config;

import ca.samanthaireland.lightning.auth.model.GrantType;

import java.util.List;
import java.util.Set;

/**
 * Configuration for OAuth2/OIDC features.
 *
 * <p>This interface defines OAuth2-specific configuration including
 * registered service clients, token lifetimes, and supported features.
 */
public interface OAuth2Configuration {

    /**
     * Gets the access token lifetime in seconds for service tokens (client_credentials).
     *
     * <p>Service tokens should be short-lived (15-30 minutes recommended).
     *
     * @return the service token lifetime in seconds (default: 900 = 15 minutes)
     */
    default int serviceTokenLifetimeSeconds() {
        return 900;
    }

    /**
     * Gets the access token lifetime in seconds for user tokens (password grant).
     *
     * @return the user token lifetime in seconds (default: 3600 = 1 hour)
     */
    default int userTokenLifetimeSeconds() {
        return 3600;
    }

    /**
     * Gets the refresh token lifetime in seconds.
     *
     * @return the refresh token lifetime in seconds (default: 604800 = 7 days)
     */
    default int refreshTokenLifetimeSeconds() {
        return 604800;
    }

    /**
     * Gets the list of registered service clients.
     *
     * <p>Each client configuration should include:
     * <ul>
     *   <li>clientId - unique identifier</li>
     *   <li>clientSecret - for confidential clients (will be hashed)</li>
     *   <li>clientType - confidential or public</li>
     *   <li>allowedScopes - scopes this client can request</li>
     *   <li>allowedGrantTypes - grant types this client can use</li>
     *   <li>displayName - human-readable name</li>
     * </ul>
     *
     * @return list of client configurations
     */
    List<? extends ServiceClientConfig> clients();

    /**
     * Gets the supported grant types.
     *
     * @return set of supported grant types
     */
    default Set<GrantType> supportedGrantTypes() {
        return Set.of(
                GrantType.CLIENT_CREDENTIALS,
                GrantType.PASSWORD,
                GrantType.REFRESH_TOKEN,
                GrantType.TOKEN_EXCHANGE
        );
    }

    /**
     * Configuration for a single service client.
     */
    interface ServiceClientConfig {
        /**
         * Gets the client ID.
         *
         * @return the client ID (e.g., "control-plane")
         */
        String clientId();

        /**
         * Gets the client secret (plaintext, will be hashed).
         *
         * <p>Required for confidential clients, ignored for public clients.
         *
         * @return the client secret, or null for public clients
         */
        String clientSecret();

        /**
         * Gets the client type.
         *
         * @return "confidential" or "public"
         */
        default String clientType() {
            return "confidential";
        }

        /**
         * Gets the human-readable display name.
         *
         * @return the display name
         */
        String displayName();

        /**
         * Gets the allowed scopes for this client.
         *
         * @return list of allowed scopes
         */
        List<String> allowedScopes();

        /**
         * Gets the allowed grant types for this client.
         *
         * @return list of allowed grant type strings (e.g., "client_credentials", "password")
         */
        List<String> allowedGrantTypes();

        /**
         * Gets whether the client is enabled.
         *
         * @return true if enabled (default: true)
         */
        default boolean enabled() {
            return true;
        }
    }
}
