/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.stormstack.thunder.auth.provider.dto;

import ca.samanthaireland.stormstack.thunder.auth.model.Role;
import ca.samanthaireland.stormstack.thunder.auth.model.RoleId;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Response DTO for a role.
 *
 * @param id              the role ID
 * @param name            the role name
 * @param description     the role description
 * @param includedRoleIds the IDs of inherited roles
 * @param scopes          the permission scopes granted by this role
 */
public record RoleResponse(
        String id,
        String name,
        String description,
        Set<String> includedRoleIds,
        Set<String> scopes
) {
    public static RoleResponse from(Role role) {
        return new RoleResponse(
                role.id().toString(),
                role.name(),
                role.description(),
                role.includedRoleIds().stream().map(RoleId::toString).collect(Collectors.toSet()),
                role.scopes()
        );
    }
}
