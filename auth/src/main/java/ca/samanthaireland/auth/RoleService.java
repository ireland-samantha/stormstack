package ca.samanthaireland.auth;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service for role management operations.
 *
 * <p>Provides CRUD operations for roles and handles role hierarchy validation.
 *
 * <p>Thread-safe.
 */
@Slf4j
public class RoleService {

    private final RoleRepository roleRepository;

    /**
     * Create a RoleService.
     *
     * @param roleRepository the role repository
     */
    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    /**
     * Create a new role.
     *
     * @param name the role name
     * @param description the role description
     * @return the created role
     * @throws AuthException if role name is already taken
     */
    public Role createRole(String name, String description) {
        return createRole(name, description, Set.of());
    }

    /**
     * Create a new role with included roles.
     *
     * @param name the role name
     * @param description the role description
     * @param includedRoles names of roles this role includes
     * @return the created role
     * @throws AuthException if role name is already taken or included roles don't exist
     */
    public Role createRole(String name, String description, Set<String> includedRoles) {
        if (roleRepository.existsByName(name)) {
            throw AuthException.roleTaken(name);
        }

        // Validate included roles exist
        for (String includedRole : includedRoles) {
            if (!roleRepository.existsByName(includedRole)) {
                throw AuthException.roleNotFound(includedRole);
            }
        }

        Role role = new Role(0, name, description, includedRoles);
        Role saved = roleRepository.save(role);

        log.info("Created role '{}' with includes {}", name, includedRoles);
        return saved;
    }

    /**
     * Find a role by ID.
     *
     * @param id the role ID
     * @return the role if found
     */
    public Optional<Role> findById(long id) {
        return roleRepository.findById(id);
    }

    /**
     * Find a role by name.
     *
     * @param name the role name
     * @return the role if found
     */
    public Optional<Role> findByName(String name) {
        return roleRepository.findByName(name);
    }

    /**
     * Get all roles.
     *
     * @return list of all roles
     */
    public List<Role> findAll() {
        return roleRepository.findAll();
    }

    /**
     * Update a role's description.
     *
     * @param roleId the role ID
     * @param description the new description
     * @return the updated role
     * @throws AuthException if role not found
     */
    public Role updateDescription(long roleId, String description) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> AuthException.roleNotFound("ID: " + roleId));

        Role updated = role.withDescription(description);
        roleRepository.save(updated);

        log.info("Updated description for role '{}'", role.name());
        return updated;
    }

    /**
     * Update a role's included roles.
     *
     * @param roleId the role ID
     * @param includedRoles the new set of included roles
     * @return the updated role
     * @throws AuthException if role not found or included roles don't exist
     */
    public Role updateIncludedRoles(long roleId, Set<String> includedRoles) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> AuthException.roleNotFound("ID: " + roleId));

        // Validate included roles exist and prevent self-inclusion
        for (String includedRole : includedRoles) {
            if (includedRole.equalsIgnoreCase(role.name())) {
                throw new AuthException("Role cannot include itself");
            }
            if (!roleRepository.existsByName(includedRole)) {
                throw AuthException.roleNotFound(includedRole);
            }
        }

        Role updated = role.withIncludedRoles(includedRoles);
        roleRepository.save(updated);

        log.info("Updated included roles for '{}' to {}", role.name(), includedRoles);
        return updated;
    }

    /**
     * Delete a role.
     *
     * @param roleId the role ID
     * @return true if role was deleted
     */
    public boolean deleteRole(long roleId) {
        Optional<Role> role = roleRepository.findById(roleId);
        if (role.isPresent()) {
            roleRepository.deleteById(roleId);
            log.info("Deleted role '{}'", role.get().name());
            return true;
        }
        return false;
    }

    /**
     * Check if a role name exists.
     *
     * @param name the role name to check
     * @return true if role exists
     */
    public boolean roleExists(String name) {
        return roleRepository.existsByName(name);
    }

    /**
     * Count total roles.
     *
     * @return the number of roles
     */
    public long count() {
        return roleRepository.count();
    }

    /**
     * Check if a role (by name) includes another role, considering hierarchy.
     *
     * @param roleName the role to check
     * @param targetRole the role to look for
     * @return true if roleName includes targetRole
     */
    public boolean roleIncludes(String roleName, String targetRole) {
        if (roleName.equalsIgnoreCase(targetRole)) {
            return true;
        }

        Optional<Role> role = roleRepository.findByName(roleName);
        if (role.isEmpty()) {
            return false;
        }

        // Direct inclusion
        if (role.get().includedRoles().stream()
                .anyMatch(r -> r.equalsIgnoreCase(targetRole))) {
            return true;
        }

        // Transitive inclusion
        for (String included : role.get().includedRoles()) {
            if (roleIncludes(included, targetRole)) {
                return true;
            }
        }

        return false;
    }
}
