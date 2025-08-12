package com.lightningfirefly.engine.gui.service;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for managing game masters via REST API.
 */
@Slf4j
public class GameMasterService {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final List<Consumer<GameMasterEvent>> listeners = new CopyOnWriteArrayList<>();

    public GameMasterService(String serverUrl) {
        this.baseUrl = serverUrl + "/api/gamemasters";
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * Add a listener for game master events.
     */
    public void addListener(Consumer<GameMasterEvent> listener) {
        listeners.add(listener);
    }

    /**
     * Get all available game masters.
     */
    public CompletableFuture<List<GameMasterInfo>> listGameMasters() {
        log.debug("Fetching game masters from: {}", baseUrl);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .GET()
                .header("Accept", "application/json")
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    log.debug("Game masters response: status={}, body={}", response.statusCode(), response.body());
                    if (response.statusCode() == 200) {
                        List<GameMasterInfo> gameMasters = parseGameMasterList(response.body());
                        log.info("Parsed {} game masters from response", gameMasters.size());
                        return gameMasters;
                    } else {
                        log.error("Failed to list game masters: {} {}", response.statusCode(), response.body());
                        notifyListeners(new GameMasterEvent(GameMasterEventType.ERROR, null,
                                "Failed to list game masters: " + response.statusCode()));
                        return new ArrayList<GameMasterInfo>();
                    }
                })
                .exceptionally(e -> {
                    log.error("Failed to list game masters: {}", e.getMessage(), e);
                    notifyListeners(new GameMasterEvent(GameMasterEventType.ERROR, null, e.getMessage()));
                    return new ArrayList<GameMasterInfo>();
                });
    }

    /**
     * Upload a JAR game master.
     */
    public CompletableFuture<Boolean> uploadGameMaster(Path jarPath) {
        try {
            String fileName = jarPath.getFileName().toString();
            byte[] fileBytes = Files.readAllBytes(jarPath);

            String boundary = "---WebKitFormBoundary" + System.currentTimeMillis();
            String body = buildMultipartBody(boundary, fileName, fileBytes);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/upload"))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.getBytes()))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() == 201) {
                            log.info("Uploaded game master: {}", fileName);
                            notifyListeners(new GameMasterEvent(GameMasterEventType.UPLOADED, fileName,
                                    "Game master " + fileName + " uploaded"));
                            return true;
                        } else {
                            log.error("Failed to upload game master: {} {}", response.statusCode(), response.body());
                            notifyListeners(new GameMasterEvent(GameMasterEventType.ERROR, fileName,
                                    "Failed to upload: " + response.statusCode()));
                            return false;
                        }
                    })
                    .exceptionally(e -> {
                        log.error("Failed to upload game master: {}", fileName, e);
                        notifyListeners(new GameMasterEvent(GameMasterEventType.ERROR, fileName, e.getMessage()));
                        return false;
                    });
        } catch (IOException e) {
            log.error("Failed to read JAR file: {}", jarPath, e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Uninstall a game master.
     */
    public CompletableFuture<Boolean> uninstallGameMaster(String gameMasterName) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/" + gameMasterName))
                .DELETE()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 204) {
                        log.info("Uninstalled game master: {}", gameMasterName);
                        notifyListeners(new GameMasterEvent(GameMasterEventType.UNINSTALLED, gameMasterName,
                                "Game master " + gameMasterName + " uninstalled"));
                        return true;
                    } else {
                        log.error("Failed to uninstall game master {}: {}", gameMasterName, response.statusCode());
                        notifyListeners(new GameMasterEvent(GameMasterEventType.ERROR, gameMasterName,
                                "Failed to uninstall: " + response.statusCode()));
                        return false;
                    }
                })
                .exceptionally(e -> {
                    log.error("Failed to uninstall game master: {}", gameMasterName, e);
                    notifyListeners(new GameMasterEvent(GameMasterEventType.ERROR, gameMasterName, e.getMessage()));
                    return false;
                });
    }

    /**
     * Reload all game masters.
     */
    public CompletableFuture<Boolean> reloadGameMasters() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/reload"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        log.info("Reloaded game masters");
                        notifyListeners(new GameMasterEvent(GameMasterEventType.RELOADED, null,
                                "Game masters reloaded"));
                        return true;
                    } else {
                        log.error("Failed to reload game masters: {}", response.statusCode());
                        notifyListeners(new GameMasterEvent(GameMasterEventType.ERROR, null,
                                "Failed to reload: " + response.statusCode()));
                        return false;
                    }
                })
                .exceptionally(e -> {
                    log.error("Failed to reload game masters", e);
                    notifyListeners(new GameMasterEvent(GameMasterEventType.ERROR, null, e.getMessage()));
                    return false;
                });
    }

    private void notifyListeners(GameMasterEvent event) {
        for (Consumer<GameMasterEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.error("Error in game master event listener", e);
            }
        }
    }

    private String buildMultipartBody(String boundary, String fileName, byte[] fileBytes) {
        StringBuilder sb = new StringBuilder();
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(fileName).append("\"\r\n");
        sb.append("Content-Type: application/java-archive\r\n");
        sb.append("\r\n");
        sb.append(new String(fileBytes));
        sb.append("\r\n");
        sb.append("--").append(boundary).append("--\r\n");
        return sb.toString();
    }

    private List<GameMasterInfo> parseGameMasterList(String json) {
        List<GameMasterInfo> gameMasters = new ArrayList<>();
        // Match individual JSON objects in the array
        Pattern objectPattern = Pattern.compile("\\{[^{}]+\\}");
        Matcher objectMatcher = objectPattern.matcher(json);

        while (objectMatcher.find()) {
            String obj = objectMatcher.group();

            // Extract individual fields
            String name = extractStringField(obj, "name");
            int enabledMatches = extractIntField(obj, "enabledMatches", 0);

            if (name != null) {
                gameMasters.add(new GameMasterInfo(name, enabledMatches));
            }
        }
        return gameMasters;
    }

    private String extractStringField(String json, String fieldName) {
        // Match "fieldName":"value" or "fieldName":null
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*(?:\"([^\"]*)\"|null)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private int extractIntField(String json, String fieldName, int defaultValue) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return defaultValue;
    }

    /**
     * Shutdown the service.
     */
    public void shutdown() {
        // HttpClient doesn't need explicit shutdown
    }

    /**
     * Game master information record.
     */
    public record GameMasterInfo(String name, int enabledMatches) {}

    /**
     * Game master event types.
     */
    public enum GameMasterEventType {
        UPLOADED, UNINSTALLED, RELOADED, ERROR
    }

    /**
     * Game master event record.
     */
    public record GameMasterEvent(GameMasterEventType type, String gameMasterName, String message) {}
}
