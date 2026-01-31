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

import ca.samanthaireland.stormstack.thunder.auth.model.ApiToken;
import ca.samanthaireland.stormstack.thunder.auth.model.ApiTokenId;
import ca.samanthaireland.stormstack.thunder.auth.model.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for API Token persistence.
 *
 * <p>Implementations of this interface handle the persistence of ApiToken entities
 * to various storage backends (MongoDB, in-memory, etc.).
 */
public interface ApiTokenRepository {

    /**
     * Finds an API token by its unique ID.
     *
     * @param id the token ID
     * @return the token if found
     */
    Optional<ApiToken> findById(ApiTokenId id);

    /**
     * Finds all API tokens for a user.
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
     * Returns all active (non-revoked, non-expired) API tokens.
     *
     * @return list of active tokens
     */
    List<ApiToken> findAllActive();

    /**
     * Saves an API token (insert or update).
     *
     * <p>If the token ID already exists, updates the existing token.
     * Otherwise, inserts a new token.
     *
     * @param token the token to save
     * @return the saved token
     */
    ApiToken save(ApiToken token);

    /**
     * Deletes an API token by its ID.
     *
     * <p>Note: Typically tokens should be revoked rather than deleted
     * to maintain audit history.
     *
     * @param id the token ID
     * @return true if the token was deleted
     */
    boolean deleteById(ApiTokenId id);

    /**
     * Returns the total count of API tokens.
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
