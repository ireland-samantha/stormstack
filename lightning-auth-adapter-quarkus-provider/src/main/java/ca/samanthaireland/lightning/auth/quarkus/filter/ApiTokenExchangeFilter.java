package ca.samanthaireland.lightning.auth.quarkus.filter;

import ca.samanthaireland.lightning.auth.quarkus.cache.CachedSession;
import ca.samanthaireland.lightning.auth.quarkus.cache.TokenCache;
import ca.samanthaireland.lightning.auth.quarkus.client.AuthServiceClient;
import ca.samanthaireland.lightning.auth.quarkus.client.TokenExchangeResponse;
import ca.samanthaireland.lightning.auth.quarkus.config.LightningAuthConfig;
import ca.samanthaireland.lightning.auth.quarkus.exception.LightningAuthException;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.Optional;

/**
 * JAX-RS filter that exchanges API tokens for session JWTs.
 *
 * <p>When an incoming request contains the {@code X-Api-Token} header,
 * this filter exchanges it for a short-lived session JWT via the
 * Lightning Auth service and sets the {@code Authorization} header
 * with the resulting Bearer token.
 *
 * <p>Token exchange results are cached to avoid repeated calls to the
 * auth service for the same API token.
 *
 * <p>This filter runs at {@link Priorities#AUTHENTICATION} - 10, ensuring
 * it executes before the JWT authorization filter.
 */
@Provider
@Priority(Priorities.AUTHENTICATION - 10)
@IfBuildProperty(name = "lightning.auth.filters.enabled", stringValue = "true", enableIfMissing = false)
public class ApiTokenExchangeFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(ApiTokenExchangeFilter.class);
    private static final String API_TOKEN_HEADER = "X-Api-Token";

    private final AuthServiceClient authServiceClient;
    private final TokenCache tokenCache;
    private final boolean enabled;

    @Inject
    public ApiTokenExchangeFilter(
            LightningAuthConfig config,
            AuthServiceClient authServiceClient,
            TokenCache tokenCache) {
        this.enabled = config.enabled();
        this.authServiceClient = authServiceClient;
        this.tokenCache = tokenCache;
    }

    /**
     * Constructor for testing.
     */
    ApiTokenExchangeFilter(boolean enabled, AuthServiceClient authServiceClient, TokenCache tokenCache) {
        this.enabled = enabled;
        this.authServiceClient = authServiceClient;
        this.tokenCache = tokenCache;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!enabled) {
            return;
        }

        String apiToken = requestContext.getHeaderString(API_TOKEN_HEADER);
        if (apiToken == null || apiToken.isBlank()) {
            return;
        }

        // Check if already has Authorization header - don't override
        String existingAuth = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (existingAuth != null && !existingAuth.isBlank()) {
            LOG.debug("Request already has Authorization header, skipping token exchange");
            return;
        }

        try {
            String sessionToken = getOrExchangeToken(apiToken);

            // Replace headers with new Authorization
            requestContext.getHeaders().putSingle(HttpHeaders.AUTHORIZATION, "Bearer " + sessionToken);
            LOG.debug("Successfully exchanged API token for session JWT");

        } catch (LightningAuthException e) {
            LOG.warnf("API token exchange failed: %s", e.getMessage());
            abortWithUnauthorized(requestContext, e.getCode(), e.getMessage());
        } catch (Exception e) {
            LOG.errorf(e, "Unexpected error during token exchange");
            abortWithError(requestContext, "AUTH_ERROR", "Authentication error occurred");
        }
    }

    private String getOrExchangeToken(String apiToken) {
        // Try cache first
        Optional<CachedSession> cached = tokenCache.get(apiToken);
        if (cached.isPresent()) {
            LOG.debug("Using cached session token");
            return cached.get().sessionToken();
        }

        // Exchange token
        TokenExchangeResponse response = authServiceClient.exchangeToken(apiToken);

        // Cache the result
        tokenCache.put(apiToken, response);

        return response.sessionToken();
    }

    private void abortWithUnauthorized(ContainerRequestContext context, String code, String message) {
        context.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse(code, message))
                .build());
    }

    private void abortWithError(ContainerRequestContext context, String code, String message) {
        context.abortWith(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse(code, message))
                .build());
    }

    /**
     * Simple error response DTO.
     */
    public record ErrorResponse(String code, String message) {
    }
}
