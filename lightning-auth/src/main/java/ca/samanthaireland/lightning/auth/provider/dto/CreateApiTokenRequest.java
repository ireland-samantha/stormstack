/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.lightning.auth.provider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;

/**
 * Request DTO for creating an API token.
 *
 * @param name      the token name
 * @param scopes    the permission scopes
 * @param expiresAt when the token should expire (null for never)
 */
public record CreateApiTokenRequest(
        @NotBlank(message = "Token name is required")
        @Size(min = 2, max = 100, message = "Token name must be between 2 and 100 characters")
        String name,

        Set<String> scopes,

        Instant expiresAt
) {
    public CreateApiTokenRequest {
        if (scopes == null) {
            scopes = Set.of();
        }
    }
}
