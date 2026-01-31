package ca.samanthaireland.stormstack.thunder.auth.quarkus.filter;

import ca.samanthaireland.stormstack.thunder.auth.quarkus.config.LightningAuthConfig;
import ca.samanthaireland.stormstack.thunder.auth.quarkus.security.LightningPrincipal;
import ca.samanthaireland.stormstack.thunder.auth.quarkus.security.LightningSecurityContext;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 * JAX-RS filter that validates JWT tokens and sets the security context.
 *
 * <p>This filter extracts the Bearer token from the Authorization header,
 * validates it using SmallRye JWT, and creates a {@link LightningSecurityContext}
 * with the authenticated user's claims.
 *
 * <p>This filter runs at {@link Priorities#AUTHENTICATION}, after the
 * API token exchange filter.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
@IfBuildProperty(name = "lightning.auth.filters.enabled", stringValue = "true", enableIfMissing = false)
public class JwtAuthorizationFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(JwtAuthorizationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JWTParser jwtParser;
    private final boolean enabled;

    @Inject
    public JwtAuthorizationFilter(LightningAuthConfig config, JWTParser jwtParser) {
        this.enabled = config.enabled();
        this.jwtParser = jwtParser;
    }

    /**
     * Constructor for testing.
     */
    JwtAuthorizationFilter(boolean enabled, JWTParser jwtParser) {
        this.enabled = enabled;
        this.jwtParser = jwtParser;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!enabled) {
            return;
        }

        String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            // No Bearer token - allow request to proceed (scope enforcement will handle)
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            JsonWebToken jwt = jwtParser.parse(token);

            // Extract claims
            String userId = jwt.getSubject();
            String username = jwt.getClaim("username");
            Set<String> scopes = extractScopes(jwt);
            String apiTokenId = jwt.getClaim("api_token_id");

            // Create principal and set security context
            LightningPrincipal principal = new LightningPrincipal(userId, username, scopes, apiTokenId);
            LightningSecurityContext securityContext = new LightningSecurityContext(
                    principal,
                    requestContext.getSecurityContext().isSecure()
            );

            requestContext.setSecurityContext(securityContext);
            LOG.debugf("Authenticated user: %s with scopes: %s", username, scopes);

        } catch (ParseException e) {
            LOG.warnf("JWT validation failed: %s", e.getMessage());
            abortWithUnauthorized(requestContext, "INVALID_TOKEN", "Invalid or expired token");
        } catch (Exception e) {
            LOG.errorf(e, "Unexpected error during JWT validation");
            abortWithUnauthorized(requestContext, "AUTH_ERROR", "Authentication error occurred");
        }
    }

    @SuppressWarnings("unchecked")
    private Set<String> extractScopes(JsonWebToken jwt) {
        Set<String> scopes = new HashSet<>();

        // Try "scopes" claim (array or space-separated string)
        Object scopesClaim = jwt.getClaim("scopes");
        if (scopesClaim instanceof Iterable<?>) {
            for (Object scope : (Iterable<?>) scopesClaim) {
                scopes.add(scope.toString());
            }
        } else if (scopesClaim instanceof String) {
            // Handle space-separated scopes (OAuth2 style)
            String[] parts = ((String) scopesClaim).split("\\s+");
            for (String part : parts) {
                if (!part.isBlank()) {
                    scopes.add(part);
                }
            }
        }

        // Also check "scope" claim (singular)
        Object scopeClaim = jwt.getClaim("scope");
        if (scopeClaim instanceof String) {
            String[] parts = ((String) scopeClaim).split("\\s+");
            for (String part : parts) {
                if (!part.isBlank()) {
                    scopes.add(part);
                }
            }
        }

        // Also include groups as scopes (common MicroProfile pattern)
        Set<String> groups = jwt.getGroups();
        if (groups != null) {
            scopes.addAll(groups);
        }

        return scopes;
    }

    private void abortWithUnauthorized(ContainerRequestContext context, String code, String message) {
        context.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ApiTokenExchangeFilter.ErrorResponse(code, message))
                .build());
    }
}
