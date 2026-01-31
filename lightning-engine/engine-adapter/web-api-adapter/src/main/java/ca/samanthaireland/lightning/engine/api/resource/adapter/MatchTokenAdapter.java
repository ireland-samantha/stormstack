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

package ca.samanthaireland.lightning.engine.api.resource.adapter;

import ca.samanthaireland.lightning.engine.api.resource.adapter.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * REST API adapter for match token operations.
 *
 * <p>Match tokens authorize players to connect to specific matches and
 * perform match-specific operations like submitting commands and viewing snapshots.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * MatchTokenAdapter tokens = new MatchTokenAdapter.HttpMatchTokenAdapter(
 *     "http://localhost:8082",
 *     AdapterConfig.defaults().withBearerToken(adminToken)
 * );
 *
 * // Issue a token for a player
 * MatchToken token = tokens.issueToken(
 *     new IssueTokenRequest("match-123", "container-456", "player-789", null, "PlayerName", null, 8)
 * );
 *
 * // Validate a token
 * MatchToken validated = tokens.validateToken(token.token());
 * }</pre>
 */
public interface MatchTokenAdapter {

    /**
     * Issue a new match token for a player.
     *
     * @param request the token issuance request
     * @return the issued token including the JWT (only returned at issuance time)
     * @throws IOException if the request fails
     */
    MatchToken issueToken(IssueTokenRequest request) throws IOException;

    /**
     * Validate a match token.
     *
     * @param jwtToken the JWT token string
     * @return the validated token details
     * @throws IOException if validation fails
     * @throws MatchTokenException if the token is invalid, expired, or revoked
     */
    MatchToken validateToken(String jwtToken) throws IOException;

    /**
     * Validate a match token for a specific match.
     *
     * @param jwtToken the JWT token string
     * @param matchId  the expected match ID
     * @return the validated token details
     * @throws IOException if validation fails
     * @throws MatchTokenException if the token is invalid or not valid for the match
     */
    MatchToken validateTokenForMatch(String jwtToken, String matchId) throws IOException;

    /**
     * Validate a match token for a specific match and container.
     *
     * @param jwtToken    the JWT token string
     * @param matchId     the expected match ID
     * @param containerId the expected container ID
     * @return the validated token details
     * @throws IOException if validation fails
     * @throws MatchTokenException if the token is invalid or not valid for the match/container
     */
    MatchToken validateTokenForMatchAndContainer(String jwtToken, String matchId, String containerId) throws IOException;

    /**
     * Get a match token by ID.
     *
     * @param tokenId the token ID
     * @return the token details
     * @throws IOException if the request fails
     * @throws MatchTokenException if the token is not found
     */
    MatchToken getById(String tokenId) throws IOException;

    /**
     * List all tokens for a match.
     *
     * @param matchId the match ID
     * @return list of tokens
     * @throws IOException if the request fails
     */
    List<MatchToken> listByMatchId(String matchId) throws IOException;

    /**
     * List active tokens for a match.
     *
     * @param matchId the match ID
     * @return list of active tokens
     * @throws IOException if the request fails
     */
    List<MatchToken> listActiveByMatchId(String matchId) throws IOException;

    /**
     * Revoke a match token.
     *
     * @param tokenId the token ID to revoke
     * @throws IOException if the request fails
     */
    void revokeToken(String tokenId) throws IOException;

    /**
     * Revoke all tokens for a player in a match.
     *
     * @param matchId  the match ID
     * @param playerId the player ID
     * @return the number of tokens revoked
     * @throws IOException if the request fails
     */
    long revokeTokensForPlayer(String matchId, String playerId) throws IOException;

    /**
     * Revoke all tokens for a match.
     *
     * @param matchId the match ID
     * @return the number of tokens revoked
     * @throws IOException if the request fails
     */
    long revokeTokensForMatch(String matchId) throws IOException;

    /**
     * Get count of active tokens for a match.
     *
     * @param matchId the match ID
     * @return the count of active tokens
     * @throws IOException if the request fails
     */
    long countActiveByMatchId(String matchId) throws IOException;

