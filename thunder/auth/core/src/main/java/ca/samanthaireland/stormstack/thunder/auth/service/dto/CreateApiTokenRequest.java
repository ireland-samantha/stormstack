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

package ca.samanthaireland.stormstack.thunder.auth.service.dto;

import ca.samanthaireland.stormstack.thunder.auth.model.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Request DTO for creating an API token.
 *
 * <p>Encapsulates all parameters needed to create an API token for programmatic
 * access to the system.
 *
 * @param userId    the user ID who will own this token (required)
 * @param name      descriptive name for the token (required)
 * @param scopes    permission scopes for this token (required)
 * @param expiresAt when the token should expire (optional, null for no expiry)
 */
public record CreateApiTokenRequest(
        UserId userId,
        String name,
        Set<String> scopes,
        Instant expiresAt
) {
    public CreateApiTokenRequest {
        Objects.requireNonNull(userId, "User ID cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(scopes, "Scopes cannot be null");

        if (name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }
        if (scopes.isEmpty()) {
            throw new IllegalArgumentException("Scopes cannot be empty");
        }
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            throw new IllegalArgumentException("Expiration must be in the future");
        }

        // Defensive copy
        scopes = Set.copyOf(scopes);
    }

    /**
     * Creates a request for a non-expiring token.
     *
     * @param userId the user ID
     * @param name   token name
     * @param scopes permission scopes
     * @return request with no expiration
     */
    public static CreateApiTokenRequest nonExpiring(UserId userId, String name, Set<String> scopes) {
        return new CreateApiTokenRequest(userId, name, scopes, null);
    }

    /**
     * Check if this token request has an expiration.
     *
     * @return true if the token will expire
     */
    public boolean hasExpiration() {
        return expiresAt != null;
    }
}
