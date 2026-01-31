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

import ca.samanthaireland.stormstack.thunder.auth.model.User;
import ca.samanthaireland.stormstack.thunder.auth.model.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for User persistence.
 *
 * <p>Implementations of this interface handle the persistence of User entities
 * to various storage backends (MongoDB, in-memory, etc.).
 */
public interface UserRepository {

    /**
     * Finds a user by their unique ID.
     *
     * @param id the user ID
     * @return the user if found
     */
    Optional<User> findById(UserId id);

    /**
     * Finds a user by their username.
     *
     * <p>Username lookup should be case-insensitive.
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
     * Saves a user (insert or update).
     *
     * <p>If the user ID already exists, updates the existing user.
     * Otherwise, inserts a new user.
     *
     * @param user the user to save
     * @return the saved user
     */
    User save(User user);

    /**
     * Deletes a user by their ID.
     *
     * @param id the user ID
     * @return true if the user was deleted
     */
    boolean deleteById(UserId id);

    /**
     * Checks if a username is already taken.
     *
     * <p>Check should be case-insensitive.
     *
     * @param username the username to check
     * @return true if the username exists
     */
    boolean existsByUsername(String username);

    /**
     * Returns the total count of users.
     *
     * @return the user count
     */
    long count();
}