    /**
     * Request to issue a match token.
     *
     * @param matchId       the match ID
     * @param containerId   the container ID (nullable)
     * @param playerId      the player ID
     * @param userId        the auth user ID (nullable)
     * @param playerName    the player's display name
     * @param scopes        permission scopes (nullable, defaults will be used)
     * @param validForHours how long the token is valid in hours
     */
    record IssueTokenRequest(
            String matchId,
            String containerId,
            String playerId,
            String userId,
            String playerName,
            Set<String> scopes,
            int validForHours
    ) {}

    /**
     * Match token response.
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
     * @param token       the JWT token (only present at issuance time)
     */
    record MatchToken(
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
            boolean active,
            String token
    ) {}

    /**
     * Exception thrown when match token operations fail.
     */
    class MatchTokenException extends IOException {
        private final String errorCode;

        public MatchTokenException(String message, String errorCode) {
            super(message);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }

    /**
     * HTTP-based implementation of MatchTokenAdapter.
     */
    class HttpMatchTokenAdapter implements MatchTokenAdapter {
        private final HttpClient httpClient;
        private final String baseUrl;
        private final AdapterConfig config;

        public HttpMatchTokenAdapter(String baseUrl) {
            this(baseUrl, AdapterConfig.defaults());
        }

        public HttpMatchTokenAdapter(String baseUrl, AdapterConfig config) {
            this.baseUrl = normalizeUrl(baseUrl);
            this.config = config;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(config.getConnectTimeout())
                    .build();
        }

        public HttpMatchTokenAdapter(String baseUrl, HttpClient httpClient) {
            this.baseUrl = normalizeUrl(baseUrl);
            this.config = AdapterConfig.defaults();
            this.httpClient = httpClient;
        }

        private static String normalizeUrl(String url) {
            return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        }

        private HttpRequest.Builder requestBuilder(String path) {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path));
            if (config.hasAuthentication()) {
                builder.header("Authorization", "Bearer " + config.getBearerToken());
            }
            return builder;
        }

