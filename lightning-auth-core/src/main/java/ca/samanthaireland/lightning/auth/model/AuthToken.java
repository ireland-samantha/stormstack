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

package ca.samanthaireland.lightning.auth.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Represents an authenticated session token.
 *
 * <p>This record contains the claims extracted from a verified JWT token,
 * including the user identity, roles, scopes, and expiration time.
 *
 * @param userId    the authenticated user's ID
 * @param username  the authenticated user's username
 * @param roleNames the role names assigned to this user
 * @param scopes    the resolved permission scopes from all roles (direct + inherited)
 * @param expiresAt when the token expires
 * @param jwtToken  the raw JWT token string
 */
public record AuthToken(
        UserId userId,
        String username,
        Set<String> roleNames,
        Set<String> scopes,
        Instant expiresAt,
        String jwtToken
) {

    /** JWT claim name for user ID. */
    public static final String CLAIM_USER_ID = "user_id";

    /** JWT claim name for username. */
    public static final String CLAIM_USERNAME = "username";

    /** JWT claim name for roles. */
    public static final String CLAIM_ROLES = "roles";

    /** JWT claim name for scopes. */
    public static final String CLAIM_SCOPES = "scopes";

    public AuthToken {
        Objects.requireNonNull(userId, "User ID cannot be null");
        Objects.requireNonNull(username, "Username cannot be null");
        Objects.requireNonNull(roleNames, "Role names cannot be null");
        Objects.requireNonNull(scopes, "Scopes cannot be null");
        Objects.requireNonNull(expiresAt, "Expires at cannot be null");
        Objects.requireNonNull(jwtToken, "JWT token cannot be null");

        // Defensive copies
        roleNames = Set.copyOf(roleNames);
        scopes = Set.copyOf(scopes);
    }

    /**
     * Legacy constructor for backward compatibility.
     * Creates an AuthToken with empty scopes.
     */
    public AuthToken(UserId userId, String username, Set<String> roleNames, Instant expiresAt, String jwtToken) {
        this(userId, username, roleNames, Set.of(), expiresAt, jwtToken);
    }

    /**
     * Check if this token has the specified role.
     *
     * @param roleName the role name to check (case-insensitive)
     * @return true if the token has this role
     */
    public boolean hasRole(String roleName) {
        return roleNames.stream().anyMatch(r -> r.equalsIgnoreCase(roleName));
    }

    /**
     * Check if this token has the specified scope.
     *
     * @param scope the scope to check (e.g., "auth.user.read")
     * @return true if the token has this scope
     */
    public boolean hasScope(String scope) {
        if (scopes.contains(scope)) {
            return true;
        }
        // Check for wildcard scopes (e.g., "auth.user.*" matches "auth.user.read")
        String[] parts = scope.split("\\.");
        if (parts.length >= 2) {
            String wildcardScope = parts[0] + "." + parts[1] + ".*";
            if (scopes.contains(wildcardScope)) {
                return true;
            }
        }
        if (parts.length >= 1) {
            String wildcardScope = parts[0] + ".*";
            if (scopes.contains(wildcardScope)) {
                return true;
            }
        }
        return scopes.contains("*");
    }

    /**
     * Check if this token has any of the specified scopes.
     *
     * @param requiredScopes the scopes to check
     * @return true if the token has any of the scopes
     */
    public boolean hasAnyScope(String... requiredScopes) {
        for (String scope : requiredScopes) {
            if (hasScope(scope)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if this token has all of the specified scopes.
     *
     * @param requiredScopes the scopes to check
     * @return true if the token has all of the scopes
     */
    public boolean hasAllScopes(String... requiredScopes) {
        for (String scope : requiredScopes) {
            if (!hasScope(scope)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if this token has admin role.
     *
     * @return true if the token has admin role
     */
    public boolean isAdmin() {
        return hasRole("admin");
    }

    /**
     * Check if this token can submit commands.
     *
     * @return true if the token has command_submit or admin role, or engine.command.* scope
     */
    public boolean canSubmitCommands() {
        return hasRole("command_submit") || isAdmin() || hasScope("engine.command.submit");
    }

    /**
     * Check if this token can view snapshots.
     *
     * @return true if the token has view_only, command_submit, or admin role, or engine.snapshot.* scope
     */
    public boolean canViewSnapshots() {
        return hasRole("view_only") || canSubmitCommands() || hasScope("engine.snapshot.view");
    }

    /**
     * Check if this token has expired.
     *
     * @return true if the token has expired
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
