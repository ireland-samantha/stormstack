/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.lightning.auth.provider.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for validating a match token.
 *
 * @param token       the JWT token to validate
 * @param matchId     the expected match ID (optional)
 * @param containerId the expected container ID (optional)
 */
public record ValidateMatchTokenRequest(
        @NotBlank(message = "Token is required")
        String token,

        String matchId,

        String containerId
) {}
