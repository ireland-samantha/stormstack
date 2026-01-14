package ca.samanthaireland.auth;

import java.util.Set;

/**
 * Represents a role in the RBAC system.
 *
 * <p>Roles are created dynamically and can include other roles to form
 * a hierarchy. A role that includes another role inherits all its permissions.
 *
 * @param id unique role identifier
 * @param name the role name (e.g., "admin", "command_manager")
 * @param description human-readable description of the role
 * @param includedRoles names of roles that this role includes (inherits permissions from)
 */
public record Role(
        long id,
        String name,
        String description,
        Set<String> includedRoles
) {
    /**
     * Create a role with no included roles.
     *
     * @param id unique role identifier
     * @param name the role name
     * @param description human-readable description
     */
    public Role(long id, String name, String description) {
        this(id, name, description, Set.of());
    }

    /**
     * Check if this role includes another role (directly or transitively).
     *
     * @param roleName the role name to check
     * @return true if this role includes the specified role
     */
    public boolean includes(String roleName) {
        return name.equals(roleName) || includedRoles.contains(roleName);
    }

    /**
     * Create a new Role with updated included roles.
     *
     * @param newIncludedRoles the new set of included roles
     * @return a new Role instance with updated includes
     */
    public Role withIncludedRoles(Set<String> newIncludedRoles) {
        return new Role(id, name, description, newIncludedRoles);
    }

    /**
     * Create a new Role with updated description.
     *
     * @param newDescription the new description
     * @return a new Role instance with updated description
     */
    public Role withDescription(String newDescription) {
        return new Role(id, name, newDescription, includedRoles);
    }
}
