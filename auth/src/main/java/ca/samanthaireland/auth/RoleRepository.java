package ca.samanthaireland.auth;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for role persistence.
 */
public interface RoleRepository {

    /**
     * Find a role by name.
     *
     * @param name the role name
     * @return the role if found
     */
    Optional<Role> findByName(String name);

    /**
     * Find a role by ID.
     *
     * @param id the role ID
     * @return the role if found
     */
    Optional<Role> findById(long id);

    /**
     * Get all roles.
     *
     * @return list of all roles
     */
    List<Role> findAll();

    /**
     * Save a role (create or update).
     *
     * @param role the role to save
     * @return the saved role (with generated ID if new)
     */
    Role save(Role role);

    /**
     * Delete a role by ID.
     *
     * @param id the role ID to delete
     * @return true if role was deleted
     */
    boolean deleteById(long id);

    /**
     * Check if a role name exists.
     *
     * @param name the role name to check
     * @return true if role name exists
     */
    boolean existsByName(String name);

    /**
     * Count total roles.
     *
     * @return the number of roles
     */
    long count();
}
