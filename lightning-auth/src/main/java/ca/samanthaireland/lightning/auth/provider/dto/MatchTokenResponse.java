/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.lightning.auth.provider.dto;

import ca.samanthaireland.lightning.auth.model.MatchToken;

import java.time.Instant;
import java.util.Set;

/**
 * Response DTO for a match token.
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
 * @param revokedAt   when revoked (null if active)
 * @param active      whether the token is currently active
 */
public record MatchTokenResponse(
        String id,
        String matchId,
        String containerId,
        String playerId,
        String userId,
        String playerName,
        Set<String> scopes,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        boolean active
) {
    public static MatchTokenResponse from(MatchToken token) {
        return new MatchTokenResponse(
                token.id().toString(),
                token.matchId(),
                token.containerId(),
                token.playerId(),
                token.userId() != null ? token.userId().toString() : null,
                token.playerName(),
                token.scopes(),
                token.createdAt(),
                token.expiresAt(),
                token.revokedAt(),
                token.isActive()
        );
    }
}
