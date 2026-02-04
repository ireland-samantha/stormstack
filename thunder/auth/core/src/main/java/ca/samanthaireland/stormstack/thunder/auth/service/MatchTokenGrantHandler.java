/*
 * Copyright (c) 2026 Samantha Ireland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ca.samanthaireland.stormstack.thunder.auth.service;

import ca.samanthaireland.stormstack.thunder.auth.exception.AuthException;
import ca.samanthaireland.stormstack.thunder.auth.model.GrantType;
import ca.samanthaireland.stormstack.thunder.auth.model.MatchToken;
import ca.samanthaireland.stormstack.thunder.auth.model.OAuth2TokenResponse;
import ca.samanthaireland.stormstack.thunder.auth.model.ServiceClient;
import ca.samanthaireland.stormstack.thunder.auth.model.UserId;
import ca.samanthaireland.stormstack.thunder.auth.service.dto.IssueMatchTokenRequest;
import ca.samanthaireland.stormstack.thunder.auth.util.SimpleJsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Handles the match_token grant type for issuing player match tokens.
 *
 * <p>This grant is used to issue JWT tokens for players to connect to
 * specific matches. It replaces the legacy /api/match-tokens endpoint.
 *
 * <p>Request parameters:
 * <ul>
 *   <li>grant_type=urn:stormstack:grant-type:match-token (required)</li>
 *   <li>match_id (required) - the match ID</li>
 *   <li>player_id (required) - the player ID</li>
 *   <li>player_name (required) - the player's display name</li>
 *   <li>container_id (optional) - the container ID</li>
 *   <li>user_id (optional) - the auth user ID</li>
 *   <li>scopes (optional) - JSON array of scope strings</li>
 *   <li>valid_for_hours (optional) - token validity in hours, defaults to 8</li>
 * </ul>
 *
 * <p>Requires scope: {@code service.match-token.issue}
 */
public class MatchTokenGrantHandler implements OAuth2GrantHandler {

    private static final Logger log = LoggerFactory.getLogger(MatchTokenGrantHandler.class);

    private static final String PARAM_MATCH_ID = "match_id";
    private static final String PARAM_CONTAINER_ID = "container_id";
    private static final String PARAM_PLAYER_ID = "player_id";
    private static final String PARAM_USER_ID = "user_id";
    private static final String PARAM_PLAYER_NAME = "player_name";
    private static final String PARAM_SCOPES = "scopes";
    private static final String PARAM_VALID_FOR_HOURS = "valid_for_hours";

    private static final String REQUIRED_SCOPE = "service.match-token.issue";
    private static final int DEFAULT_VALID_FOR_HOURS = 8;

    private final MatchTokenService matchTokenService;

    public MatchTokenGrantHandler(MatchTokenService matchTokenService) {
        this.matchTokenService = matchTokenService;
    }

    @Override
    public GrantType getGrantType() {
        return GrantType.MATCH_TOKEN;
    }

    @Override
    public OAuth2TokenResponse handle(ServiceClient client, Map<String, String> parameters) {
        if (client == null) {
            log.error("match_token grant requires authenticated client");
            throw AuthException.invalidClient("Client authentication required");
        }

        // Check required scope
        if (!client.allowedScopes().contains(REQUIRED_SCOPE) && !client.allowedScopes().contains("*")) {
            log.warn("Client {} lacks required scope: {}", client.clientId(), REQUIRED_SCOPE);
            throw AuthException.invalidScope("Client not authorized for scope: " + REQUIRED_SCOPE);
        }

        String matchId = parameters.get(PARAM_MATCH_ID);
        String containerId = parameters.get(PARAM_CONTAINER_ID);
        String playerId = parameters.get(PARAM_PLAYER_ID);
        String userIdStr = parameters.get(PARAM_USER_ID);
        String playerName = parameters.get(PARAM_PLAYER_NAME);
        String scopesJson = parameters.get(PARAM_SCOPES);
        String validForHoursStr = parameters.get(PARAM_VALID_FOR_HOURS);

        UserId userId = userIdStr != null && !userIdStr.isBlank() ? UserId.fromString(userIdStr) : null;
        Set<String> scopes = parseScopesJson(scopesJson);
        int validForHours = parseValidForHours(validForHoursStr);

        // Build the request
        IssueMatchTokenRequest request = new IssueMatchTokenRequest(
                matchId,
                containerId,
                playerId,
                userId,
                playerName,
                scopes,
                Duration.ofHours(validForHours)
        );

        // Issue the token
        MatchToken token = matchTokenService.issueToken(request);

        log.info("Issued match token {} for player {} in match {} via client: {}",
                token.id(), playerId, matchId, client.clientId());

        // Calculate expires_in in seconds
        int expiresIn = validForHours * 60 * 60;

        // Return OAuth2-style response with the match token JWT as access_token
        return new OAuth2TokenResponse(
                token.jwtToken(),
                OAuth2TokenResponse.TOKEN_TYPE_BEARER,
                expiresIn,
                null,  // No refresh token for match tokens
                String.join(" ", token.scopes())
        );
    }

    @Override
    public void validateRequest(Map<String, String> parameters) {
        if (!parameters.containsKey(PARAM_MATCH_ID) || parameters.get(PARAM_MATCH_ID).isBlank()) {
            throw AuthException.invalidRequest("Missing required parameter: " + PARAM_MATCH_ID);
        }

        if (!parameters.containsKey(PARAM_PLAYER_ID) || parameters.get(PARAM_PLAYER_ID).isBlank()) {
            throw AuthException.invalidRequest("Missing required parameter: " + PARAM_PLAYER_ID);
        }

        if (!parameters.containsKey(PARAM_PLAYER_NAME) || parameters.get(PARAM_PLAYER_NAME).isBlank()) {
            throw AuthException.invalidRequest("Missing required parameter: " + PARAM_PLAYER_NAME);
        }

        // Validate valid_for_hours if provided
        String validForHoursStr = parameters.get(PARAM_VALID_FOR_HOURS);
        if (validForHoursStr != null && !validForHoursStr.isBlank()) {
            try {
                int hours = Integer.parseInt(validForHoursStr);
                if (hours <= 0 || hours > 168) {  // Max 7 days
                    throw AuthException.invalidRequest(
                            PARAM_VALID_FOR_HOURS + " must be between 1 and 168 hours");
                }
            } catch (NumberFormatException e) {
                throw AuthException.invalidRequest(
                        PARAM_VALID_FOR_HOURS + " must be a valid integer");
            }
        }

        // Validate scopes JSON if provided
        String scopesJson = parameters.get(PARAM_SCOPES);
        if (scopesJson != null && !scopesJson.isBlank()) {
            try {
                parseScopesJson(scopesJson);
            } catch (IllegalArgumentException e) {
                throw AuthException.invalidRequest("Invalid " + PARAM_SCOPES + ": " + e.getMessage());
            }
        }
    }

    private Set<String> parseScopesJson(String json) {
        if (json == null || json.isBlank()) {
            return null;  // Use defaults
        }

        try {
            return SimpleJsonParser.parseStringArray(json);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid JSON array: " + e.getMessage(), e);
        }
    }

    private int parseValidForHours(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_VALID_FOR_HOURS;
        }
        return Integer.parseInt(value);
    }
}
