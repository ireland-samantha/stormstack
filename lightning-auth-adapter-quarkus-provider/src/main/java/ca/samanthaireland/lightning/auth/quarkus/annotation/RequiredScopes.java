package ca.samanthaireland.lightning.auth.quarkus.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to declare required permission scopes for a resource method or class.
 *
 * <p>When applied to a class, all methods in that class require the specified scopes.
 * Method-level annotations override class-level annotations.
 *
 * <p>Examples:
 * <pre>
 * // Require specific scope for all methods in this resource
 * &#64;Path("/snapshots")
 * &#64;RequiredScopes("view_snapshots")
 * public class SnapshotResource {
 *
 *     // Inherits "view_snapshots" requirement
 *     &#64;GET
 *     public List&lt;Snapshot&gt; list() { ... }
 *
 *     // Requires EITHER "submit_commands" OR "admin" scope
 *     &#64;POST
 *     &#64;RequiredScopes(value = {"submit_commands", "admin"}, all = false)
 *     public Response create() { ... }
 *
 *     // Requires BOTH "admin" AND "delete_resources" scopes
 *     &#64;DELETE
 *     &#64;RequiredScopes(value = {"admin", "delete_resources"}, all = true)
 *     public Response delete() { ... }
 * }
 * </pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiredScopes {

    /**
     * The scopes required to access this resource.
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
