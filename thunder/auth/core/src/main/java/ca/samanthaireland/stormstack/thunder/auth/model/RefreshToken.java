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
import java.util.UUID;

/**
 * Represents an OAuth2 refresh token.
 *
 * <p>Refresh tokens are long-lived tokens that can be exchanged for new
 * access tokens. They are stored with a hash of the token value for security.
 *
 * @param id          unique identifier for the refresh token
 * @param tokenHash   BCrypt hash of the token value
 * @param userId      the user this token belongs to
 * @param clientId    the client that requested this token
 * @param scopes      the scopes associated with this token
 * @param createdAt   when the token was created
 * @param expiresAt   when the token expires
 * @param revokedAt   when the token was revoked (null if active)
 * @param lastUsedAt  when the token was last used
 */
public record RefreshToken(
        RefreshTokenId id,
        String tokenHash,
        UserId userId,
        ServiceClientId clientId,
        Set<String> scopes,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        Instant lastUsedAt
) {

    public RefreshToken {
        Objects.requireNonNull(id, "Refresh token ID cannot be null");
        Objects.requireNonNull(tokenHash, "Token hash cannot be null");
        Objects.requireNonNull(userId, "User ID cannot be null");
        Objects.requireNonNull(clientId, "Client ID cannot be null");
        Objects.requireNonNull(scopes, "Scopes cannot be null");
        Objects.requireNonNull(createdAt, "Created at cannot be null");
        Objects.requireNonNull(expiresAt, "Expires at cannot be null");

        if (tokenHash.isBlank()) {
            throw new IllegalArgumentException("Token hash cannot be blank");
        }

        // Defensive copy
        scopes = Set.copyOf(scopes);
    }

    /**
     * Creates a new refresh token.
     *
     * @param tokenHash BCrypt hash of the token value
     * @param userId    the user ID
     * @param clientId  the client ID
     * @param scopes    the granted scopes
     * @param expiresAt when the token expires
     * @return the new RefreshToken
     */
    public static RefreshToken create(
            String tokenHash,
            UserId userId,
            ServiceClientId clientId,
            Set<String> scopes,
            Instant expiresAt) {
        return new RefreshToken(
                RefreshTokenId.generate(),
                tokenHash,
                userId,
                clientId,
                scopes,
                Instant.now(),
                expiresAt,
                null,
                null
        );
    }

    /**
     * Checks if this token is currently valid (not expired and not revoked).
     *
     * @return true if the token is valid
     */
    public boolean isValid() {
        return !isExpired() && !isRevoked();
    }

    /**
     * Checks if this token has expired.
     *
     * @return true if expired
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Checks if this token has been revoked.
     *
     * @return true if revoked
     */
    public boolean isRevoked() {
        return revokedAt != null;
    }

    /**
     * Creates a copy of this token marked as revoked.
     *
     * @return the revoked token
     */
    public RefreshToken revoke() {
        return new RefreshToken(
                id, tokenHash, userId, clientId, scopes,
                createdAt, expiresAt, Instant.now(), lastUsedAt
        );
    }

    /**
     * Creates a copy of this token with updated last used timestamp.
     *
     * @return the updated token
     */
    public RefreshToken recordUsage() {
        return new RefreshToken(
                id, tokenHash, userId, clientId, scopes,
                createdAt, expiresAt, revokedAt, Instant.now()
        );
    }

    /**
     * Strongly-typed identifier for a RefreshToken.
     *
     * @param value the UUID value
     */
    public record RefreshTokenId(UUID value) {

        public RefreshTokenId {
            Objects.requireNonNull(value, "RefreshTokenId value cannot be null");
        }

        public static RefreshTokenId generate() {
            return new RefreshTokenId(UUID.randomUUID());
        }

        public static RefreshTokenId fromString(String value) {
            return new RefreshTokenId(UUID.fromString(value));
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }
}
