/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.stormstack.thunder.auth.provider.dto;

import ca.samanthaireland.stormstack.thunder.auth.model.MatchToken;

import java.time.Instant;
import java.util.Set;

/**
 * Response DTO for a newly issued match token.
 *
 * <p>This response includes the JWT token which is only available at issuance time.
 *
 * @param id          the token ID
 * @param matchId     the match ID
 * @param containerId the container ID (nullable)
 * @param playerId    the player ID
 * @param userId      the auth user ID (nullable)
 * @param playerName  the player's display name
 * @param scopes      the permission scopes
 * @param createdAt   when created
 * @param expiresAt   when expires
 * @param token       the JWT token (only returned at issuance!)
 */
public record IssueMatchTokenResponse(
        String id,
        String matchId,
        String containerId,
        String playerId,
        String userId,
        String playerName,
        Set<String> scopes,
        Instant createdAt,
        Instant expiresAt,
        String token
) {
    public static IssueMatchTokenResponse from(MatchToken matchToken) {
        return new IssueMatchTokenResponse(
                matchToken.id().toString(),
                matchToken.matchId(),
                matchToken.containerId(),
                matchToken.playerId(),
                matchToken.userId() != null ? matchToken.userId().toString() : null,
                matchToken.playerName(),
                matchToken.scopes(),
                matchToken.createdAt(),
                matchToken.expiresAt(),
                matchToken.jwtToken()
        );
    }
}
