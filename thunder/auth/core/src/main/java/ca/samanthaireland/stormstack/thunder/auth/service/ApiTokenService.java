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

import ca.samanthaireland.stormstack.thunder.auth.exception.AuthException;
import ca.samanthaireland.stormstack.thunder.auth.model.ApiToken;
import ca.samanthaireland.stormstack.thunder.auth.model.ApiTokenId;
import ca.samanthaireland.stormstack.thunder.auth.model.UserId;
import ca.samanthaireland.stormstack.thunder.auth.service.dto.CreateApiTokenRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service for API token management.
 *
 * <p>Handles creation, validation, and revocation of API tokens for
 * programmatic access to the API.
 */
public interface ApiTokenService {

    /**
     * Result of creating an API token.
     *
     * @param token          the created token metadata
     * @param plaintextToken the plaintext token value (only returned at creation)
     */
    record CreateTokenResult(ApiToken token, String plaintextToken) {}

    /**
     * Result of exchanging an API token for a session JWT.
     *
     * @param sessionToken the JWT session token
     * @param expiresAt    when the session token expires
     * @param scopes       the scopes granted to the session
     */
    record TokenExchangeResult(String sessionToken, Instant expiresAt, Set<String> scopes) {}

    /**
     * Creates a new API token.
     *
     * <p>The plaintext token is only returned once at creation time.
     * The token is stored as a BCrypt hash.
     *
     * @param request the token creation request
     * @return the created token and its plaintext value
     * @throws AuthException if the user doesn't exist
     */
    CreateTokenResult createToken(CreateApiTokenRequest request);

    /**
     * Exchanges an API token for a short-lived session JWT.
     *
     * <p>This implements the token exchange flow where a long-lived API token
     * is exchanged for a short-lived session token that can be used for
     * subsequent API calls. The session token inherits the scopes of the
     * API token.
     *
     * @param plaintextToken the plaintext API token to exchange
     * @param ipAddress      the IP address of the request (for auditing)
     * @return the exchange result containing the session JWT
     * @throws AuthException if the API token is invalid, expired, or revoked
     */
    TokenExchangeResult exchangeToken(String plaintextToken, String ipAddress);

    /**
     * Validates an API token and returns its metadata.
     *
     * @param plaintextToken the plaintext token value
     * @param ipAddress      the IP address of the request (for usage tracking)
     * @return the token if valid
     * @throws AuthException if the token is invalid, expired, or revoked
     */
    ApiToken validateToken(String plaintextToken, String ipAddress);

    /**
     * Finds a token by its ID.
     *
     * @param tokenId the token ID
     * @return the token if found
     */
    Optional<ApiToken> findById(ApiTokenId tokenId);

    /**
     * Finds all tokens for a user.
     *
     * @param userId the user ID
     * @return list of tokens owned by the user
     */
    List<ApiToken> findByUserId(UserId userId);

    /**
     * Returns all API tokens.
     *
     * @return list of all tokens
     */
    List<ApiToken> findAll();

    /**
     * Revokes an API token.
     *
     * @param tokenId the token ID
     * @return the revoked token
     * @throws AuthException if the token doesn't exist
     */
    ApiToken revokeToken(ApiTokenId tokenId);

    /**
     * Deletes an API token permanently.
     *
     * @param tokenId the token ID
     * @return true if deleted
     * @throws AuthException if the token doesn't exist
     */
    boolean deleteToken(ApiTokenId tokenId);

    /**
     * Returns the count of API tokens.
     *
     * @return the token count
     */
    long count();

    /**
     * Returns the count of active API tokens for a user.
     *
     * @param userId the user ID
     * @return the count of active tokens
     */
    long countActiveByUserId(UserId userId);
}
