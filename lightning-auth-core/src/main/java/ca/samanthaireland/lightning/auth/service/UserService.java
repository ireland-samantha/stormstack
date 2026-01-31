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
import ca.samanthaireland.lightning.auth.model.RoleId;
import ca.samanthaireland.lightning.auth.model.User;
import ca.samanthaireland.lightning.auth.model.UserId;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service for user management.
 *
 * <p>Handles CRUD operations on users including password hashing.
 */
public interface UserService {

    /**
     * Creates a new user.
     *
     * @param username the username
     * @param password the plain text password
     * @param roleIds  the role IDs to assign
     * @return the created user
     * @throws AuthException if the username is taken or roles don't exist
     */
    User createUser(String username, String password, Set<RoleId> roleIds);

    /**
     * Creates a new user with scopes.
     *
     * @param username the username
     * @param password the plain text password
     * @param roleIds  the role IDs to assign
     * @param scopes   the permission scopes (format: service.resource.operation)
     * @return the created user
     * @throws AuthException if the username is taken or roles don't exist
     */
    User createUser(String username, String password, Set<RoleId> roleIds, Set<String> scopes);

    /**
     * Finds a user by ID.
     *
     * @param id the user ID
     * @return the user if found
     */
    Optional<User> findById(UserId id);

    /**
     * Finds a user by username (case-insensitive).
     *
     * @param username the username
     * @return the user if found
     */
    Optional<User> findByUsername(String username);

    /**
     * Returns all users.
     *
     * @return list of all users
     */
    List<User> findAll();

    /**
     * Updates a user's password.
     *
     * @param userId      the user ID
     * @param newPassword the new plain text password
     * @return the updated user
     * @throws AuthException if the user is not found
     */
    User updatePassword(UserId userId, String newPassword);

    /**
     * Updates a user's roles.
     *
     * @param userId  the user ID
     * @param roleIds the new role IDs
     * @return the updated user
     * @throws AuthException if the user or roles are not found
     */
    User updateRoles(UserId userId, Set<RoleId> roleIds);

    /**
     * Updates a user's scopes.
     *
     * @param userId the user ID
     * @param scopes the new scopes (format: service.resource.operation)
     * @return the updated user
     * @throws AuthException if the user is not found
     */
    User updateScopes(UserId userId, Set<String> scopes);

    /**
     * Adds a scope to a user.
     *
     * @param userId the user ID
     * @param scope  the scope to add (format: service.resource.operation)
     * @return the updated user
     * @throws AuthException if the user is not found
     */
    User addScope(UserId userId, String scope);

    /**
     * Removes a scope from a user.
     *
     * @param userId the user ID
     * @param scope  the scope to remove
     * @return the updated user
     * @throws AuthException if the user is not found
     */
    User removeScope(UserId userId, String scope);

    /**
     * Adds a role to a user.
     *
     * @param userId the user ID
     * @param roleId the role ID to add
     * @return the updated user
     * @throws AuthException if the user or role is not found
     */
    User addRole(UserId userId, RoleId roleId);

    /**
     * Removes a role from a user.
     *
     * @param userId the user ID
     * @param roleId the role ID to remove
     * @return the updated user
     * @throws AuthException if the user is not found
     */
    User removeRole(UserId userId, RoleId roleId);

    /**
     * Enables or disables a user.
     *
     * @param userId  the user ID
     * @param enabled the enabled status
     * @return the updated user
     * @throws AuthException if the user is not found
     */
    User setEnabled(UserId userId, boolean enabled);

    /**
     * Deletes a user.
     *
     * @param userId the user ID
     * @return true if the user was deleted
     * @throws AuthException if the user is not found
     */
    boolean deleteUser(UserId userId);

    /**
     * Checks if a username is available.
     *
     * @param username the username to check
     * @return true if the username is available
     */
    boolean isUsernameAvailable(String username);

    /**
     * Checks if a user exists by username (case-insensitive).
     *
     * @param username the username to check
     * @return true if the user exists
     */
    boolean existsByUsername(String username);

    /**
     * Returns the count of users.
     *
     * @return the user count
     */
    long count();
}
