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

package ca.samanthaireland.stormstack.thunder.controlplane.provider.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a JAX-RS resource class or method as requiring JWT authentication.
 *
 * <p>When applied, the {@link JwtAuthFilter} will validate the JWT token
 * in the Authorization header before allowing access.
 *
 * <p>Usage:
 * <pre>{@code
 * @JwtProtected(roles = {"admin"})
 * @GET
 * @Path("/admin")
 * public Response adminEndpoint() { ... }
 *
 * // Or at class level:
 * @JwtProtected
 * @Path("/api/admin")
 * public class AdminResource { ... }
 * }</pre>
 *
 * <p>Note: This annotation only takes effect when JWT authentication is enabled
 * via the {@code control-plane.jwt.enabled=true} configuration.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface JwtProtected {

    /**
     * Required roles for access. If empty, any authenticated user can access.
     * If specified, the user must have at least one of the listed roles.
     *
     * @return array of allowed role names
     */
    String[] roles() default {};
}
