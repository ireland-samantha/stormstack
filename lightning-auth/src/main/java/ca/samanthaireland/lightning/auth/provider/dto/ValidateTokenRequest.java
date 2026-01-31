/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.lightning.auth.provider.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for validating a JWT token.
 *
 * @param token the JWT token to validate
 */
public record ValidateTokenRequest(
        @NotBlank(message = "Token is required")
        String token
) {}
