/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.stormstack.thunder.auth.provider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Request DTO for creating a role.
 *
 * @param name            the role name
 * @param description     the role description
 * @param includedRoleIds the IDs of roles to inherit
 * @param scopes          the permission scopes to grant
 */
public record CreateRoleRequest(
        @NotBlank(message = "Role name is required")
        @Size(min = 2, max = 50, message = "Role name must be between 2 and 50 characters")
        String name,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,

        Set<String> includedRoleIds,

        Set<String> scopes
) {
    public CreateRoleRequest {
        if (description == null) {
            description = "";
        }
        if (includedRoleIds == null) {
            includedRoleIds = Set.of();
        }
        if (scopes == null) {
            scopes = Set.of();
        }
    }
}
