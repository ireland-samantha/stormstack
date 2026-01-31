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

import ca.samanthaireland.stormstack.thunder.auth.spring.annotation.RequiredScopes;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RequiredScopesAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    private RequiredScopesAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new RequiredScopesAspect();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void checkScopes_allowsAccessWithMatchingScope() throws Throwable {
        setUpAuthentication(Set.of("view_snapshots", "submit_commands"));
        setUpAnnotatedMethod("annotatedWithViewSnapshots");
        when(joinPoint.proceed()).thenReturn("success");

        Object result = aspect.checkScopes(joinPoint);

        assertThat(result).isEqualTo("success");
        verify(joinPoint).proceed();
    }

    @Test
    void checkScopes_allowsAccessWithAnyScopeMatch_orLogic() throws Throwable {
        setUpAuthentication(Set.of("submit_commands"));
        setUpAnnotatedMethod("annotatedWithMultipleScopes");
        when(joinPoint.proceed()).thenReturn("success");

        Object result = aspect.checkScopes(joinPoint);

        assertThat(result).isEqualTo("success");
    }

    @Test
    void checkScopes_deniesAccessWithoutAuthentication() throws Throwable {
        // No authentication set
        setUpAnnotatedMethod("annotatedWithViewSnapshots");

        assertThatThrownBy(() -> aspect.checkScopes(joinPoint))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    void checkScopes_deniesAccessWithoutMatchingScope() throws Throwable {
        setUpAuthentication(Set.of("view_snapshots"));
        setUpAnnotatedMethod("annotatedWithAdmin");

        assertThatThrownBy(() -> aspect.checkScopes(joinPoint))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Insufficient scopes");
    }

    @Test
    void checkScopes_requiresAllScopes_whenAllIsTrue() throws Throwable {
        setUpAuthentication(Set.of("view_snapshots", "submit_commands"));
        setUpAnnotatedMethod("annotatedWithAllRequired");

        assertThatThrownBy(() -> aspect.checkScopes(joinPoint))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Insufficient scopes");
    }

    @Test
    void checkScopes_allowsAccessWithAllScopes_whenAllIsTrue() throws Throwable {
        setUpAuthentication(Set.of("view_snapshots", "admin", "submit_commands"));
        setUpAnnotatedMethod("annotatedWithAllRequired");
        when(joinPoint.proceed()).thenReturn("success");

        Object result = aspect.checkScopes(joinPoint);

        assertThat(result).isEqualTo("success");
    }

    @Test
    void checkScopes_proceedsWithoutAnnotation() throws Throwable {
        setUpAuthentication(Set.of("view_snapshots"));
        setUpAnnotatedMethod("unannotatedMethod");
        when(joinPoint.proceed()).thenReturn("success");

        Object result = aspect.checkScopes(joinPoint);

        assertThat(result).isEqualTo("success");
    }

    private void setUpAuthentication(Set<String> scopes) {
        LightningAuthentication auth = new LightningAuthentication(
                "user-123",
                "testuser",
                scopes,
                "jwt.token",
                null,
                Instant.now().plusSeconds(3600)
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void setUpAnnotatedMethod(String methodName) throws NoSuchMethodException {
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        Method method = TestService.class.getMethod(methodName);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(new TestService());
    }

    // Test service class with annotated methods
    public static class TestService {

        public String unannotatedMethod() {
            return "result";
        }

        @RequiredScopes("view_snapshots")
        public String annotatedWithViewSnapshots() {
            return "result";
        }

        @RequiredScopes("admin")
        public String annotatedWithAdmin() {
            return "result";
        }

        @RequiredScopes({"view_snapshots", "submit_commands"})
        public String annotatedWithMultipleScopes() {
            return "result";
        }

        @RequiredScopes(value = {"view_snapshots", "admin"}, all = true)
        public String annotatedWithAllRequired() {
            return "result";
        }
    }
}
