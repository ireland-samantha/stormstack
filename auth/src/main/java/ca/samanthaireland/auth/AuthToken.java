package ca.samanthaireland.auth;

import java.time.Instant;
import java.util.Set;

/**
 * Represents an authenticated REST API user session.
 *
 * <p>This record contains claims extracted from a verified JWT token.
 *
 * @param userId the authenticated user's ID
 * @param username the authenticated username
 * @param roles the role names assigned to this user
 * @param expiresAt when the token expires
 * @param jwtToken the raw JWT token string
 */
public record AuthToken(
        long userId,
        String username,
        Set<String> roles,
        Instant expiresAt,
        String jwtToken
) {
    // JWT claim names
    public static final String CLAIM_USER_ID = "user_id";
    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_ROLES = "roles";

    /**
     * Check if this token grants the specified role.
     *
     * @param roleName the role name to check
     * @return true if this token includes the role
     */
    public boolean hasRole(String roleName) {
        return roles.stream().anyMatch(r -> r.equalsIgnoreCase(roleName));
    }

    /**
     * Check if this token has admin privileges.
     *
     * @return true if user has admin role
     */
    public boolean isAdmin() {
        return hasRole("admin");
    }

    /**
     * Check if this token can submit commands.
     *
     * @return true if user has command_manager or admin role
     */
    public boolean canSubmitCommands() {
        return hasRole("command_manager") || hasRole("admin");
    }

    /**
     * Check if this token can view snapshots.
     *
     * @return true if user has view_only, command_manager, or admin role
     */
    public boolean canViewSnapshots() {
        return hasRole("view_only") || hasRole("command_manager") || hasRole("admin");
    }

    /**
     * Check if the token has expired.
     *
     * @return true if the token has expired
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
