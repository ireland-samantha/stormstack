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
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * AOP aspect that enforces {@link RequiredScopes} annotations.
 *
 * <p>This aspect intercepts method calls annotated with {@code @RequiredScopes}
 * and verifies that the current authentication has the required scopes before
 * allowing the method to proceed.
 *
 * <p>Scope checking logic:
 * <ul>
 *   <li>If {@code all = false} (default): user must have at least one scope (OR)</li>
 *   <li>If {@code all = true}: user must have all specified scopes (AND)</li>
 * </ul>
 */
@Aspect
public class RequiredScopesAspect {

    private static final Logger log = LoggerFactory.getLogger(RequiredScopesAspect.class);

    /**
     * Intercepts method calls with @RequiredScopes annotation.
     */
    @Around("@annotation(ca.samanthaireland.stormstack.thunder.auth.spring.annotation.RequiredScopes) || " +
            "@within(ca.samanthaireland.stormstack.thunder.auth.spring.annotation.RequiredScopes)")
    public Object checkScopes(ProceedingJoinPoint joinPoint) throws Throwable {
        RequiredScopes annotation = getAnnotation(joinPoint);

        if (annotation == null) {
            return joinPoint.proceed();
        }

        String[] requiredScopes = annotation.value();
        boolean requireAll = annotation.all();

        // Get current authentication
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            log.warn("Access denied: no authenticated user for method {}",
                    joinPoint.getSignature().getName());
            throw new AccessDeniedException("Authentication required");
        }

        // Get user's scopes
        Set<String> userScopes = getUserScopes(auth);

        // Check scopes
        boolean hasAccess = requireAll
                ? hasAllScopes(userScopes, requiredScopes)
                : hasAnyScope(userScopes, requiredScopes);

        if (!hasAccess) {
            log.warn("Access denied for user {}: required scopes {} (all={}), actual scopes {}",
                    auth.getName(), requiredScopes, requireAll, userScopes);
            throw new AccessDeniedException(String.format(
                    "Insufficient scopes. Required: %s (all=%s), actual: %s",
                    String.join(", ", requiredScopes), requireAll, userScopes));
        }

        log.debug("Access granted for user {} to method {}",
                auth.getName(), joinPoint.getSignature().getName());

        return joinPoint.proceed();
    }

    /**
     * Gets the @RequiredScopes annotation, preferring method-level over class-level.
     */
    private RequiredScopes getAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // Method-level annotation takes precedence
        RequiredScopes methodAnnotation = method.getAnnotation(RequiredScopes.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }

        // Fall back to class-level annotation
        return joinPoint.getTarget().getClass().getAnnotation(RequiredScopes.class);
    }

    /**
     * Extracts scopes from the authentication.
     */
    private Set<String> getUserScopes(Authentication auth) {
        if (auth instanceof LightningAuthentication lightningAuth) {
            return lightningAuth.getScopes();
        }

        // Fall back to extracting from authorities
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith("SCOPE_"))
                .map(a -> a.substring(6))
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Checks if user has any of the required scopes (OR logic).
     */
    private boolean hasAnyScope(Set<String> userScopes, String[] requiredScopes) {
        for (String required : requiredScopes) {
            if (userScopes.contains(required)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if user has all of the required scopes (AND logic).
     */
    private boolean hasAllScopes(Set<String> userScopes, String[] requiredScopes) {
        for (String required : requiredScopes) {
            if (!userScopes.contains(required)) {
                return false;
            }
        }
        return true;
    }
}
