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

package ca.samanthaireland.stormstack.thunder.auth.spring.filter;

import ca.samanthaireland.stormstack.thunder.auth.spring.exception.LightningAuthException;
import ca.samanthaireland.stormstack.thunder.auth.spring.security.LightningAuthentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Filter that validates JWT tokens and establishes Spring Security context.
 *
 * <p>This filter extracts the Bearer token from the Authorization header,
 * validates it using Spring Security's JwtDecoder, and creates a
 * {@link LightningAuthentication} with the user's identity and scopes.
 *
 * <p>Filter order: {@code Ordered.HIGHEST_PRECEDENCE + 200} (runs after
 * API token exchange filter).
 */
public class JwtAuthorizationFilter extends OncePerRequestFilter implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthorizationFilter.class);

    /** Bearer token prefix. */
    private static final String BEARER_PREFIX = "Bearer ";

    /** Filter order - runs after API token exchange filter. */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 200;

    /** JWT claim for user ID. */
    private static final String CLAIM_USER_ID = "user_id";

    /** JWT claim for username. */
    private static final String CLAIM_USERNAME = "username";

    /** JWT claim for scopes (as array or space-delimited string). */
    private static final String CLAIM_SCOPES = "scopes";

    /** JWT claim for roles (fallback). */
    private static final String CLAIM_ROLES = "roles";

    /** JWT claim for API token ID. */
    private static final String CLAIM_API_TOKEN_ID = "api_token_id";

    private final JwtDecoder jwtDecoder;

    /**
     * Creates a new JWT authorization filter.
     *
     * @param jwtDecoder the JWT decoder for token validation
     */
    public JwtAuthorizationFilter(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        // If no Authorization header or not Bearer token, pass through
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwtToken = authHeader.substring(BEARER_PREFIX.length());

        try {
            Jwt jwt = jwtDecoder.decode(jwtToken);
            LightningAuthentication authentication = createAuthentication(jwt, jwtToken);

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Authenticated user: {} with scopes: {}",
                    authentication.getUsername(), authentication.getScopes());

            filterChain.doFilter(request, response);

        } catch (JwtException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            handleJwtException(e, response);
        } finally {
            // Clear security context after request
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Creates a LightningAuthentication from a validated JWT.
     */
    private LightningAuthentication createAuthentication(Jwt jwt, String jwtToken) {
        String userId = extractClaim(jwt, CLAIM_USER_ID, jwt.getSubject());
        String username = extractClaim(jwt, CLAIM_USERNAME, userId);
        Set<String> scopes = extractScopes(jwt);
        String apiTokenId = jwt.getClaimAsString(CLAIM_API_TOKEN_ID);
        Instant expiresAt = jwt.getExpiresAt();

        if (expiresAt == null) {
            throw LightningAuthException.invalidJwt("Missing expiry claim");
        }

        if (Instant.now().isAfter(expiresAt)) {
            throw LightningAuthException.jwtExpired();
        }

        return new LightningAuthentication(
                userId,
                username,
                scopes,
                jwtToken,
                apiTokenId,
                expiresAt
        );
    }

    /**
     * Extracts a string claim from the JWT with a fallback value.
     */
    private String extractClaim(Jwt jwt, String claimName, String fallback) {
        String value = jwt.getClaimAsString(claimName);
        return value != null ? value : fallback;
    }

    /**
     * Extracts scopes from the JWT.
     * Supports both array format and space-delimited string format.
     */
    @SuppressWarnings("unchecked")
    private Set<String> extractScopes(Jwt jwt) {
        Set<String> scopes = new HashSet<>();

        // Try scopes claim first
        Object scopesClaim = jwt.getClaim(CLAIM_SCOPES);
        if (scopesClaim != null) {
            addScopesFromClaim(scopes, scopesClaim);
        }

        // Also check roles claim as fallback
        Object rolesClaim = jwt.getClaim(CLAIM_ROLES);
        if (rolesClaim != null) {
            addScopesFromClaim(scopes, rolesClaim);
        }

        // Also check standard "scope" claim (OAuth2 convention)
        Object scopeClaim = jwt.getClaim("scope");
        if (scopeClaim != null) {
            addScopesFromClaim(scopes, scopeClaim);
        }

        return scopes;
    }

    /**
     * Adds scopes from a claim value, handling both array and string formats.
     */
    @SuppressWarnings("unchecked")
    private void addScopesFromClaim(Set<String> scopes, Object claim) {
        if (claim instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String s) {
                    scopes.add(s);
                }
            }
        } else if (claim instanceof String s) {
            // Space-delimited string (OAuth2 convention)
            for (String scope : s.split("\\s+")) {
                if (!scope.isBlank()) {
                    scopes.add(scope);
                }
            }
        }
    }

    /**
     * Handles JWT validation exceptions by setting appropriate error response.
     */
    private void handleJwtException(JwtException e, HttpServletResponse response)
            throws IOException {
        String message = e.getMessage();
        HttpStatus status;

        if (message != null && message.toLowerCase().contains("expired")) {
            status = HttpStatus.UNAUTHORIZED;
        } else {
            status = HttpStatus.UNAUTHORIZED;
        }

        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"error\":\"INVALID_JWT\",\"message\":\"%s\"}",
                message != null ? message.replace("\"", "\\\"") : "Invalid JWT"
        ));
    }
}
