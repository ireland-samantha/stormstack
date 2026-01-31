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

package ca.samanthaireland.lightning.auth.model;

import java.util.Objects;
import java.util.Set;

/**
 * Represents a role in the authentication system.
 *
 * <p>Roles support inheritance through the {@code includedRoleIds} field,
 * allowing hierarchical permissions (e.g., admin includes all permissions of user).
 *
 * <p>Scopes define the permissions granted by this role using the format
 * {@code service.resource.operation} (e.g., "auth.user.read", "engine.container.*").
 *
 * @param id              unique role identifier
 * @param name            the role name (unique, case-insensitive)
 * @param description     human-readable description
 * @param includedRoleIds role IDs that are inherited by this role
 * @param scopes          permission scopes granted by this role
 */
public record Role(
        RoleId id,
        String name,
        String description,
        Set<RoleId> includedRoleIds,
        Set<String> scopes
) {

    public Role {
        Objects.requireNonNull(id, "Role id cannot be null");
        Objects.requireNonNull(name, "Role name cannot be null");
        Objects.requireNonNull(includedRoleIds, "Included role IDs cannot be null");
        Objects.requireNonNull(scopes, "Scopes cannot be null");

        if (name.isBlank()) {
            throw new IllegalArgumentException("Role name cannot be blank");
        }

        // Defensive copies
        includedRoleIds = Set.copyOf(includedRoleIds);
        scopes = Set.copyOf(scopes);
    }

    /**
     * Legacy constructor for backward compatibility.
     * Creates a role with no scopes.
     */
    public Role(RoleId id, String name, String description, Set<RoleId> includedRoleIds) {
        this(id, name, description, includedRoleIds, Set.of());
    }

    /**
     * Creates a new role with the given details.
     *
     * @param name        the role name
     * @param description the description
     * @return a new Role with a generated ID
     */
    public static Role create(String name, String description) {
        return new Role(
                RoleId.generate(),
                name,
                description,
                Set.of(),
                Set.of()
        );
    }

    /**
     * Creates a new role with the given details and included roles.
     *
     * @param name            the role name
     * @param description     the description
     * @param includedRoleIds the role IDs to inherit
     * @return a new Role with a generated ID
     */
    public static Role create(String name, String description, Set<RoleId> includedRoleIds) {
        return new Role(
                RoleId.generate(),
                name,
                description,
                includedRoleIds,
                Set.of()
        );
    }

    /**
     * Creates a new role with the given details, included roles, and scopes.
     *
     * @param name            the role name
     * @param description     the description
     * @param includedRoleIds the role IDs to inherit
     * @param scopes          the scopes to grant
     * @return a new Role with a generated ID
     */
    public static Role create(String name, String description, Set<RoleId> includedRoleIds, Set<String> scopes) {
        return new Role(
                RoleId.generate(),
                name,
                description,
                includedRoleIds,
                scopes
        );
    }

    /**
     * Check if this role directly includes another role.
     *
     * @param roleId the role ID to check
     * @return true if this role includes the given role
     */
    public boolean includes(RoleId roleId) {
        return includedRoleIds.contains(roleId);
    }

    /**
     * Check if this role directly has a scope.
     *
     * @param scope the scope to check
     * @return true if this role has the given scope
     */
    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }

    /**
     * Create a new Role with different included role IDs.
     *
     * @param newIncludedRoleIds the new included role IDs
     * @return a new Role instance with the updated included roles
     */
    public Role withIncludedRoleIds(Set<RoleId> newIncludedRoleIds) {
        return new Role(id, name, description, newIncludedRoleIds, scopes);
    }

    /**
     * Create a new Role with a different description.
     *
     * @param newDescription the new description
     * @return a new Role instance with the updated description
     */
    public Role withDescription(String newDescription) {
        return new Role(id, name, newDescription, includedRoleIds, scopes);
    }

    /**
     * Create a new Role with different scopes.
     *
     * @param newScopes the new scopes
     * @return a new Role instance with the updated scopes
     */
    public Role withScopes(Set<String> newScopes) {
        return new Role(id, name, description, includedRoleIds, newScopes);
    }
}
