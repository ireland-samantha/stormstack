/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.lightning.auth.provider.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Request DTO for issuing a match token.
 *
 * @param matchId       the match ID
 * @param containerId   the container ID (optional)
 * @param playerId      the player ID
 * @param userId        the auth user ID (optional)
 * @param playerName    the player's display name
 * @param scopes        permission scopes (optional, defaults will be used)
 * @param validForHours how long the token is valid in hours
 */
public record IssueMatchTokenRequest(
        @NotBlank(message = "Match ID is required")
        String matchId,

        String containerId,

        @NotBlank(message = "Player ID is required")
        String playerId,

        String userId,

        @NotBlank(message = "Player name is required")
        @Size(min = 1, max = 100, message = "Player name must be between 1 and 100 characters")
        String playerName,

        Set<String> scopes,

        @Min(value = 1, message = "Valid duration must be at least 1 hour")
        Integer validForHours
) {
    public IssueMatchTokenRequest {
        if (validForHours == null) {
            validForHours = 8; // Default 8 hours
        }
    }
}
