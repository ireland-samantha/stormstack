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

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service for role management.
 *
 * <p>Handles CRUD operations on roles and role hierarchy resolution.
 */
public interface RoleService {

    /**
     * Creates a new role.
     *
     * @param name        the role name
     * @param description the role description
     * @return the created role
     * @throws AuthException if the role name is already taken
     */
    Role createRole(String name, String description);

    /**
     * Creates a new role with inherited roles.
     *
     * @param name            the role name
     * @param description     the role description
     * @param includedRoleIds the IDs of roles to inherit
     * @return the created role
     * @throws AuthException if the role name is taken or included roles don't exist
     */
    Role createRole(String name, String description, Set<RoleId> includedRoleIds);

    /**
     * Creates a new role with inherited roles and scopes.
     *
     * @param name            the role name
     * @param description     the role description
     * @param includedRoleIds the IDs of roles to inherit
     * @param scopes          the scopes to grant
     * @return the created role
     * @throws AuthException if the role name is taken or included roles don't exist
     */
    Role createRole(String name, String description, Set<RoleId> includedRoleIds, Set<String> scopes);

    /**
     * Finds a role by ID.
     *
     * @param id the role ID
     * @return the role if found
     */
    Optional<Role> findById(RoleId id);

    /**
     * Finds a role by name (case-insensitive).
     *
     * @param name the role name
     * @return the role if found
     */
    Optional<Role> findByName(String name);

    /**
     * Returns all roles.
     *
     * @return list of all roles
     */
    List<Role> findAll();

    /**
     * Finds all roles by their IDs.
     *
     * @param ids the role IDs
     * @return list of found roles
     */
    List<Role> findAllById(Set<RoleId> ids);

    /**
     * Updates a role's description.
     *
     * @param roleId      the role ID
     * @param description the new description
     * @return the updated role
     * @throws AuthException if the role is not found
     */
    Role updateDescription(RoleId roleId, String description);

    /**
     * Updates a role's included roles.
     *
     * @param roleId          the role ID
     * @param includedRoleIds the new included role IDs
     * @return the updated role
     * @throws AuthException if the role or included roles are not found
     */
    Role updateIncludedRoles(RoleId roleId, Set<RoleId> includedRoleIds);

    /**
     * Deletes a role.
     *
     * @param roleId the role ID
     * @return true if the role was deleted
     * @throws AuthException if the role is not found
     */
    boolean deleteRole(RoleId roleId);

    /**
     * Checks if a role includes another role (directly or transitively).
     *
     * @param roleId       the role ID to check
     * @param targetRoleId the target role ID
     * @return true if the role includes the target role
     */
    boolean roleIncludes(RoleId roleId, RoleId targetRoleId);

    /**
     * Checks if a role name includes another role name (directly or transitively).
     *
     * @param roleName       the role name to check
     * @param targetRoleName the target role name
     * @return true if the role includes the target role
     */
    boolean roleIncludes(String roleName, String targetRoleName);

    /**
     * Returns the count of roles.
     *
     * @return the role count
     */
    long count();
}
