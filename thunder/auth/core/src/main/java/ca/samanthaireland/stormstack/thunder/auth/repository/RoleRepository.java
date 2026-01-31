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

package ca.samanthaireland.stormstack.thunder.auth.repository;

import ca.samanthaireland.stormstack.thunder.auth.model.Role;
import ca.samanthaireland.stormstack.thunder.auth.model.RoleId;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repository interface for Role persistence.
 *
 * <p>Implementations of this interface handle the persistence of Role entities
 * to various storage backends (MongoDB, in-memory, etc.).
 */
public interface RoleRepository {

    /**
     * Finds a role by its unique ID.
     *
     * @param id the role ID
     * @return the role if found
     */
    Optional<Role> findById(RoleId id);

    /**
     * Finds a role by its name.
     *
     * <p>Role name lookup should be case-insensitive.
     *
     * @param name the role name
     * @return the role if found
     */
    Optional<Role> findByName(String name);

    /**
     * Finds all roles by their IDs.
     *
     * @param ids the role IDs
     * @return list of found roles (may be fewer than requested if some don't exist)
     */
    List<Role> findAllById(Set<RoleId> ids);

    /**
     * Returns all roles.
     *
     * @return list of all roles
     */
    List<Role> findAll();

    /**
     * Saves a role (insert or update).
     *
     * <p>If the role ID already exists, updates the existing role.
     * Otherwise, inserts a new role.
     *
     * @param role the role to save
     * @return the saved role
     */
    Role save(Role role);

    /**
     * Deletes a role by its ID.
     *
     * @param id the role ID
     * @return true if the role was deleted
     */
    boolean deleteById(RoleId id);

    /**
     * Checks if a role name is already taken.
     *
     * <p>Check should be case-insensitive.
     *
     * @param name the role name to check
     * @return true if the role name exists
     */
    boolean existsByName(String name);

    /**
     * Returns the total count of roles.
     *
     * @return the role count
     */
    long count();
}
