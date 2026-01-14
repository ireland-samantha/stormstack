package ca.samanthaireland.auth;

import java.time.Instant;
import java.util.Set;

/**
 * Represents a user in the REST API authentication system.
 *
 * @param id unique user identifier
 * @param username the user's login name
 * @param passwordHash BCrypt hashed password
 * @param roles the role names assigned to this user
 * @param createdAt when the user was created
 * @param enabled whether the user can log in
 */
public record User(
        long id,
        String username,
        String passwordHash,
        Set<String> roles,
        Instant createdAt,
        boolean enabled
) {
    /**
     * Check if this user has the specified role.
     *
     * @param roleName the role name to check
     * @return true if user has this role
     */
    public boolean hasRole(String roleName) {
        return roles.stream().anyMatch(r -> r.equalsIgnoreCase(roleName));
    }

    /**
     * Check if this user is an admin.
     *
     * @return true if user has admin role
     */
    public boolean isAdmin() {
        return hasRole("admin");
    }

    /**
     * Create a new User with a different password hash.
     *
     * @param newPasswordHash the new password hash
     * @return a new User instance with the updated password
     */
    public User withPasswordHash(String newPasswordHash) {
        return new User(id, username, newPasswordHash, roles, createdAt, enabled);
    }

    /**
     * Create a new User with different roles.
     *
     * @param newRoles the new role names
     * @return a new User instance with the updated roles
     */
    public User withRoles(Set<String> newRoles) {
        return new User(id, username, passwordHash, newRoles, createdAt, enabled);
    }

    /**
     * Create a new User with enabled/disabled status.
     *
     * @param newEnabled the new enabled status
     * @return a new User instance with the updated status
     */
    public User withEnabled(boolean newEnabled) {
        return new User(id, username, passwordHash, roles, createdAt, newEnabled);
    }
}
