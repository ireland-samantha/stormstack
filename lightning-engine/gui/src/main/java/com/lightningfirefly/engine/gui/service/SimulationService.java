package com.lightningfirefly.engine.gui.service;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for simulation tick operations via REST API.
 */
@Slf4j
public class SimulationService {

    private final String baseUrl;
    private final HttpClient httpClient;

    public SimulationService(String serverUrl) {
        this.baseUrl = serverUrl + "/api/simulation";
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * Get the current tick value.
     * @return the current tick
     */
    public CompletableFuture<Long> getCurrentTick() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/tick"))
                .GET()
                .header("Accept", "application/json")
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return parseTickResponse(response.body());
                    } else {
                        log.error("Failed to get current tick: {} {}", response.statusCode(), response.body());
                        return -1L;
                    }
                })
                .exceptionally(e -> {
                    log.error("Failed to get current tick", e);
                    return -1L;
                });
    }

    /**
     * Advance the simulation by one tick.
     * @return the new tick value after advancing
     */
    public CompletableFuture<Long> advanceTick() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/tick"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .header("Accept", "application/json")
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        long newTick = parseTickResponse(response.body());
                        log.info("Advanced tick to {}", newTick);
                        return newTick;
                    } else {
                        log.error("Failed to advance tick: {} {}", response.statusCode(), response.body());
                        return -1L;
                    }
                })
                .exceptionally(e -> {
                    log.error("Failed to advance tick", e);
                    return -1L;
                });
    }

    /**
     * Start auto-advancing ticks at the specified interval.
     *
     * @param intervalMs the interval between ticks in milliseconds
     * @return true if started successfully
     */
    public CompletableFuture<Boolean> play(long intervalMs) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/play?intervalMs=" + intervalMs))
                .POST(HttpRequest.BodyPublishers.noBody())
                .header("Accept", "application/json")
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        log.info("Started auto-advance with interval {}ms", intervalMs);
                        return true;
                    } else {
                        log.error("Failed to start auto-advance: {} {}", response.statusCode(), response.body());
                        return false;
                    }
                })
                .exceptionally(e -> {
                    log.error("Failed to start auto-advance", e);
                    return false;
                });
    }

    /**
     * Stop auto-advancing ticks.
     *
     * @return true if stopped successfully
     */
    public CompletableFuture<Boolean> stop() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/stop"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .header("Accept", "application/json")
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        log.info("Stopped auto-advance");
                        return true;
                    } else {
                        log.error("Failed to stop auto-advance: {} {}", response.statusCode(), response.body());
                        return false;
                    }
                })
                .exceptionally(e -> {
                    log.error("Failed to stop auto-advance", e);
                    return false;
                });
    }

    /**
     * Get the current play status.
     *
     * @return true if auto-advancing
     */
    public CompletableFuture<Boolean> isPlaying() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/status"))
                .GET()
                .header("Accept", "application/json")
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return parsePlayingStatus(response.body());
                    } else {
                        log.error("Failed to get play status: {} {}", response.statusCode(), response.body());
                        return false;
                    }
                })
                .exceptionally(e -> {
                    log.error("Failed to get play status", e);
                    return false;
                });
    }

    private long parseTickResponse(String json) {
        // Parse {"tick": 123}
        Pattern pattern = Pattern.compile("\"tick\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        }
        return -1L;
    }

    private boolean parsePlayingStatus(String json) {
        // Parse {"playing": true, ...}
        Pattern pattern = Pattern.compile("\"playing\"\\s*:\\s*(true|false)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return Boolean.parseBoolean(matcher.group(1));
        }
        return false;
    }

    /**
     * Shutdown the service.
     */
    public void shutdown() {
        // HttpClient doesn't need explicit shutdown
    }
}
