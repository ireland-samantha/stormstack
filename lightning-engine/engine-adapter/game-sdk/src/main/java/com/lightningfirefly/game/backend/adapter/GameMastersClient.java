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
 * Fluent client for game master operations.
 *
 * <p>Usage:
 * <pre>{@code
 * // List all game masters
 * var gameMasters = client.gameMasters().list();
 *
 * // Get a specific game master
 * var gm = client.gameMasters().get("CheckersGameMaster");
 *
 * // Check if game master exists
 * boolean exists = client.gameMasters().exists("TickCounter");
 *
 * // Reload all game masters
 * client.gameMasters().reload();
 * }</pre>
 */
@Slf4j
public final class GameMastersClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    GameMastersClient(String baseUrl, HttpClient httpClient, Duration requestTimeout) {
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
    }

    /**
     * List all installed game masters.
     */
    public List<GameMaster> list() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/gamemasters"))
                    .timeout(requestTimeout)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return parseGameMasterList(response.body());
            }
            throw new IOException("List game masters failed: " + response.statusCode());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list game masters", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted", e);
        }
    }

    /**
     * Get a specific game master by name.
     */
    public Optional<GameMaster> get(String gameMasterName) {
        return list().stream()
                .filter(gm -> gm.name().equals(gameMasterName))
                .findFirst();
    }

    /**
     * Check if a game master exists.
     */
    public boolean exists(String gameMasterName) {
        return get(gameMasterName).isPresent();
    }

    /**
     * Get all game master names.
     */
    public List<String> names() {
        return list().stream().map(GameMaster::name).toList();
    }

    /**
     * Reload all game masters from disk.
     */
    public void reload() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/gamemasters/reload"))
                    .timeout(requestTimeout)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 400) {
                throw new IOException("Reload game masters failed: " + response.statusCode());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to reload game masters", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted", e);
        }
    }

    private List<GameMaster> parseGameMasterList(String json) {
        List<GameMaster> gameMasters = new ArrayList<>();

        int pos = 0;
        while ((pos = json.indexOf("\"name\":", pos)) != -1) {
            int nameStart = json.indexOf("\"", pos + 7);
            int nameEnd = json.indexOf("\"", nameStart + 1);
            if (nameStart == -1 || nameEnd == -1) break;

            String name = json.substring(nameStart + 1, nameEnd);

            // Try to find description
            String description = "";
            int descPos = json.indexOf("\"description\":", nameEnd);
            if (descPos != -1 && descPos < json.indexOf("}", nameEnd)) {
                int descStart = json.indexOf("\"", descPos + 14);
                int descEnd = json.indexOf("\"", descStart + 1);
                if (descStart != -1 && descEnd != -1) {
                    description = json.substring(descStart + 1, descEnd);
                }
            }

            gameMasters.add(new GameMaster(name, description));
            pos = nameEnd + 1;
        }

        return gameMasters;
    }

    /**
     * Game master information.
     */
    public record GameMaster(String name, String description) {}
}
