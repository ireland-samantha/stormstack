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

package ca.samanthaireland.lightning.auth.provider.config;

import ca.samanthaireland.lightning.auth.config.OAuth2Configuration;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.List;
import java.util.Optional;

/**
 * Quarkus configuration mapping for OAuth2 settings.
 *
 * <p>Example application.properties:
 * <pre>
 * auth.oauth2.service-token-lifetime-seconds=900
 * auth.oauth2.user-token-lifetime-seconds=3600
 * auth.oauth2.refresh-token-lifetime-seconds=604800
 *
 * auth.oauth2.clients[0].client-id=control-plane
 * auth.oauth2.clients[0].client-secret=secret123
 * auth.oauth2.clients[0].display-name=Lightning Control Plane
 * auth.oauth2.clients[0].allowed-scopes=service.match-token.issue,service.match-token.validate
 * auth.oauth2.clients[0].allowed-grant-types=client_credentials
 * </pre>
 */
@ConfigMapping(prefix = "auth.oauth2")
public interface QuarkusOAuth2Config extends OAuth2Configuration {

    /**
     * Service token (client_credentials) lifetime in seconds.
     */
    @WithDefault("900")
    @WithName("service-token-lifetime-seconds")
    @Override
    int serviceTokenLifetimeSeconds();

    /**
     * User token (password grant) lifetime in seconds.
     */
    @WithDefault("3600")
    @WithName("user-token-lifetime-seconds")
    @Override
    int userTokenLifetimeSeconds();

    /**
     * Refresh token lifetime in seconds.
     */
    @WithDefault("604800")
    @WithName("refresh-token-lifetime-seconds")
    @Override
    int refreshTokenLifetimeSeconds();

    /**
     * List of registered OAuth2 clients.
     *
     * <p>Note: Returns QuarkusServiceClientConfig which extends ServiceClientConfig.
     * The parent interface uses covariant return type.
     */
    List<QuarkusServiceClientConfig> clients();

    /**
     * Quarkus config mapping for a service client.
     */
    interface QuarkusServiceClientConfig extends ServiceClientConfig {

        @WithName("client-id")
        @Override
        String clientId();

        @WithName("client-secret")
        @Override
        String clientSecret();

        @WithDefault("confidential")
        @WithName("client-type")
        @Override
        String clientType();

        @WithName("display-name")
        @Override
        String displayName();

        @WithName("allowed-scopes")
        @Override
        List<String> allowedScopes();

        @WithName("allowed-grant-types")
        @Override
        List<String> allowedGrantTypes();

        @WithDefault("true")
        @Override
        boolean enabled();
    }
}
