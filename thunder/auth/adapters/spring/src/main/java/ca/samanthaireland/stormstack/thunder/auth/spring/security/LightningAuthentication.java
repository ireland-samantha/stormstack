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

package ca.samanthaireland.stormstack.thunder.auth.spring.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Spring Security Authentication implementation for Lightning Auth.
 *
 * <p>Contains the authenticated user's identity, scopes, and the raw JWT token.
 */
public class LightningAuthentication implements Authentication {

    private final String userId;
    private final String username;
    private final Set<String> scopes;
    private final String jwtToken;
    private final String apiTokenId;
    private final Instant expiresAt;
    private final Collection<GrantedAuthority> authorities;
    private boolean authenticated = true;

    /**
     * Creates a new Lightning authentication.
     *
     * @param userId     the user ID
     * @param username   the username
     * @param scopes     the granted scopes
     * @param jwtToken   the raw JWT token
     * @param apiTokenId the originating API token ID (if from token exchange)
     * @param expiresAt  when the authentication expires
     */
    public LightningAuthentication(
            String userId,
            String username,
            Set<String> scopes,
            String jwtToken,
            String apiTokenId,
            Instant expiresAt) {
        this.userId = Objects.requireNonNull(userId, "User ID is required");
        this.username = Objects.requireNonNull(username, "Username is required");
        this.scopes = scopes != null ? Set.copyOf(scopes) : Set.of();
        this.jwtToken = Objects.requireNonNull(jwtToken, "JWT token is required");
        this.apiTokenId = apiTokenId;
        this.expiresAt = Objects.requireNonNull(expiresAt, "Expiry time is required");
        this.authorities = this.scopes.stream()
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public Object getCredentials() {
        return jwtToken;
    }

    @Override
    public Object getDetails() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return userId;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated && !isExpired();
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        this.authenticated = isAuthenticated;
    }

    @Override
    public String getName() {
        return username;
    }

    /**
     * Returns the user ID.
     *
     * @return the user ID
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Returns the username.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the granted scopes.
     *
     * @return the scopes
     */
    public Set<String> getScopes() {
        return scopes;
    }

    /**
     * Returns the raw JWT token.
     *
     * @return the JWT token
     */
    public String getJwtToken() {
        return jwtToken;
    }

    /**
     * Returns the originating API token ID, if this authentication
     * was created via token exchange.
     *
     * @return the API token ID, or null if not from token exchange
     */
    public String getApiTokenId() {
        return apiTokenId;
    }

    /**
     * Returns when this authentication expires.
     *
     * @return the expiry time
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Checks if this authentication has expired.
     *
     * @return true if expired
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Checks if this authentication has the specified scope.
     *
     * @param scope the scope to check
     * @return true if the scope is granted
     */
    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }

    /**
     * Checks if this authentication has any of the specified scopes.
     *
     * @param requiredScopes the scopes to check
     * @return true if at least one scope is granted
     */
    public boolean hasAnyScope(String... requiredScopes) {
        for (String scope : requiredScopes) {
            if (scopes.contains(scope)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if this authentication has all of the specified scopes.
     *
     * @param requiredScopes the scopes to check
     * @return true if all scopes are granted
     */
    public boolean hasAllScopes(String... requiredScopes) {
        for (String scope : requiredScopes) {
            if (!scopes.contains(scope)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "LightningAuthentication{" +
                "userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", scopes=" + scopes +
                ", apiTokenId='" + apiTokenId + '\'' +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
