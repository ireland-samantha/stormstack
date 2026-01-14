package ca.samanthaireland.auth;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for user persistence.
 *
 * <p>Implementations can store users in memory, database, or external
 * identity providers like Keycloak.
 */
public interface UserRepository {

    /**
     * Find a user by username.
     *
     * @param username the username to search for
     * @return the user if found
     */
    Optional<User> findByUsername(String username);

    /**
     * Find a user by ID.
     *
     * @param id the user ID
     * @return the user if found
     */
    Optional<User> findById(long id);

    /**
     * Get all users.
     *
     * @return list of all users
     */
    List<User> findAll();

    /**
     * Save a user (create or update).
     *
     * @param user the user to save
     * @return the saved user (with generated ID if new)
     */
    User save(User user);

    /**
     * Delete a user by ID.
     *
     * @param id the user ID to delete
     * @return true if user was deleted, false if not found
     */
    boolean deleteById(long id);

    /**
     * Check if a username exists.
     *
     * @param username the username to check
     * @return true if username is already taken
     */
    boolean existsByUsername(String username);

    /**
     * Count total users.
     *
     * @return the number of users
     */
    long count();
}
