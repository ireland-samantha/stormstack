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
 * REST API adapter for match operations.
 */
public interface MatchAdapter {

    /**
     * Create a new match. Match ID is generated server-side.
     *
     * @param enabledModules list of enabled module names
     * @return the created match response with server-generated ID
     */
    MatchResponse createMatch(List<String> enabledModules) throws IOException;

    /**
     * Get a match by ID.
     *
     * @param matchId the match ID
     * @return the match if found
     */
    Optional<MatchResponse> getMatch(long matchId) throws IOException;

    /**
     * Get all matches.
     *
     * @return list of all matches
     */
    List<MatchResponse> getAllMatches() throws IOException;

    /**
     * Delete a match.
     *
     * @param matchId the match ID
     * @return true if deleted
     */
    boolean deleteMatch(long matchId) throws IOException;

    /**
     * Match response DTO.
     */
    record MatchResponse(long id, List<String> enabledModules) {}

    /**
     * HTTP-based implementation.
     */
    class HttpMatchAdapter implements MatchAdapter {
        private final HttpClient httpClient;
        private final String baseUrl;

        public HttpMatchAdapter(String baseUrl) {
            this(baseUrl, AdapterConfig.defaults());
        }

        public HttpMatchAdapter(String baseUrl, AdapterConfig config) {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(config.getConnectTimeout())
                    .build();
        }

        public HttpMatchAdapter(String baseUrl, HttpClient httpClient) {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            this.httpClient = httpClient;
        }

        @Override
        public MatchResponse createMatch(List<String> enabledModules) throws IOException {
            // Match ID is generated server-side
            String modulesJson = "[" + String.join(",", enabledModules.stream().map(m -> "\"" + m + "\"").toList()) + "]";
            String json = "{\"enabledModuleNames\":" + modulesJson + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/matches"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 201) {
                    return parseMatchResponse(response.body());
                }
                throw new IOException("Create match failed with status: " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        @Override
        public Optional<MatchResponse> getMatch(long matchId) throws IOException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/matches/" + matchId))
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return Optional.of(parseMatchResponse(response.body()));
                } else if (response.statusCode() == 404) {
                    return Optional.empty();
                }
                throw new IOException("Get match failed with status: " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        @Override
        public List<MatchResponse> getAllMatches() throws IOException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/matches"))
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    // Simplified - would parse JSON array in real implementation
                    return List.of();
                }
                throw new IOException("Get all matches failed with status: " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        @Override
        public boolean deleteMatch(long matchId) throws IOException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/matches/" + matchId))
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

        private MatchResponse parseMatchResponse(String json) {
            // Simplified JSON parsing - production code should use Jackson
            int idStart = json.indexOf("\"id\":") + 5;
            int idEnd = json.indexOf(",", idStart);
            if (idEnd == -1) idEnd = json.indexOf("}", idStart);
            long id = Long.parseLong(json.substring(idStart, idEnd).trim());
            return new MatchResponse(id, List.of());
        }
    }
}
