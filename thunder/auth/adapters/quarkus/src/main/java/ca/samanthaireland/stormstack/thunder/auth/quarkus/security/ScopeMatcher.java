package ca.samanthaireland.stormstack.thunder.auth.quarkus.security;

import java.util.Set;

/**
 * Utility class for matching scopes with wildcard support.
 *
 * <p>Scope format: {@code service.resource.operation}
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code engine.container.create} - Create containers in engine</li>
 *   <li>{@code engine.container.*} - All container operations in engine</li>
 *   <li>{@code engine.*} - All engine operations</li>
 *   <li>{@code *} - Full admin access (all operations)</li>
 * </ul>
 *
 * <p>Wildcard rules:
 * <ul>
 *   <li>{@code *} at any position matches all values at that level and below</li>
 *   <li>{@code *} alone grants full access to everything</li>
 *   <li>More specific scopes are checked first, but wildcards can satisfy any requirement</li>
 * </ul>
 */
public final class ScopeMatcher {

    private ScopeMatcher() {
        // Utility class
    }

    /**
     * Check if the user's scopes satisfy a required scope.
     *
     * <p>Supports wildcards: {@code *}, {@code service.*}, {@code service.resource.*}
     *
     * @param userScopes    the set of scopes the user has
     * @param requiredScope the scope required for access
     * @return true if the user has sufficient scopes
     */
    public static boolean matches(Set<String> userScopes, String requiredScope) {
        if (userScopes == null || userScopes.isEmpty() || requiredScope == null || requiredScope.isBlank()) {
            return false;
        }

        // Full wildcard grants everything
        if (userScopes.contains("*")) {
            return true;
        }

        // Exact match
        if (userScopes.contains(requiredScope)) {
            return true;
        }

        // Wildcard matching: engine.container.* matches engine.container.create
        String[] requiredParts = requiredScope.split("\\.");
        for (String userScope : userScopes) {
            if (matchesWithWildcard(userScope, requiredParts)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if any of the user's scopes satisfy any of the required scopes (OR logic).
     *
     * @param userScopes     the set of scopes the user has
     * @param requiredScopes the scopes required for access (any one is sufficient)
     * @return true if the user has at least one sufficient scope
     */
    public static boolean matchesAny(Set<String> userScopes, Set<String> requiredScopes) {
        if (requiredScopes == null || requiredScopes.isEmpty()) {
            return true;
        }

        for (String requiredScope : requiredScopes) {
            if (matches(userScopes, requiredScope)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if the user's scopes satisfy all required scopes (AND logic).
     *
     * @param userScopes     the set of scopes the user has
     * @param requiredScopes the scopes required for access (all must be satisfied)
     * @return true if the user has all sufficient scopes
     */
    public static boolean matchesAll(Set<String> userScopes, Set<String> requiredScopes) {
        if (requiredScopes == null || requiredScopes.isEmpty()) {
            return true;
        }

        for (String requiredScope : requiredScopes) {
            if (!matches(userScopes, requiredScope)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if a single user scope (potentially with wildcards) matches required scope parts.
     *
     * <p>The matching logic:
     * <ul>
     *   <li>If a user scope part is "*", it matches the rest of the required scope</li>
     *   <li>Each non-wildcard part must match exactly</li>
     *   <li>User scope must be at least as long as required scope (unless wildcard terminates early)</li>
     * </ul>
     *
     * @param userScope     a single user scope (may contain wildcards)
     * @param requiredParts the required scope split by "."
     * @return true if the user scope satisfies the required scope
     */
    private static boolean matchesWithWildcard(String userScope, String[] requiredParts) {
        if (userScope == null || userScope.isBlank()) {
            return false;
        }

        String[] userParts = userScope.split("\\.");

        for (int i = 0; i < userParts.length; i++) {
            // Wildcard at this position matches everything from here on
            if ("*".equals(userParts[i])) {
                return true;
            }

            // If we've run out of required parts but still have user parts, no match
            if (i >= requiredParts.length) {
                return false;
            }

            // Parts must match exactly
            if (!userParts[i].equals(requiredParts[i])) {
                return false;
            }
        }

        // All user parts matched; check if we covered all required parts
        return userParts.length == requiredParts.length;
    }
}
