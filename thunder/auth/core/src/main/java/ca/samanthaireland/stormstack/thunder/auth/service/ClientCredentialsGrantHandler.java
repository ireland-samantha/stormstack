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

package ca.samanthaireland.stormstack.thunder.auth.service;

import ca.samanthaireland.stormstack.thunder.auth.config.OAuth2Configuration;
import ca.samanthaireland.stormstack.thunder.auth.exception.AuthException;
import ca.samanthaireland.stormstack.thunder.auth.model.GrantType;
import ca.samanthaireland.stormstack.thunder.auth.model.OAuth2TokenResponse;
import ca.samanthaireland.stormstack.thunder.auth.model.ServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles the OAuth2 Client Credentials grant (RFC 6749 Section 4.4).
 *
 * <p>This grant is used for service-to-service authentication where
 * the client is acting on its own behalf.
 *
 * <p>Request parameters:
 * <ul>
 *   <li>grant_type=client_credentials (required)</li>
 *   <li>scope (optional) - space-delimited list of requested scopes</li>
 * </ul>
 *
 * <p>The client must authenticate using client_id and client_secret.
 */
public class ClientCredentialsGrantHandler implements OAuth2GrantHandler {

    private static final Logger log = LoggerFactory.getLogger(ClientCredentialsGrantHandler.class);
    private static final String PARAM_SCOPE = "scope";

    private final JwtTokenService jwtTokenService;
    private final OAuth2Configuration oauth2Config;

    public ClientCredentialsGrantHandler(
            JwtTokenService jwtTokenService,
            OAuth2Configuration oauth2Config) {
        this.jwtTokenService = jwtTokenService;
        this.oauth2Config = oauth2Config;
    }

    @Override
    public GrantType getGrantType() {
        return GrantType.CLIENT_CREDENTIALS;
    }

    @Override
    public OAuth2TokenResponse handle(ServiceClient client, Map<String, String> parameters) {
        if (client == null) {
            log.error("client_credentials grant requires authenticated client");
            throw AuthException.invalidClient("Client authentication required");
        }

        // Parse requested scopes
        Set<String> requestedScopes = parseScopes(parameters.get(PARAM_SCOPE));

        // If no scopes requested, use all allowed scopes for this client
        Set<String> grantedScopes;
        if (requestedScopes.isEmpty()) {
            grantedScopes = client.allowedScopes();
            log.debug("No scopes requested, using all allowed scopes for client: {}",
                    client.clientId());
        } else {
            // Filter to allowed scopes
            grantedScopes = client.filterAllowedScopes(requestedScopes);

            // Check if any requested scopes were denied
            if (grantedScopes.size() < requestedScopes.size()) {
                Set<String> deniedScopes = requestedScopes.stream()
                        .filter(s -> !grantedScopes.contains(s))
                        .collect(Collectors.toSet());
                log.warn("Client {} requested unauthorized scopes: {}",
                        client.clientId(), deniedScopes);

                // Per OAuth2 spec, we can either reduce scope or reject
                // We'll reject if any requested scope is not allowed
                if (!deniedScopes.isEmpty()) {
                    throw AuthException.invalidScope(String.join(" ", deniedScopes));
                }
            }
        }

        // Get token lifetime
        int expiresIn = oauth2Config.serviceTokenLifetimeSeconds();

        // Create the service token
        String accessToken = jwtTokenService.createServiceToken(client, grantedScopes, expiresIn);

        log.info("Issued service token for client: {}, scopes: {}",
                client.clientId(), grantedScopes);

        return OAuth2TokenResponse.forClientCredentials(accessToken, expiresIn, grantedScopes);
    }

    @Override
    public void validateRequest(Map<String, String> parameters) {
        // client_credentials has no additional required parameters beyond grant_type
        // Scope is optional
    }

    private Set<String> parseScopes(String scopeParam) {
        if (scopeParam == null || scopeParam.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(scopeParam.split("\\s+"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }
}
