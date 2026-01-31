/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.lightning.auth.provider.dto;

import ca.samanthaireland.lightning.auth.model.ApiToken;

import java.time.Instant;
import java.util.Set;

/**
 * Response DTO for an API token.
 *
 * @param id         the token ID
 * @param userId     the owner user ID
 * @param name       the token name
 * @param scopes     the permission scopes
 * @param createdAt  when created
 * @param expiresAt  when expires (null for never)
 * @param revokedAt  when revoked (null if active)
 * @param lastUsedAt when last used
 * @param lastUsedIp IP of last use
 * @param active     whether the token is currently active
 */
public record ApiTokenResponse(
        String id,
        String userId,
        String name,
        Set<String> scopes,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        Instant lastUsedAt,
        String lastUsedIp,
        boolean active
) {
    public static ApiTokenResponse from(ApiToken token) {
        return new ApiTokenResponse(
                token.id().toString(),
                token.userId().toString(),
                token.name(),
                token.scopes(),
                token.createdAt(),
                token.expiresAt(),
                token.revokedAt(),
                token.lastUsedAt(),
                token.lastUsedIp(),
                token.isActive()
        );
    }
}
