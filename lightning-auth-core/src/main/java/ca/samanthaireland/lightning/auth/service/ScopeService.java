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

package ca.samanthaireland.lightning.auth.service;

import ca.samanthaireland.lightning.auth.exception.AuthException;
import ca.samanthaireland.lightning.auth.model.Role;
import ca.samanthaireland.lightning.auth.model.RoleId;

import java.util.Set;

/**
 * Service for managing role scopes.
 *
 * <p>Handles scope CRUD operations and transitive scope resolution.
 * This interface follows the Interface Segregation Principle (ISP) by
 * separating scope management from role CRUD operations.
 */
public interface ScopeService {

    /**
     * Updates a role's scopes.
     *
     * @param roleId the role ID
     * @param scopes the new scopes
     * @return the updated role
     * @throws AuthException if the role is not found
     */
    Role updateScopes(RoleId roleId, Set<String> scopes);

    /**
     * Adds a scope to a role.
     *
     * @param roleId the role ID
     * @param scope  the scope to add
     * @return the updated role
     * @throws AuthException if the role is not found
     */
    Role addScope(RoleId roleId, String scope);

    /**
     * Removes a scope from a role.
     *
     * @param roleId the role ID
     * @param scope  the scope to remove
     * @return the updated role
     * @throws AuthException if the role is not found
     */
    Role removeScope(RoleId roleId, String scope);

    /**
     * Resolves all scopes for a role, including inherited scopes from included roles.
     *
     * @param roleId the role ID
     * @return all resolved scopes (direct + inherited)
     * @throws AuthException if the role is not found
     */
    Set<String> resolveScopes(RoleId roleId);

    /**
     * Resolves all scopes for multiple roles, including inherited scopes.
     * This is useful for computing a user's effective scopes from all their roles.
     *
     * @param roleIds the role IDs
     * @return all resolved scopes from all roles (direct + inherited)
     */
    Set<String> resolveScopes(Set<RoleId> roleIds);
}
