package ca.samanthaireland.lightning.auth.quarkus.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to declare required permission scopes for a resource method or class.
 *
 * <p>This annotation uses the unified scope format: {@code service.resource.operation}
 *
 * <p>When applied to a class, all methods in that class require the specified scopes.
 * Method-level annotations override class-level annotations.
 *
 * <h2>Scope Format</h2>
 * <pre>
 * service.resource.operation
 *
 * Examples:
 * - engine.container.create       # Create containers
 * - engine.container.*            # All container operations
 * - engine.*                      # All engine operations
 * - auth.user.read                # Read users
 * - control-plane.module.upload   # Upload modules
 * - *                             # Full admin access (all operations)
 * </pre>
 *
 * <h2>Wildcard Rules</h2>
 * <ul>
 *   <li>{@code *} at any position matches all values at that level and below</li>
 *   <li>{@code *} alone grants full access to everything</li>
 *   <li>More specific scopes take precedence in evaluation</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 * <pre>
 * // Require specific scope for all methods in this resource
 * &#64;Path("/api/containers")
 * &#64;Scopes("engine.container.read")
 * public class ContainerResource {
 *
 *     // Inherits "engine.container.read" requirement
 *     &#64;GET
 *     public List&lt;Container&gt; list() { ... }
 *
 *     // Requires "engine.container.create" scope
 *     &#64;POST
 *     &#64;Scopes("engine.container.create")
 *     public Response create() { ... }
 *
 *     // Requires BOTH admin AND specific scope
 *     &#64;DELETE
 *     &#64;Scopes(value = {"engine.container.delete", "engine.admin"}, all = true)
 *     public Response delete() { ... }
 * }
 * </pre>
 *
 * @see RequiredScopes
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Scopes {

    /**
     * The scopes required to access this resource.
     *
     * <p>Use the unified format: {@code service.resource.operation}
     *
     * @return array of required scope names
     */
    String[] value();

    /**
     * If true, the user must have ALL specified scopes (AND logic).
     * If false (default), the user must have ANY of the specified scopes (OR logic).
     *
     * @return true for AND logic, false for OR logic
     */
    boolean all() default false;
}
