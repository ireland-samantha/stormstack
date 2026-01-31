/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.lightning.auth.spring.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

/**
 * OAuth2 token response DTO matching RFC 6749 format.
 *
 * @param accessToken  the issued access token (JWT)
 * @param tokenType    token type (always "Bearer")
 * @param expiresIn    lifetime in seconds
 * @param scope        space-separated scope string
 * @param issuedTokenType for token exchange responses (RFC 8693)
 */
public record OAuth2TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") int expiresIn,
        @JsonProperty("scope") String scope,
        @JsonProperty("issued_token_type") String issuedTokenType
) {
    /**
     * Parse the scope string into a set of individual scopes.
     *
     * @return set of scopes, or empty set if scope is null/empty
     */
    public Set<String> scopeSet() {
        if (scope == null || scope.isBlank()) {
            return Set.of();
        }
        return Set.of(scope.split("\\s+"));
    }
}
