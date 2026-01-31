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

package ca.samanthaireland.lightning.auth.service;

import ca.samanthaireland.lightning.auth.config.OAuth2Configuration;
import ca.samanthaireland.lightning.auth.exception.AuthException;
import ca.samanthaireland.lightning.auth.model.GrantType;
import ca.samanthaireland.lightning.auth.model.OAuth2TokenResponse;
import ca.samanthaireland.lightning.auth.model.ServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * Handles the OAuth2 Token Exchange grant (RFC 8693).
 *
 * <p>This grant is used to exchange one token type for another. In this
 * implementation, it exchanges API tokens (lat_XXXXX) for session JWTs.
 *
 * <p>Request parameters:
 * <ul>
 *   <li>grant_type=urn:ietf:params:oauth:grant-type:token-exchange (required)</li>
 *   <li>subject_token (required) - the API token to exchange</li>
 *   <li>subject_token_type (required) - must be "urn:ietf:params:oauth:token-type:api_token"</li>
 *   <li>scope (optional) - requested scopes (must be subset of API token scopes)</li>
 * </ul>
 */
public class TokenExchangeGrantHandler implements OAuth2GrantHandler {

    private static final Logger log = LoggerFactory.getLogger(TokenExchangeGrantHandler.class);

    private static final String PARAM_SUBJECT_TOKEN = "subject_token";
    private static final String PARAM_SUBJECT_TOKEN_TYPE = "subject_token_type";
    private static final String PARAM_SCOPE = "scope";

    // Custom token type for API tokens
    public static final String TOKEN_TYPE_API_TOKEN = "urn:ietf:params:oauth:token-type:api_token";

    private final ApiTokenService apiTokenService;
    private final JwtTokenService jwtTokenService;
    private final OAuth2Configuration oauth2Config;

    public TokenExchangeGrantHandler(
            ApiTokenService apiTokenService,
            JwtTokenService jwtTokenService,
            OAuth2Configuration oauth2Config) {
        this.apiTokenService = apiTokenService;
        this.jwtTokenService = jwtTokenService;
        this.oauth2Config = oauth2Config;
    }

    @Override
    public GrantType getGrantType() {
        return GrantType.TOKEN_EXCHANGE;
    }

    @Override
    public OAuth2TokenResponse handle(ServiceClient client, Map<String, String> parameters) {
        String subjectToken = parameters.get(PARAM_SUBJECT_TOKEN);
        String subjectTokenType = parameters.get(PARAM_SUBJECT_TOKEN_TYPE);

        // Validate token type
        if (!TOKEN_TYPE_API_TOKEN.equals(subjectTokenType)) {
            log.warn("Unsupported subject_token_type: {}", subjectTokenType);
            throw AuthException.invalidRequest(
                    "Unsupported subject_token_type. Expected: " + TOKEN_TYPE_API_TOKEN);
        }

        // Exchange the API token using existing service
        var exchangeResult = apiTokenService.exchangeToken(subjectToken, null);

        // Get the scopes from the exchanged token
        Set<String> tokenScopes = exchangeResult.scopes();

        // If scope parameter provided, filter to requested scopes
        String scopeParam = parameters.get(PARAM_SCOPE);
        Set<String> grantedScopes;
        if (scopeParam != null && !scopeParam.isBlank()) {
            Set<String> requestedScopes = Set.of(scopeParam.split("\\s+"));
            // Can only request scopes that are in the API token
            grantedScopes = requestedScopes.stream()
                    .filter(tokenScopes::contains)
                    .collect(java.util.stream.Collectors.toSet());

            if (grantedScopes.isEmpty() && !requestedScopes.isEmpty()) {
                throw AuthException.invalidScope("None of the requested scopes are available");
            }
        } else {
            grantedScopes = tokenScopes;
        }

        // Also filter by client allowed scopes if client is provided
        if (client != null) {
            grantedScopes = client.filterAllowedScopes(grantedScopes);
        }

        // The existing exchangeToken already returns a session JWT
        // We just need to wrap it in the OAuth2 response format
        int expiresIn = oauth2Config.userTokenLifetimeSeconds();

        log.info("Token exchange: exchanged API token for session, scopes: {}", grantedScopes);

        return OAuth2TokenResponse.forTokenExchange(
                exchangeResult.sessionToken(),
                expiresIn,
                grantedScopes
        );
    }

    @Override
    public void validateRequest(Map<String, String> parameters) {
        String subjectToken = parameters.get(PARAM_SUBJECT_TOKEN);
        String subjectTokenType = parameters.get(PARAM_SUBJECT_TOKEN_TYPE);

        if (subjectToken == null || subjectToken.isBlank()) {
            throw AuthException.invalidRequest("Missing required parameter: subject_token");
        }
        if (subjectTokenType == null || subjectTokenType.isBlank()) {
            throw AuthException.invalidRequest("Missing required parameter: subject_token_type");
        }
    }
}
