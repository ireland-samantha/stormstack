/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.stormstack.thunder.auth.provider.dto;

import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Request DTO for updating a role.
 *
 * @param description     the new description (optional)
 * @param includedRoleIds the new included role IDs (optional)
 * @param scopes          the new permission scopes (optional)
 */
public record UpdateRoleRequest(
        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,

        Set<String> includedRoleIds,

        Set<String> scopes
) {}
