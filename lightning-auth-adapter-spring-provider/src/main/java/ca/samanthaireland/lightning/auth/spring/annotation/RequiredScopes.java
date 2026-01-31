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

package ca.samanthaireland.lightning.auth.spring.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to require specific scopes for accessing a method or class.
 *
 * <p>When applied to a method, the authenticated user must have the specified
 * scope(s) to access that method. When applied to a class, all methods in that
 * class require the specified scope(s).
 *
 * <p>Method-level annotations override class-level annotations.
 *
 * <h2>Usage Examples</h2>
 *
 * <p>Require a single scope:
 * <pre>{@code
 * @RequiredScopes("view_snapshots")
 * public Snapshot getSnapshot(String id) { ... }
 * }</pre>
 *
 * <p>Require any of multiple scopes (OR logic):
 * <pre>{@code
 * @RequiredScopes({"admin", "submit_commands"})
 * public void executeCommand(Command cmd) { ... }
 * }</pre>
 *
 * <p>Require all specified scopes (AND logic):
 * <pre>{@code
 * @RequiredScopes(value = {"admin", "super_admin"}, all = true)
 * public void deleteUser(String userId) { ... }
 * }</pre>
 *
 * <p>Apply to all methods in a class:
 * <pre>{@code
 * @RestController
 * @RequiredScopes("view_snapshots")
 * public class SnapshotController {
 *     // All methods require "view_snapshots" scope
 * }
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiredScopes {

    /**
     * The required scope(s).
     *
     * <p>By default (when {@link #all()} is false), the user must have at least
     * one of the specified scopes (OR logic). When {@link #all()} is true, the
     * user must have all specified scopes (AND logic).
     *
     * @return the required scopes
     */
    String[] value();

    /**
     * Whether all specified scopes are required (AND logic).
     *
     * <p>When false (default), the user must have at least one of the specified
     * scopes. When true, the user must have all specified scopes.
     *
     * @return true for AND logic, false for OR logic
     */
    boolean all() default false;
}
