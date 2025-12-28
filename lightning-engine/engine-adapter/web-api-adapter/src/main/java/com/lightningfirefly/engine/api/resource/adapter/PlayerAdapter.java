package com.lightningfirefly.engine.api.resource.adapter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * REST API adapter for player operations.
 */
public interface PlayerAdapter {

    /**
     * Create a new player.
     *
     * @param playerId the player ID
     * @param playerName the player name
     * @return the created player response
     */
    PlayerResponse createPlayer(long playerId, String playerName) throws IOException;

    /**
     * Get a player by ID.
     *
     * @param playerId the player ID
     * @return the player if found
     */
    Optional<PlayerResponse> getPlayer(long playerId) throws IOException;

    /**
     * Get all players.
     *
     * @return list of all players
     */
    List<PlayerResponse> getAllPlayers() throws IOException;

    /**
     * Delete a player.
     *
     * @param playerId the player ID
     * @return true if deleted
     */
    boolean deletePlayer(long playerId) throws IOException;

    /**
     * Join a player to a match.
     *
     * @param playerId the player ID
     * @param matchId the match ID
     * @return the player match response
     */
    PlayerMatchResponse joinMatch(long playerId, long matchId) throws IOException;

    /**
     * Leave a match.
     *
     * @param playerId the player ID
     * @param matchId the match ID
     * @return true if left successfully
     */
    boolean leaveMatch(long playerId, long matchId) throws IOException;

    /**
     * Player response DTO.
     */
    record PlayerResponse(long id, String name) {}

    /**
     * Player match response DTO.
     */
    record PlayerMatchResponse(long playerId, long matchId) {}

    /**
     * HTTP-based implementation.
     */
    class HttpPlayerAdapter implements PlayerAdapter {
        private final HttpClient httpClient;
        private final String baseUrl;

        public HttpPlayerAdapter(String baseUrl) {
            this(baseUrl, AdapterConfig.defaults());
        }

        public HttpPlayerAdapter(String baseUrl, AdapterConfig config) {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(config.getConnectTimeout())
                    .build();
        }

        public HttpPlayerAdapter(String baseUrl, HttpClient httpClient) {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            this.httpClient = httpClient;
        }

        @Override
        public PlayerResponse createPlayer(long playerId, String playerName) throws IOException {
            String json = "{\"id\":" + playerId + ",\"name\":\"" + playerName + "\"}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/players"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 201) {
                    return parsePlayerResponse(response.body());
                }
                throw new IOException("Create player failed with status: " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        @Override
        public Optional<PlayerResponse> getPlayer(long playerId) throws IOException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/players/" + playerId))
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return Optional.of(parsePlayerResponse(response.body()));
                } else if (response.statusCode() == 404) {
                    return Optional.empty();
                }
                throw new IOException("Get player failed with status: " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        @Override
        public List<PlayerResponse> getAllPlayers() throws IOException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/players"))
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return List.of();
                }
                throw new IOException("Get all players failed with status: " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        @Override
        public boolean deletePlayer(long playerId) throws IOException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/players/" + playerId))
                    .DELETE()
                    .build();

            try {
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                return response.statusCode() == 204;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        @Override
        public PlayerMatchResponse joinMatch(long playerId, long matchId) throws IOException {
            String json = "{\"playerId\":" + playerId + ",\"matchId\":" + matchId + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/player-matches"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 201) {
                    return new PlayerMatchResponse(playerId, matchId);
                }
                throw new IOException("Join match failed with status: " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        @Override
        public boolean leaveMatch(long playerId, long matchId) throws IOException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/player-matches/" + playerId + "/" + matchId))
                    .DELETE()
                    .build();

            try {
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                return response.statusCode() == 204;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        private PlayerResponse parsePlayerResponse(String json) {
            int idStart = json.indexOf("\"id\":") + 5;
            int idEnd = json.indexOf(",", idStart);
            if (idEnd == -1) idEnd = json.indexOf("}", idStart);
            long id = Long.parseLong(json.substring(idStart, idEnd).trim());

            int nameStart = json.indexOf("\"name\":\"") + 8;
            int nameEnd = json.indexOf("\"", nameStart);
            String name = json.substring(nameStart, nameEnd);

            return new PlayerResponse(id, name);
        }
    }
}
