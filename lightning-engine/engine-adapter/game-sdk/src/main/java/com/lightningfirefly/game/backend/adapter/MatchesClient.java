package com.lightningfirefly.game.backend.adapter;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fluent client for match operations.
 *
 * <p>Usage:
 * <pre>{@code
 * // Create a match
 * var match = client.matches().create()
 *     .withModule("SpawnModule")
 *     .withModule("RenderModule")
 *     .withGameMaster("CheckersGameMaster")
 *     .execute();
 *
 * // Get a match
 * var match = client.matches().get(matchId);
 *
 * // List all matches
 * var matches = client.matches().list();
 *
 * // Delete a match
 * client.matches().delete(matchId);
 * }</pre>
 */
@Slf4j
public final class MatchesClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    MatchesClient(String baseUrl, HttpClient httpClient, Duration requestTimeout) {
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
    }

    /**
     * Start creating a new match.
     */
    public CreateMatchBuilder create() {
        return new CreateMatchBuilder(this);
    }

    /**
     * Get a match by ID.
     *
     * @param matchId the match ID
     * @return the match, or empty if not found
     */
    public Optional<Match> get(long matchId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/matches/" + matchId))
                    .timeout(requestTimeout)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return Optional.of(parseMatch(response.body()));
            } else if (response.statusCode() == 404) {
                return Optional.empty();
            }
            throw new IOException("Get match failed: " + response.statusCode());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to get match " + matchId, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted", e);
        }
    }

    /**
     * List all matches.
     */
    public List<Match> list() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/matches"))
                    .timeout(requestTimeout)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return parseMatchList(response.body());
            }
            throw new IOException("List matches failed: " + response.statusCode());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list matches", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted", e);
        }
    }

    /**
     * Delete a match.
     *
     * @param matchId the match ID
     * @return true if deleted
     */
    public boolean delete(long matchId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/matches/" + matchId))
                    .timeout(requestTimeout)
                    .DELETE()
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 204 || response.statusCode() == 200;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete match " + matchId, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted", e);
        }
    }

    Match createMatch(List<String> modules, List<String> gameMasters) {
        try {
            String modulesJson = "[" + String.join(",", modules.stream().map(m -> "\"" + m + "\"").toList()) + "]";
            String gameMastersJson = "[" + String.join(",", gameMasters.stream().map(m -> "\"" + m + "\"").toList()) + "]";
            String json = "{\"enabledModuleNames\":" + modulesJson + ",\"enabledGameMasterNames\":" + gameMastersJson + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/matches"))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 201 || response.statusCode() == 200) {
                return parseMatch(response.body());
            }
            throw new IOException("Create match failed: " + response.statusCode() + " - " + response.body());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create match", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted", e);
        }
    }

    private Match parseMatch(String json) {
        // Parse ID
        int idStart = json.indexOf("\"id\":") + 5;
        int idEnd = json.indexOf(",", idStart);
        if (idEnd == -1) idEnd = json.indexOf("}", idStart);
        long id = Long.parseLong(json.substring(idStart, idEnd).trim());

        // Parse enabled modules (simplified)
        List<String> modules = new ArrayList<>();
        int modulesStart = json.indexOf("\"enabledModules\":");
        if (modulesStart != -1) {
            int arrayStart = json.indexOf("[", modulesStart);
            int arrayEnd = json.indexOf("]", arrayStart);
            if (arrayStart != -1 && arrayEnd != -1) {
                String modulesStr = json.substring(arrayStart + 1, arrayEnd);
                for (String module : modulesStr.split(",")) {
                    String trimmed = module.trim().replace("\"", "");
                    if (!trimmed.isEmpty()) {
                        modules.add(trimmed);
                    }
                }
            }
        }

        return new Match(id, modules);
    }

    private List<Match> parseMatchList(String json) {
        List<Match> matches = new ArrayList<>();
        // Simplified array parsing
        int pos = 0;
        while ((pos = json.indexOf("{", pos)) != -1) {
            int end = json.indexOf("}", pos);
            if (end == -1) break;
            String matchJson = json.substring(pos, end + 1);
            matches.add(parseMatch(matchJson));
            pos = end + 1;
        }
        return matches;
    }

    /**
     * Match data record.
     */
    public record Match(long id, List<String> enabledModules) {}

    /**
     * Fluent builder for creating matches.
     */
    public static class CreateMatchBuilder {
        private final MatchesClient client;
        private final List<String> modules = new ArrayList<>();
        private final List<String> gameMasters = new ArrayList<>();

        CreateMatchBuilder(MatchesClient client) {
            this.client = client;
        }

        /**
         * Add a module to the match.
         */
        public CreateMatchBuilder withModule(String moduleName) {
            modules.add(moduleName);
            return this;
        }

        /**
         * Add multiple modules to the match.
         */
        public CreateMatchBuilder withModules(String... moduleNames) {
            modules.addAll(List.of(moduleNames));
            return this;
        }

        /**
         * Add a game master to the match.
         */
        public CreateMatchBuilder withGameMaster(String gameMasterName) {
            gameMasters.add(gameMasterName);
            return this;
        }

        /**
         * Add multiple game masters to the match.
         */
        public CreateMatchBuilder withGameMasters(String... gameMasterNames) {
            gameMasters.addAll(List.of(gameMasterNames));
            return this;
        }

        /**
         * Execute the match creation.
         */
        public Match execute() {
            return client.createMatch(modules, gameMasters);
        }
    }
}
