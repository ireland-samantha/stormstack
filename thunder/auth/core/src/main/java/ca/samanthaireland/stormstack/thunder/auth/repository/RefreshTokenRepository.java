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

package ca.samanthaireland.stormstack.thunder.auth.repository;

import ca.samanthaireland.stormstack.thunder.auth.model.RefreshToken;
import ca.samanthaireland.stormstack.thunder.auth.model.RefreshToken.RefreshTokenId;
import ca.samanthaireland.stormstack.thunder.auth.model.ServiceClientId;
import ca.samanthaireland.stormstack.thunder.auth.model.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for OAuth2 refresh token persistence.
 */
public interface RefreshTokenRepository {

    /**
     * Finds a refresh token by its ID.
     *
     * @param id the token ID
     * @return the token if found
     */
    Optional<RefreshToken> findById(RefreshTokenId id);

    /**
     * Returns all refresh tokens for a user.
     *
     * @param userId the user ID
     * @return list of tokens
     */
    List<RefreshToken> findByUserId(UserId userId);

    /**
     * Returns all active (non-revoked, non-expired) refresh tokens for a user.
     *
     * @param userId the user ID
     * @return list of active tokens
     */
    List<RefreshToken> findActiveByUserId(UserId userId);

    /**
     * Returns all refresh tokens for a user and client combination.
     *
     * @param userId   the user ID
     * @param clientId the client ID
     * @return list of tokens
     */
    List<RefreshToken> findByUserIdAndClientId(UserId userId, ServiceClientId clientId);

    /**
     * Saves a refresh token (insert or update).
     *
     * @param token the token to save
     * @return the saved token
     */
    RefreshToken save(RefreshToken token);

    /**
     * Deletes a refresh token by its ID.
     *
     * @param id the token ID
     * @return true if deleted
     */
    boolean deleteById(RefreshTokenId id);

    /**
     * Revokes all refresh tokens for a user.
     *
     * @param userId the user ID
     * @return the number of tokens revoked
     */
    int revokeAllByUserId(UserId userId);

    /**
     * Revokes all refresh tokens for a user and client combination.
     *
     * @param userId   the user ID
     * @param clientId the client ID
     * @return the number of tokens revoked
     */
    int revokeAllByUserIdAndClientId(UserId userId, ServiceClientId clientId);

    /**
     * Deletes expired tokens.
     *
     * @return the number of tokens deleted
     */
    int deleteExpired();
}
