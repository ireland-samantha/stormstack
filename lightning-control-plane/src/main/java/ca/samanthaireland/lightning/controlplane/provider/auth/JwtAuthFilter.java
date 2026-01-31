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

package ca.samanthaireland.lightning.controlplane.provider.auth;

import ca.samanthaireland.lightning.controlplane.provider.config.JwtAuthConfig;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JWT authentication filter for control plane admin endpoints.
 *
 * <p>This filter validates JWT tokens for endpoints annotated with {@link JwtProtected}.
 * It supports both local JWT validation (SmallRye JWT) and remote validation via
 * the lightning-auth service.
 *
 * <p>Node registration/heartbeat endpoints continue to use X-Control-Plane-Token
 * authentication and are NOT affected by this filter.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
@ApplicationScoped
public class JwtAuthFilter implements ContainerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String API_TOKEN_HEADER = "X-Api-Token";
    private static final String BEARER_PREFIX = "Bearer ";

    @Inject
    JwtAuthConfig config;

    @Inject
    AuthServiceClient authServiceClient;

    @Inject
    JWTParser jwtParser;

    @Context
    ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        // Only filter endpoints marked with @JwtProtected
        JwtProtected annotation = getJwtProtectedAnnotation();
        if (annotation == null) {
            return;
        }

        // Skip if JWT auth is disabled
        if (!config.enabled()) {
            log.debug("JWT auth disabled, skipping filter");
            return;
        }

        // Extract token - X-Api-Token takes priority over Authorization header
        String token = null;
        String apiTokenHeader = requestContext.getHeaderString(API_TOKEN_HEADER);
        if (apiTokenHeader != null && !apiTokenHeader.isEmpty()) {
            // API token in X-Api-Token header - must be validated via auth service
            token = apiTokenHeader;
            log.debug("Using token from X-Api-Token header");
        } else {
            // Fall back to Authorization: Bearer header
            String authHeader = requestContext.getHeaderString(AUTHORIZATION_HEADER);
            if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                token = authHeader.substring(BEARER_PREFIX.length());
                log.debug("Using token from Authorization header");
            }
        }

        if (token == null || token.isEmpty()) {
            abortWithUnauthorized(requestContext, "Missing authentication token");
            return;
        }

        // Validate token
        AuthenticationResult authResult;
        if (authServiceClient.isRemoteValidationEnabled()) {
            authResult = validateRemotely(token);
        } else {
            authResult = validateLocally(token);
        }

        if (!authResult.success()) {
            abortWithUnauthorized(requestContext, authResult.message());
            return;
        }

        // Check required roles
        Set<String> requiredRoles = getRequiredRoles(annotation);
        if (!requiredRoles.isEmpty()) {
            boolean hasRequiredRole = authResult.roles().stream().anyMatch(requiredRoles::contains);
            if (!hasRequiredRole) {
                abortWithForbidden(requestContext,
                        "Insufficient permissions. Required one of: " + requiredRoles);
                return;
            }
        }

        // Set security context
        requestContext.setSecurityContext(new JwtSecurityContext(authResult.username(), authResult.roles()));

        log.debug("JWT authentication successful for user: {}", authResult.username());
    }

    private JwtProtected getJwtProtectedAnnotation() {
        // Check method first
        JwtProtected methodAnnotation = resourceInfo.getResourceMethod().getAnnotation(JwtProtected.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }

        // Fall back to class
        return resourceInfo.getResourceClass().getAnnotation(JwtProtected.class);
    }

    private Set<String> getRequiredRoles(JwtProtected annotation) {
        String[] roles = annotation.roles();
        if (roles.length == 0) {
            return Set.of();
        }
        return Arrays.stream(roles).collect(Collectors.toSet());
    }

    private AuthenticationResult validateRemotely(String token) {
        log.debug("Validating token via auth service");
        var result = authServiceClient.validateToken(token);

        return switch (result) {
            case AuthServiceClient.ValidationResult.Success success ->
                    new AuthenticationResult(true, success.username(), success.roles(), null);
            case AuthServiceClient.ValidationResult.Failure failure ->
                    new AuthenticationResult(false, null, Set.of(), failure.message());
        };
    }

    private AuthenticationResult validateLocally(String token) {
        log.debug("Validating token locally");
        try {
            JsonWebToken jwt = jwtParser.parse(token);
            return new AuthenticationResult(true, jwt.getSubject(), jwt.getGroups(), null);
        } catch (ParseException e) {
            log.warn("Local JWT validation failed: {}", e.getMessage());
            return new AuthenticationResult(false, null, Set.of(), "Invalid token");
        }
    }

    private void abortWithUnauthorized(ContainerRequestContext context, String message) {
        context.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("UNAUTHORIZED", message))
                .build());
    }

    private void abortWithForbidden(ContainerRequestContext context, String message) {
        context.abortWith(Response.status(Response.Status.FORBIDDEN)
                .entity(new ErrorResponse("FORBIDDEN", message))
                .build());
    }

    private record AuthenticationResult(boolean success, String username, Set<String> roles, String message) {}

    private record ErrorResponse(String code, String message) {}

    /**
     * Security context implementation for JWT-authenticated requests.
     */
    private static class JwtSecurityContext implements SecurityContext {
        private final String username;
        private final Set<String> roles;

        JwtSecurityContext(String username, Set<String> roles) {
            this.username = username;
            this.roles = roles;
        }

        @Override
        public Principal getUserPrincipal() {
            return () -> username;
        }

        @Override
        public boolean isUserInRole(String role) {
            return roles.contains(role);
        }

        @Override
        public boolean isSecure() {
            return true;
        }

        @Override
        public String getAuthenticationScheme() {
            return "BEARER";
        }
    }
}
