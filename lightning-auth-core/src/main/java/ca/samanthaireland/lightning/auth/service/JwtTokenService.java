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

import ca.samanthaireland.lightning.auth.model.ServiceClient;
import ca.samanthaireland.lightning.auth.model.User;

import java.util.Map;
import java.util.Set;

/**
 * Service for creating and verifying JWT tokens for OAuth2 flows.
 *
 * <p>This service handles the creation of access tokens, refresh tokens,
 * and service tokens with standardized claims per OAuth2/OIDC specs.
 */
public interface JwtTokenService {

    /**
     * JWT claim names for OAuth2 tokens.
     */
    String CLAIM_CLIENT_ID = "client_id";
    String CLAIM_TOKEN_TYPE = "token_type";
    String CLAIM_SCOPE = "scope";
    String CLAIM_JTI = "jti";

    /**
     * Token type values.
     */
    String TOKEN_TYPE_ACCESS = "access";
    String TOKEN_TYPE_REFRESH = "refresh";
    String TOKEN_TYPE_SERVICE = "service";

    /**
     * Creates a service access token for client_credentials grant.
     *
     * <p>Service tokens are short-lived and include the client_id as the subject.
     *
     * @param client    the authenticated client
     * @param scopes    the granted scopes
     * @param expiresIn lifetime in seconds
     * @return the signed JWT
     */
    String createServiceToken(ServiceClient client, Set<String> scopes, int expiresIn);

    /**
     * Creates a user access token for password grant.
     *
     * @param user      the authenticated user
     * @param client    the client that requested the token
     * @param scopes    the granted scopes
     * @param expiresIn lifetime in seconds
     * @return the signed JWT
     */
    String createUserAccessToken(User user, ServiceClient client, Set<String> scopes, int expiresIn);

    /**
     * Creates a refresh token.
     *
     * <p>Note: The refresh token is returned as a JWT but should also be
     * stored in the RefreshTokenRepository with a hashed value.
     *
     * @param user      the user
     * @param client    the client
     * @param scopes    the granted scopes
     * @param expiresIn lifetime in seconds
     * @return the signed JWT
     */
    String createRefreshToken(User user, ServiceClient client, Set<String> scopes, int expiresIn);

    /**
     * Verifies a JWT and returns its claims.
     *
     * @param token the JWT to verify
     * @return the decoded claims
     * @throws ca.samanthaireland.lightning.auth.exception.AuthException if verification fails
     */
    Map<String, Object> verifyToken(String token);

    /**
     * Gets the issuer URL configured for this service.
     *
     * @return the issuer URL
     */
    String getIssuer();
}
