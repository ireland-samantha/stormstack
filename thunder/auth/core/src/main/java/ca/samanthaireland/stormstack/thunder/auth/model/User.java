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
 * Represents a user in the authentication system.
 *
 * @param id           unique user identifier
 * @param username     the user's login name (unique, case-insensitive)
 * @param passwordHash BCrypt hashed password
 * @param roleIds      the role IDs assigned to this user
 * @param scopes       direct permission scopes assigned to this user (format: service.resource.operation)
 * @param createdAt    when the user was created
 * @param enabled      whether the user can log in
 */
public record User(
        UserId id,
        String username,
        String passwordHash,
        Set<RoleId> roleIds,
        Set<String> scopes,
        Instant createdAt,
        boolean enabled
) {

    public User {
        Objects.requireNonNull(id, "User id cannot be null");
        Objects.requireNonNull(username, "Username cannot be null");
        Objects.requireNonNull(passwordHash, "Password hash cannot be null");
        Objects.requireNonNull(roleIds, "Role IDs cannot be null");
        Objects.requireNonNull(createdAt, "Created at cannot be null");

        if (username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be blank");
        }
        if (passwordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash cannot be blank");
        }

        // Defensive copies
        roleIds = Set.copyOf(roleIds);
        scopes = scopes != null ? Set.copyOf(scopes) : Set.of();
    }

    /**
     * Creates a new user with the given details and empty scopes.
     *
     * @param username     the username
     * @param passwordHash the BCrypt password hash
     * @param roleIds      the role IDs
     * @return a new User with a generated ID and current timestamp
     */
    public static User create(String username, String passwordHash, Set<RoleId> roleIds) {
        return new User(
                UserId.generate(),
                username,
                passwordHash,
                roleIds,
                Set.of(),
                Instant.now(),
                true
        );
    }

    /**
     * Creates a new user with the given details including scopes.
     *
     * @param username     the username
     * @param passwordHash the BCrypt password hash
     * @param roleIds      the role IDs
     * @param scopes       the permission scopes (format: service.resource.operation)
     * @return a new User with a generated ID and current timestamp
     */
    public static User create(String username, String passwordHash, Set<RoleId> roleIds, Set<String> scopes) {
        return new User(
                UserId.generate(),
                username,
                passwordHash,
                roleIds,
                scopes,
                Instant.now(),
                true
        );
    }

    /**
     * Check if this user has the specified role ID.
     *
     * @param roleId the role ID to check
     * @return true if user has this role
     */
    public boolean hasRole(RoleId roleId) {
        return roleIds.contains(roleId);
    }

    /**
     * Check if this user has a specific scope.
     *
     * <p>Note: This only checks direct scopes, not role-inherited scopes or wildcards.
     * For wildcard-aware matching, use the authentication layer.
     *
     * @param scope the scope to check
     * @return true if user has this exact scope
     */
    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }

    /**
     * Create a new User with a different password hash.
     *
     * @param newPasswordHash the new password hash
     * @return a new User instance with the updated password
     */
    public User withPasswordHash(String newPasswordHash) {
        return new User(id, username, newPasswordHash, roleIds, scopes, createdAt, enabled);
    }

    /**
     * Create a new User with different role IDs.
     *
     * @param newRoleIds the new role IDs
     * @return a new User instance with the updated roles
     */
    public User withRoleIds(Set<RoleId> newRoleIds) {
        return new User(id, username, passwordHash, newRoleIds, scopes, createdAt, enabled);
    }

    /**
     * Create a new User with different scopes.
     *
     * @param newScopes the new scopes (format: service.resource.operation)
     * @return a new User instance with the updated scopes
     */
    public User withScopes(Set<String> newScopes) {
        return new User(id, username, passwordHash, roleIds, newScopes, createdAt, enabled);
    }

    /**
     * Create a new User with enabled/disabled status.
     *
     * @param newEnabled the new enabled status
     * @return a new User instance with the updated status
     */
    public User withEnabled(boolean newEnabled) {
        return new User(id, username, passwordHash, roleIds, scopes, createdAt, newEnabled);
    }
}
