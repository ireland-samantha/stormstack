package ca.samanthaireland.lightning.auth.quarkus.security;

import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;

/**
 * JAX-RS SecurityContext implementation for Lightning Auth.
 */
public class LightningSecurityContext implements SecurityContext {

    private final LightningPrincipal principal;
    private final boolean secure;

    public LightningSecurityContext(LightningPrincipal principal, boolean secure) {
        this.principal = principal;
        this.secure = secure;
    }

    @Override
    public Principal getUserPrincipal() {
        return principal;
    }

    @Override
    public boolean isUserInRole(String role) {
        // In Lightning Auth, roles are represented as scopes
        return principal != null && principal.hasScope(role);
    }

    @Override
    public boolean isSecure() {
        return secure;
    }

    @Override
    public String getAuthenticationScheme() {
        return "Bearer";
    }

    /**
     * Get the Lightning principal with full details.
     *
     * @return the principal or null if not authenticated
     */
    public LightningPrincipal getLightningPrincipal() {
        return principal;
    }
}
