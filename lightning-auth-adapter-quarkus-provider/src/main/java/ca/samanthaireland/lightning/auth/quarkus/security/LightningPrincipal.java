package ca.samanthaireland.lightning.auth.quarkus.security;

import java.security.Principal;
import java.util.Set;

/**
 * Principal representing an authenticated Lightning Auth user.
 *
 * <p>Supports wildcard scope matching with the unified scope format:
 * {@code service.resource.operation}
 *
 * <p>Wildcard examples:
 * <ul>
 *   <li>{@code *} - Full admin access</li>
 *   <li>{@code engine.*} - All engine operations</li>
 *   <li>{@code engine.container.*} - All container operations</li>
 * </ul>
 */
public class LightningPrincipal implements Principal {

    private final String userId;
    private final String username;
    private final Set<String> scopes;
    private final String apiTokenId;

    public LightningPrincipal(String userId, String username, Set<String> scopes, String apiTokenId) {
        this.userId = userId;
        this.username = username;
        this.scopes = scopes != null ? Set.copyOf(scopes) : Set.of();
        this.apiTokenId = apiTokenId;
    }

    @Override
    public String getName() {
        return username;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public Set<String> getScopes() {
        return scopes;
    }

    public String getApiTokenId() {
        return apiTokenId;
    }

    /**
     * Check if the principal has a specific scope.
     *
     * <p>Supports wildcard matching. A user with {@code engine.*} scope
     * will match a required scope of {@code engine.container.create}.
     *
     * @param scope the scope to check
     * @return true if the principal has the scope (including via wildcards)
     */
    public boolean hasScope(String scope) {
        return ScopeMatcher.matches(scopes, scope);
    }

    /**
     * Check if the principal has all specified scopes.
     *
     * <p>Supports wildcard matching. A user with {@code *} scope
     * will satisfy any set of required scopes.
     *
     * @param requiredScopes the scopes to check
     * @return true if the principal has all scopes (including via wildcards)
     */
    public boolean hasAllScopes(Set<String> requiredScopes) {
        return ScopeMatcher.matchesAll(scopes, requiredScopes);
    }

    /**
     * Check if the principal has any of the specified scopes.
     *
     * <p>Supports wildcard matching. A user with {@code engine.*} scope
     * will match if any required scope starts with {@code engine.}.
     *
     * @param requiredScopes the scopes to check
     * @return true if the principal has at least one scope (including via wildcards)
     */
    public boolean hasAnyScope(Set<String> requiredScopes) {
        return ScopeMatcher.matchesAny(scopes, requiredScopes);
    }

    /**
     * Check if the principal has exact scope (no wildcard matching).
     *
     * <p>Use this when you need to check for an exact scope without
     * wildcard expansion.
     *
     * @param scope the exact scope to check
     * @return true if the principal has exactly this scope
     */
    public boolean hasExactScope(String scope) {
        return scopes.contains(scope);
    }

    @Override
    public String toString() {
        return "LightningPrincipal{" +
                "userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", scopes=" + scopes +
                '}';
    }
}
