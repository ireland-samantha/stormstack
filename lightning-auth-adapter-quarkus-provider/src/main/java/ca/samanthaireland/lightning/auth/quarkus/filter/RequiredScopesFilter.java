package ca.samanthaireland.lightning.auth.quarkus.filter;

import ca.samanthaireland.lightning.auth.quarkus.annotation.RequiredScopes;
import ca.samanthaireland.lightning.auth.quarkus.annotation.Scopes;
import ca.samanthaireland.lightning.auth.quarkus.config.LightningAuthConfig;
import ca.samanthaireland.lightning.auth.quarkus.security.LightningPrincipal;
import ca.samanthaireland.lightning.auth.quarkus.security.LightningSecurityContext;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * JAX-RS filter that enforces {@link RequiredScopes} and {@link Scopes} annotations.
 *
 * <p>This filter checks if the authenticated user has the required scopes
 * to access the requested resource. It supports both method-level and
 * class-level annotations, with method-level taking precedence.
 *
 * <p>Supports wildcard scope matching with the unified format:
 * {@code service.resource.operation}
 *
 * <p>Wildcard examples:
 * <ul>
 *   <li>{@code *} - Full admin access</li>
 *   <li>{@code engine.*} - All engine operations</li>
 *   <li>{@code engine.container.*} - All container operations</li>
 * </ul>
 *
 * <p>This filter runs at {@link Priorities#AUTHORIZATION}, after authentication.
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
@IfBuildProperty(name = "lightning.auth.filters.enabled", stringValue = "true", enableIfMissing = false)
public class RequiredScopesFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(RequiredScopesFilter.class);

    @Context
    private ResourceInfo resourceInfo;

    private final boolean enabled;

    @Inject
    public RequiredScopesFilter(LightningAuthConfig config) {
        this.enabled = config.enabled();
    }

    /**
     * Constructor for testing.
     */
    RequiredScopesFilter(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Set ResourceInfo for testing.
     */
    void setResourceInfo(ResourceInfo resourceInfo) {
        this.resourceInfo = resourceInfo;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!enabled || resourceInfo == null) {
            return;
        }

        ScopeRequirement requirement = getScopeRequirement();
        if (requirement == null) {
            // No scope requirements on this endpoint
            return;
        }

        SecurityContext securityContext = requestContext.getSecurityContext();

        // Check if user is authenticated
        if (!(securityContext instanceof LightningSecurityContext lightningContext)) {
            LOG.debug("No LightningSecurityContext found, denying access to protected resource");
            abortWithUnauthorized(requestContext);
            return;
        }

        LightningPrincipal principal = lightningContext.getLightningPrincipal();
        if (principal == null) {
            LOG.debug("No principal found in security context, denying access");
            abortWithUnauthorized(requestContext);
            return;
        }

        // Check scopes using wildcard-aware matching via LightningPrincipal
        Set<String> required = requirement.scopes();
        boolean hasAccess;

        if (requirement.all()) {
            // AND logic - must have all scopes (supports wildcards)
            hasAccess = principal.hasAllScopes(required);
        } else {
            // OR logic - must have any scope (supports wildcards)
            hasAccess = principal.hasAnyScope(required);
        }

        if (!hasAccess) {
            LOG.warnf("User %s denied access: required scopes %s (all=%s), has scopes %s",
                    principal.getUsername(), required, requirement.all(), principal.getScopes());
            abortWithForbidden(requestContext, required);
            return;
        }

        LOG.debugf("User %s granted access to resource requiring scopes %s",
                principal.getUsername(), required);
    }

    /**
     * Get scope requirements from either @Scopes or @RequiredScopes annotation.
     *
     * @return the scope requirement, or null if no annotation is present
     */
    private ScopeRequirement getScopeRequirement() {
        Method method = resourceInfo.getResourceMethod();
        Class<?> resourceClass = resourceInfo.getResourceClass();

        // Method annotation takes precedence - check @Scopes first, then @RequiredScopes
        Scopes methodScopes = method.getAnnotation(Scopes.class);
        if (methodScopes != null) {
            return new ScopeRequirement(Set.of(methodScopes.value()), methodScopes.all());
        }

        RequiredScopes methodRequiredScopes = method.getAnnotation(RequiredScopes.class);
        if (methodRequiredScopes != null) {
            return new ScopeRequirement(Set.of(methodRequiredScopes.value()), methodRequiredScopes.all());
        }

        // Fall back to class annotation - check @Scopes first, then @RequiredScopes
        Scopes classScopes = resourceClass.getAnnotation(Scopes.class);
        if (classScopes != null) {
            return new ScopeRequirement(Set.of(classScopes.value()), classScopes.all());
        }

        RequiredScopes classRequiredScopes = resourceClass.getAnnotation(RequiredScopes.class);
        if (classRequiredScopes != null) {
            return new ScopeRequirement(Set.of(classRequiredScopes.value()), classRequiredScopes.all());
        }

        return null;
    }

    private void abortWithUnauthorized(ContainerRequestContext context) {
        context.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ApiTokenExchangeFilter.ErrorResponse(
                        "UNAUTHORIZED",
                        "Authentication required"))
                .build());
    }

    private void abortWithForbidden(ContainerRequestContext context, Set<String> requiredScopes) {
        context.abortWith(Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiTokenExchangeFilter.ErrorResponse(
                        "INSUFFICIENT_SCOPE",
                        "Missing required scope(s): " + requiredScopes))
                .build());
    }

    /**
     * Internal record to hold scope requirement from either annotation type.
     */
    private record ScopeRequirement(Set<String> scopes, boolean all) {}
}
