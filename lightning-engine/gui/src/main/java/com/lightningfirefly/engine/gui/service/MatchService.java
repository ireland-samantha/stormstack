package com.lightningfirefly.engine.gui.service;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for managing matches via REST API.
 */
@Slf4j
public class MatchService {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final List<Consumer<MatchEvent>> listeners = new CopyOnWriteArrayList<>();

    public MatchService(String serverUrl) {
        this.baseUrl = serverUrl + "/api/matches";
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * Add a listener for match events.
     */
    public void addListener(Consumer<MatchEvent> listener) {
        listeners.add(listener);
    }

    /**
     * Get all matches.
     */
    public CompletableFuture<List<MatchInfo>> listMatches() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .GET()
                .header("Accept", "application/json")
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return parseMatchList(response.body());
                    } else {
                        log.error("Failed to list matches: {} {}", response.statusCode(), response.body());
                        notifyListeners(new MatchEvent(MatchEventType.ERROR, 0,
                                "Failed to list matches: " + response.statusCode()));
                        return new ArrayList<MatchInfo>();
                    }
                })
                .exceptionally(e -> {
                    log.error("Failed to list matches", e);
                    notifyListeners(new MatchEvent(MatchEventType.ERROR, 0, e.getMessage()));
                    return new ArrayList<MatchInfo>();
                });
    }

    /**
     * Get a specific match by ID.
     */
    public CompletableFuture<MatchInfo> getMatch(long matchId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/" + matchId))
                .GET()
                .header("Accept", "application/json")
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return parseMatch(response.body());
                    } else {
                        log.error("Failed to get match {}: {} {}", matchId, response.statusCode(), response.body());
                        return null;
                    }
                })
                .exceptionally(e -> {
                    log.error("Failed to get match {}", matchId, e);
                    return null;
                });
    }

    /**
     * Create a new match. Match ID is generated server-side.
     *
     * @param enabledModules list of module names to enable
     * @return the server-generated match ID
     */
    public CompletableFuture<Long> createMatch(List<String> enabledModules) {
        return createMatch(enabledModules, List.of());
    }

    /**
     * Create a new match with modules and game masters. Match ID is generated server-side.
     *
     * @param enabledModules list of module names to enable
     * @param enabledGameMasters list of game master names to enable
     * @return the server-generated match ID
     */
    public CompletableFuture<Long> createMatch(List<String> enabledModules, List<String> enabledGameMasters) {
        String modulesJson = enabledModules.stream()
                .map(m -> "\"" + m + "\"")
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        String gameMastersJson = enabledGameMasters.stream()
                .map(g -> "\"" + g + "\"")
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        String body = String.format("{\"enabledModuleNames\":[%s],\"enabledGameMasterNames\":[%s]}",
                modulesJson, gameMastersJson);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 201) {
                        // Parse the generated ID from the response
                        MatchInfo createdMatch = parseMatch(response.body());
                        long matchId = createdMatch != null ? createdMatch.id() : -1;
                        log.info("Created match {}", matchId);
                        notifyListeners(new MatchEvent(MatchEventType.CREATED, matchId,
                                "Match " + matchId + " created"));
                        return matchId;
                    } else {
                        log.error("Failed to create match: {} {}", response.statusCode(), response.body());
                        notifyListeners(new MatchEvent(MatchEventType.ERROR, 0,
                                "Failed to create match: " + response.statusCode()));
                        return -1L;
                    }
                })
                .exceptionally(e -> {
                    log.error("Failed to create match", e);
                    notifyListeners(new MatchEvent(MatchEventType.ERROR, 0, e.getMessage()));
                    return -1L;
                });
    }

    /**
     * Delete a match.
     */
    public CompletableFuture<Boolean> deleteMatch(long matchId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/" + matchId))
                .DELETE()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 204) {
                        log.info("Deleted match {}", matchId);
                        notifyListeners(new MatchEvent(MatchEventType.DELETED, matchId,
                                "Match " + matchId + " deleted"));
                        return true;
                    } else {
                        log.error("Failed to delete match {}: {}", matchId, response.statusCode());
                        notifyListeners(new MatchEvent(MatchEventType.ERROR, matchId,
                                "Failed to delete match: " + response.statusCode()));
                        return false;
                    }
                })
                .exceptionally(e -> {
                    log.error("Failed to delete match {}", matchId, e);
                    notifyListeners(new MatchEvent(MatchEventType.ERROR, matchId, e.getMessage()));
                    return false;
                });
    }

    private void notifyListeners(MatchEvent event) {
        for (Consumer<MatchEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.error("Error in match event listener", e);
            }
        }
    }

    private List<MatchInfo> parseMatchList(String json) {
        List<MatchInfo> matches = new ArrayList<>();
        // Simple JSON parsing for arrays - handles both old format (modules only) and new format (modules + game masters)
        Pattern pattern = Pattern.compile("\\{\"id\":(\\d+),\"enabledModuleNames\":\\[(.*?)\\](?:,\"enabledGameMasterNames\":\\[(.*?)\\])?\\}");
        Matcher matcher = pattern.matcher(json);
        while (matcher.find()) {
            long id = Long.parseLong(matcher.group(1));
            List<String> modules = parseModuleList(matcher.group(2));
            List<String> gameMasters = matcher.group(3) != null ? parseModuleList(matcher.group(3)) : new ArrayList<>();
            matches.add(new MatchInfo(id, modules, gameMasters));
        }
        return matches;
    }

    private MatchInfo parseMatch(String json) {
        Pattern pattern = Pattern.compile("\\{\"id\":(\\d+),\"enabledModuleNames\":\\[(.*?)\\](?:,\"enabledGameMasterNames\":\\[(.*?)\\])?\\}");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            long id = Long.parseLong(matcher.group(1));
            List<String> modules = parseModuleList(matcher.group(2));
            List<String> gameMasters = matcher.group(3) != null ? parseModuleList(matcher.group(3)) : new ArrayList<>();
            return new MatchInfo(id, modules, gameMasters);
        }
        return null;
    }

    private List<String> parseModuleList(String modulesJson) {
        List<String> modules = new ArrayList<>();
        Pattern pattern = Pattern.compile("\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(modulesJson);
        while (matcher.find()) {
            modules.add(matcher.group(1));
        }
        return modules;
    }

    /**
     * Shutdown the service.
     */
    public void shutdown() {
        // HttpClient doesn't need explicit shutdown
    }

    /**
     * Match information record.
     */
    public record MatchInfo(long id, List<String> enabledModules, List<String> enabledGameMasters) {}

    /**
     * Match event types.
     */
    public enum MatchEventType {
        CREATED, DELETED, ERROR
    }

    /**
     * Match event record.
     */
    public record MatchEvent(MatchEventType type, long matchId, String message) {}
}
