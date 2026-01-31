/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.lightning.auth.provider.dto;

import ca.samanthaireland.lightning.auth.model.ApiToken;

import java.time.Instant;
import java.util.Set;

/**
 * Response DTO for a newly created API token.
 *
 * <p>This response includes the plaintext token which is only available at creation time.
 *
 * @param id             the token ID
 * @param userId         the owner user ID
 * @param name           the token name
 * @param scopes         the permission scopes
 * @param createdAt      when created
 * @param expiresAt      when expires (null for never)
 * @param plaintextToken the plaintext token value (only returned once!)
 */
public record CreateApiTokenResponse(
        String id,
        String userId,
        String name,
        Set<String> scopes,
        Instant createdAt,
        Instant expiresAt,
        String plaintextToken
) {
    public static CreateApiTokenResponse from(ApiToken token, String plaintextToken) {
        return new CreateApiTokenResponse(
                token.id().toString(),
                token.userId().toString(),
                token.name(),
                token.scopes(),
                token.createdAt(),
                token.expiresAt(),
                plaintextToken
        );
    }
}
