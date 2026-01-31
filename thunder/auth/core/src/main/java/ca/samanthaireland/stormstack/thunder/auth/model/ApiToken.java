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

package ca.samanthaireland.stormstack.thunder.auth.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Represents an API token for programmatic access.
 *
 * <p>API tokens are long-lived tokens that can be used for service-to-service
 * communication or programmatic access to the API. The actual token value is
 * only returned once at creation time; only the hash is stored.
 *
 * @param id          unique token identifier
 * @param userId      the user who owns this token
 * @param name        human-readable name for the token
 * @param tokenHash   BCrypt hash of the token value
 * @param scopes      permission scopes granted to this token
 * @param createdAt   when the token was created
 * @param expiresAt   when the token expires (null for never)
 * @param revokedAt   when the token was revoked (null if active)
 * @param lastUsedAt  when the token was last used (null if never)
 * @param lastUsedIp  IP address of the last use (null if never)
 */
public record ApiToken(
        ApiTokenId id,
        UserId userId,
        String name,
        String tokenHash,
        Set<String> scopes,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        Instant lastUsedAt,
        String lastUsedIp
) {

    public ApiToken {
        Objects.requireNonNull(id, "ApiToken id cannot be null");
        Objects.requireNonNull(userId, "User id cannot be null");
        Objects.requireNonNull(name, "Token name cannot be null");
        Objects.requireNonNull(tokenHash, "Token hash cannot be null");
        Objects.requireNonNull(scopes, "Scopes cannot be null");
        Objects.requireNonNull(createdAt, "Created at cannot be null");

        if (name.isBlank()) {
            throw new IllegalArgumentException("Token name cannot be blank");
        }
        if (tokenHash.isBlank()) {
            throw new IllegalArgumentException("Token hash cannot be blank");
        }

        // Defensive copy
        scopes = Set.copyOf(scopes);
    }

    /**
     * Creates a new API token.
     *
     * @param userId    the user who owns this token
     * @param name      the token name
     * @param tokenHash the BCrypt hash of the token value
     * @param scopes    the permission scopes
     * @param expiresAt when the token expires (null for never)
     * @return a new ApiToken
     */
    public static ApiToken create(UserId userId, String name, String tokenHash, Set<String> scopes, Instant expiresAt) {
        return new ApiToken(
                ApiTokenId.generate(),
                userId,
                name,
                tokenHash,
                scopes,
                Instant.now(),
                expiresAt,
                null,
                null,
                null
        );
    }

    /**
     * Checks if this token is currently active (not expired or revoked).
     *
     * @return true if the token is active
     */
    public boolean isActive() {
        if (revokedAt != null) {
            return false;
        }
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
            return false;
        }
        return true;
    }

    /**
     * Checks if this token is expired.
     *
     * @return true if the token has expired
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Checks if this token has been revoked.
     *
     * @return true if the token has been revoked
     */
    public boolean isRevoked() {
        return revokedAt != null;
    }

    /**
     * Checks if this token has the specified scope.
     *
     * @param scope the scope to check
     * @return true if the token has this scope
     */
    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }

    /**
     * Creates a revoked version of this token.
     *
     * @return a new ApiToken with revokedAt set to now
     */
    public ApiToken revoke() {
        return new ApiToken(id, userId, name, tokenHash, scopes, createdAt, expiresAt, Instant.now(), lastUsedAt, lastUsedIp);
    }

    /**
     * Records usage of this token.
     *
     * @param ipAddress the IP address of the request
     * @return a new ApiToken with updated usage info
     */
    public ApiToken recordUsage(String ipAddress) {
        return new ApiToken(id, userId, name, tokenHash, scopes, createdAt, expiresAt, revokedAt, Instant.now(), ipAddress);
    }
}