        @Override
        public MatchToken issueToken(IssueTokenRequest request) throws IOException {
            StringBuilder json = new StringBuilder();
            json.append("{\"matchId\":\"").append(escapeJson(request.matchId())).append("\"");
            if (request.containerId() != null) {
                json.append(",\"containerId\":\"").append(escapeJson(request.containerId())).append("\"");
            }
            json.append(",\"playerId\":\"").append(escapeJson(request.playerId())).append("\"");
            if (request.userId() != null) {
                json.append(",\"userId\":\"").append(escapeJson(request.userId())).append("\"");
            }
            json.append(",\"playerName\":\"").append(escapeJson(request.playerName())).append("\"");
            if (request.scopes() != null && !request.scopes().isEmpty()) {
                json.append(",\"scopes\":[");
                boolean first = true;
                for (String scope : request.scopes()) {
                    if (!first) json.append(",");
                    json.append("\"").append(escapeJson(scope)).append("\"");
                    first = false;
                }
                json.append("]");
            }
            json.append(",\"validForHours\":").append(request.validForHours());
            json.append("}");

            HttpRequest httpRequest = requestBuilder("/api/match-tokens")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 201) {
                    return parseMatchTokenWithJwt(response.body());
                }
                throw handleErrorResponse(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        @Override
        public MatchToken validateToken(String jwtToken) throws IOException {
            return validateTokenInternal(jwtToken, null, null);
        }

        @Override
        public MatchToken validateTokenForMatch(String jwtToken, String matchId) throws IOException {
            return validateTokenInternal(jwtToken, matchId, null);
        }

        @Override
        public MatchToken validateTokenForMatchAndContainer(String jwtToken, String matchId, String containerId) throws IOException {
            return validateTokenInternal(jwtToken, matchId, containerId);
        }

        private MatchToken validateTokenInternal(String jwtToken, String matchId, String containerId) throws IOException {
            StringBuilder json = new StringBuilder();
            json.append("{\"token\":\"").append(escapeJson(jwtToken)).append("\"");
            if (matchId != null) {
                json.append(",\"matchId\":\"").append(escapeJson(matchId)).append("\"");
            }
            if (containerId != null) {
                json.append(",\"containerId\":\"").append(escapeJson(containerId)).append("\"");
            }
            json.append("}");

            HttpRequest request = requestBuilder("/api/match-tokens/validate")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return parseMatchToken(response.body());
                }
                throw handleErrorResponse(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        @Override
        public MatchToken getById(String tokenId) throws IOException {
            HttpRequest request = requestBuilder("/api/match-tokens/" + encodePathSegment(tokenId))
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return parseMatchToken(response.body());
                }
                throw handleErrorResponse(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        @Override
        public List<MatchToken> listByMatchId(String matchId) throws IOException {
            HttpRequest request = requestBuilder("/api/match-tokens/match/" + encodePathSegment(matchId))
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return parseMatchTokenList(response.body());
                }
                throw handleErrorResponse(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        @Override
        public List<MatchToken> listActiveByMatchId(String matchId) throws IOException {
            HttpRequest request = requestBuilder("/api/match-tokens/match/" + encodePathSegment(matchId) + "/active")
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return parseMatchTokenList(response.body());
                }
                throw handleErrorResponse(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        @Override
        public void revokeToken(String tokenId) throws IOException {
            HttpRequest request = requestBuilder("/api/match-tokens/" + encodePathSegment(tokenId))
                    .DELETE()
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 204) {
                    throw handleErrorResponse(response);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        @Override
        public long revokeTokensForPlayer(String matchId, String playerId) throws IOException {
            HttpRequest request = requestBuilder("/api/match-tokens/match/" + encodePathSegment(matchId) + "/player/" + encodePathSegment(playerId))
                    .DELETE()
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return JsonMapper.extractLong(response.body(), "revokedCount");
                }
                throw handleErrorResponse(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        @Override
        public long revokeTokensForMatch(String matchId) throws IOException {
            HttpRequest request = requestBuilder("/api/match-tokens/match/" + encodePathSegment(matchId))
                    .DELETE()
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return JsonMapper.extractLong(response.body(), "revokedCount");
                }
                throw handleErrorResponse(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        @Override
        public long countActiveByMatchId(String matchId) throws IOException {
            HttpRequest request = requestBuilder("/api/match-tokens/match/" + encodePathSegment(matchId) + "/count")
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return JsonMapper.extractLong(response.body(), "activeCount");
                }
                throw handleErrorResponse(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        private MatchToken parseMatchToken(String json) {
            String id = JsonMapper.extractString(json, "id");
            String matchId = JsonMapper.extractString(json, "matchId");
            String containerId = JsonMapper.extractString(json, "containerId");
            String playerId = JsonMapper.extractString(json, "playerId");
            String userId = JsonMapper.extractString(json, "userId");
            String playerName = JsonMapper.extractString(json, "playerName");
            Set<String> scopes = JsonMapper.extractStringSet(json, "scopes");
            Instant createdAt = parseInstant(JsonMapper.extractString(json, "createdAt"));
            Instant expiresAt = parseInstant(JsonMapper.extractString(json, "expiresAt"));
            Instant revokedAt = parseInstant(JsonMapper.extractString(json, "revokedAt"));
            boolean active = JsonMapper.extractBoolean(json, "active");
            return new MatchToken(id, matchId, containerId, playerId, userId, playerName, scopes, createdAt, expiresAt, revokedAt, active, null);
        }

        private MatchToken parseMatchTokenWithJwt(String json) {
            MatchToken base = parseMatchToken(json);
            String token = JsonMapper.extractString(json, "token");
            return new MatchToken(
                    base.id(), base.matchId(), base.containerId(), base.playerId(),
                    base.userId(), base.playerName(), base.scopes(),
                    base.createdAt(), base.expiresAt(), base.revokedAt(), base.active(), token
            );
        }

        private List<MatchToken> parseMatchTokenList(String json) {
            return JsonMapper.extractArray(json, this::parseMatchToken);
        }

        private Instant parseInstant(String value) {
            return value != null ? Instant.parse(value) : null;
        }

        private IOException handleErrorResponse(HttpResponse<String> response) {
            String code = JsonMapper.extractString(response.body(), "code");
            String message = JsonMapper.extractString(response.body(), "message");
            if (message == null) {
                message = "Request failed with status: " + response.statusCode();
            }
            return new MatchTokenException(message, code != null ? code : "UNKNOWN");
        }

        private String escapeJson(String value) {
            return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }

        private String encodePathSegment(String segment) {
            return java.net.URLEncoder.encode(segment, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
