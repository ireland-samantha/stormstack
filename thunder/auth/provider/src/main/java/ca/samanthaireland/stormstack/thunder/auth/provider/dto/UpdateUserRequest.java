/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.stormstack.thunder.auth.provider.dto;

import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Request DTO for updating a user.
 *
 * @param password the new password (optional)
 * @param roleIds  the new role IDs (optional)
 * @param scopes   the permission scopes (optional, format: service.resource.operation)
 * @param enabled  the enabled status (optional)
 */
public record UpdateUserRequest(
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        String password,

        Set<String> roleIds,

        Set<String> scopes,

        Boolean enabled
) {}
