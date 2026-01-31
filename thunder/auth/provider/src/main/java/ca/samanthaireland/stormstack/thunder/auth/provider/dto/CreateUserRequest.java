/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.stormstack.thunder.auth.provider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Request DTO for creating a user.
 *
 * @param username the username
 * @param password the password
 * @param roleIds  the role IDs to assign
 * @param scopes   the permission scopes (format: service.resource.operation)
 */
public record CreateUserRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        String username,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        String password,

        Set<String> roleIds,

        Set<String> scopes
) {
    public CreateUserRequest {
        if (roleIds == null) {
            roleIds = Set.of();
        }
        if (scopes == null) {
            scopes = Set.of();
        }
    }
}
