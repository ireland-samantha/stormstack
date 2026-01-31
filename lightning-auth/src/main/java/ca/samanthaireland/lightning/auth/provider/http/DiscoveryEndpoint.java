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

package ca.samanthaireland.lightning.auth.provider.http;

import ca.samanthaireland.lightning.auth.config.AuthConfiguration;
import ca.samanthaireland.lightning.auth.config.OAuth2Configuration;
import ca.samanthaireland.lightning.auth.model.GrantType;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenID Connect Discovery Endpoint (RFC 8414).
 *
 * <p>Provides the OAuth 2.0 Authorization Server Metadata document
 * at the well-known URL.
 */
@Path("/.well-known")
@Produces(MediaType.APPLICATION_JSON)
public class DiscoveryEndpoint {

    @Inject
    AuthConfiguration authConfig;

    @Inject
    OAuth2Configuration oauth2Config;

    /**
     * OpenID Connect Discovery document.
     *
     * @param uriInfo the URI info for building URLs
     * @return the discovery document
     */
    @GET
    @Path("/openid-configuration")
    public Map<String, Object> getOpenIdConfiguration(@Context UriInfo uriInfo) {
        String baseUrl = buildBaseUrl(uriInfo);
        String issuer = authConfig.jwtIssuer();

        Map<String, Object> config = new LinkedHashMap<>();

        // Required fields
        config.put("issuer", issuer);
        config.put("token_endpoint", baseUrl + "/oauth2/token");
        config.put("jwks_uri", baseUrl + "/.well-known/jwks.json");

        // Supported grant types
        List<String> grantTypes = oauth2Config.supportedGrantTypes().stream()
                .map(GrantType::getValue)
                .toList();
        config.put("grant_types_supported", grantTypes);

        // Response types (we only support token endpoint, no authorization endpoint)
        config.put("response_types_supported", List.of("token"));

        // Token endpoint auth methods
        config.put("token_endpoint_auth_methods_supported", List.of(
                "client_secret_basic",
                "client_secret_post"
        ));

        // Supported scopes
        config.put("scopes_supported", List.of(
                // Service scopes
                "service.match-token.issue",
                "service.match-token.validate",
                "service.match-token.revoke",
                "service.token.read",
                "service.token.create",
                // Admin scopes
                "auth.token.read",
                "auth.token.create",
                "auth.token.revoke",
                "auth.match-token.read",
                "auth.match-token.issue",
                "auth.match-token.revoke",
                // Match/player scopes
                "match.command.send",
                "match.snapshot.read"
        ));

        // Subject types
        config.put("subject_types_supported", List.of("public"));

        // Signing algorithms
        config.put("id_token_signing_alg_values_supported", List.of("RS256", "HS256"));
        config.put("token_endpoint_auth_signing_alg_values_supported", List.of("RS256", "HS256"));

        // Additional endpoints
        config.put("userinfo_endpoint", baseUrl + "/oauth2/userinfo");
        config.put("revocation_endpoint", baseUrl + "/oauth2/revoke");

        // Service documentation
        config.put("service_documentation", "https://github.com/samanthaireland/lightning-engine");

        return config;
    }

    /**
     * OAuth 2.0 Authorization Server Metadata (RFC 8414).
     *
     * <p>Alias for the OpenID Connect discovery endpoint.
     *
     * @param uriInfo the URI info
     * @return the metadata document
     */
    @GET
    @Path("/oauth-authorization-server")
    public Map<String, Object> getOAuthMetadata(@Context UriInfo uriInfo) {
        return getOpenIdConfiguration(uriInfo);
    }

    private String buildBaseUrl(UriInfo uriInfo) {
        // Build base URL from request
        return uriInfo.getBaseUri().toString().replaceAll("/$", "");
    }
}
