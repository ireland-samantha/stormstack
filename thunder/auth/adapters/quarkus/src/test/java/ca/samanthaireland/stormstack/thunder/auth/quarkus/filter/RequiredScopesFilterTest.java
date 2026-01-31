package ca.samanthaireland.stormstack.thunder.auth.quarkus.filter;

import ca.samanthaireland.stormstack.thunder.auth.quarkus.annotation.RequiredScopes;
import ca.samanthaireland.stormstack.thunder.auth.quarkus.security.LightningPrincipal;
import ca.samanthaireland.stormstack.thunder.auth.quarkus.security.LightningSecurityContext;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequiredScopesFilterTest {

    @Mock
    private ContainerRequestContext requestContext;

    @Mock
    private ResourceInfo resourceInfo;

    private RequiredScopesFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequiredScopesFilter(true);
        filter.setResourceInfo(resourceInfo);
    }

    @Test
    void filter_whenDisabled_doesNothing() {
        filter = new RequiredScopesFilter(false);

        filter.filter(requestContext);

        verify(requestContext, never()).getSecurityContext();
    }

    @Test
    void filter_withNoResourceInfo_doesNothing() {
        filter.setResourceInfo(null);

        filter.filter(requestContext);

        verify(requestContext, never()).getSecurityContext();
    }

    @Test
    void filter_withNoAnnotation_allowsAccess() throws NoSuchMethodException {
        when(resourceInfo.getResourceMethod()).thenReturn(
                UnprotectedResource.class.getMethod("publicEndpoint"));
        when(resourceInfo.getResourceClass()).thenReturn((Class) UnprotectedResource.class);

        filter.filter(requestContext);

        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void filter_withMethodAnnotation_andMatchingScope_allowsAccess() throws NoSuchMethodException {
        LightningPrincipal principal = new LightningPrincipal(
                "user-123", "testuser", Set.of("read", "write"), "token-456");
        LightningSecurityContext securityContext = new LightningSecurityContext(principal, true);

        when(resourceInfo.getResourceMethod()).thenReturn(
                ProtectedResource.class.getMethod("readEndpoint"));
        when(resourceInfo.getResourceClass()).thenReturn((Class) ProtectedResource.class);
        when(requestContext.getSecurityContext()).thenReturn(securityContext);

        filter.filter(requestContext);

        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void filter_withMethodAnnotation_andMissingScope_deniesAccess() throws NoSuchMethodException {
        LightningPrincipal principal = new LightningPrincipal(
                "user-123", "testuser", Set.of("read"), "token-456");
        LightningSecurityContext securityContext = new LightningSecurityContext(principal, true);

        when(resourceInfo.getResourceMethod()).thenReturn(
                ProtectedResource.class.getMethod("adminEndpoint"));
        when(resourceInfo.getResourceClass()).thenReturn((Class) ProtectedResource.class);
        when(requestContext.getSecurityContext()).thenReturn(securityContext);

        filter.filter(requestContext);

        ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
        verify(requestContext).abortWith(responseCaptor.capture());
        assertThat(responseCaptor.getValue().getStatus()).isEqualTo(403);
    }

    @Test
    void filter_withClassAnnotation_andMatchingScope_allowsAccess() throws NoSuchMethodException {
        LightningPrincipal principal = new LightningPrincipal(
                "user-123", "testuser", Set.of("api_access"), "token-456");
        LightningSecurityContext securityContext = new LightningSecurityContext(principal, true);

        when(resourceInfo.getResourceMethod()).thenReturn(
                ClassProtectedResource.class.getMethod("anyEndpoint"));
        when(resourceInfo.getResourceClass()).thenReturn((Class) ClassProtectedResource.class);
        when(requestContext.getSecurityContext()).thenReturn(securityContext);

        filter.filter(requestContext);

        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void filter_withMethodOverridingClass_usesMethodAnnotation() throws NoSuchMethodException {
        LightningPrincipal principal = new LightningPrincipal(
                "user-123", "testuser", Set.of("admin"), "token-456");
        LightningSecurityContext securityContext = new LightningSecurityContext(principal, true);

        when(resourceInfo.getResourceMethod()).thenReturn(
                ClassProtectedResource.class.getMethod("adminOnlyEndpoint"));
        when(resourceInfo.getResourceClass()).thenReturn((Class) ClassProtectedResource.class);
        when(requestContext.getSecurityContext()).thenReturn(securityContext);

        filter.filter(requestContext);

        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void filter_withAllScopesRequired_andAllPresent_allowsAccess() throws NoSuchMethodException {
        LightningPrincipal principal = new LightningPrincipal(
                "user-123", "testuser", Set.of("read", "write", "delete"), "token-456");
        LightningSecurityContext securityContext = new LightningSecurityContext(principal, true);

        when(resourceInfo.getResourceMethod()).thenReturn(
                ProtectedResource.class.getMethod("multiScopeAllEndpoint"));
        when(resourceInfo.getResourceClass()).thenReturn((Class) ProtectedResource.class);
        when(requestContext.getSecurityContext()).thenReturn(securityContext);

        filter.filter(requestContext);

        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void filter_withAllScopesRequired_andSomeMissing_deniesAccess() throws NoSuchMethodException {
        LightningPrincipal principal = new LightningPrincipal(
                "user-123", "testuser", Set.of("read"), "token-456");
        LightningSecurityContext securityContext = new LightningSecurityContext(principal, true);

        when(resourceInfo.getResourceMethod()).thenReturn(
                ProtectedResource.class.getMethod("multiScopeAllEndpoint"));
        when(resourceInfo.getResourceClass()).thenReturn((Class) ProtectedResource.class);
        when(requestContext.getSecurityContext()).thenReturn(securityContext);

        filter.filter(requestContext);

        ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
        verify(requestContext).abortWith(responseCaptor.capture());
        assertThat(responseCaptor.getValue().getStatus()).isEqualTo(403);
    }

    @Test
    void filter_withAnyScopeRequired_andOnePresent_allowsAccess() throws NoSuchMethodException {
        LightningPrincipal principal = new LightningPrincipal(
                "user-123", "testuser", Set.of("admin"), "token-456");
        LightningSecurityContext securityContext = new LightningSecurityContext(principal, true);

        when(resourceInfo.getResourceMethod()).thenReturn(
                ProtectedResource.class.getMethod("multiScopeAnyEndpoint"));
        when(resourceInfo.getResourceClass()).thenReturn((Class) ProtectedResource.class);
        when(requestContext.getSecurityContext()).thenReturn(securityContext);

        filter.filter(requestContext);

        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void filter_withNoSecurityContext_deniesAccess() throws NoSuchMethodException {
        when(resourceInfo.getResourceMethod()).thenReturn(
                ProtectedResource.class.getMethod("readEndpoint"));
        when(resourceInfo.getResourceClass()).thenReturn((Class) ProtectedResource.class);
        when(requestContext.getSecurityContext()).thenReturn(mock(SecurityContext.class));

        filter.filter(requestContext);

        ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
        verify(requestContext).abortWith(responseCaptor.capture());
        assertThat(responseCaptor.getValue().getStatus()).isEqualTo(401);
    }

    @Test
    void filter_withNullPrincipal_deniesAccess() throws NoSuchMethodException {
        LightningSecurityContext securityContext = new LightningSecurityContext(null, true);

        when(resourceInfo.getResourceMethod()).thenReturn(
                ProtectedResource.class.getMethod("readEndpoint"));
        when(resourceInfo.getResourceClass()).thenReturn((Class) ProtectedResource.class);
        when(requestContext.getSecurityContext()).thenReturn(securityContext);

        filter.filter(requestContext);

        ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
        verify(requestContext).abortWith(responseCaptor.capture());
        assertThat(responseCaptor.getValue().getStatus()).isEqualTo(401);
    }

    // Test resource classes
    public static class UnprotectedResource {
        public void publicEndpoint() {}
    }

    public static class ProtectedResource {
        @RequiredScopes("read")
        public void readEndpoint() {}

        @RequiredScopes("admin")
        public void adminEndpoint() {}

        @RequiredScopes(value = {"read", "write"}, all = true)
        public void multiScopeAllEndpoint() {}

        @RequiredScopes(value = {"moderator", "admin"}, all = false)
        public void multiScopeAnyEndpoint() {}
    }

    @RequiredScopes("api_access")
    public static class ClassProtectedResource {
        public void anyEndpoint() {}

        @RequiredScopes("admin")
        public void adminOnlyEndpoint() {}
    }
}
