package com.lightningfirefly.game.backend.adapter;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fluent client for simulation control.
 *
 * <p>Usage:
 * <pre>{@code
 * var sim = client.simulation();
 *
 * // Get current tick
 * long tick = sim.currentTick();
 *
 * // Advance by one tick
 * sim.tick();
 *
 * // Advance by multiple ticks
 * sim.tick(10);
 *
 * // Start auto-advance
 * sim.play(10);  // 10ms interval
 *
 * // Stop auto-advance
 * sim.stop();
 *
 * // Check status
 * boolean playing = sim.isPlaying();
 * }</pre>
 */
@Slf4j
public final class SimulationClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    SimulationClient(String baseUrl, HttpClient httpClient, Duration requestTimeout) {
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
    }

    /**
     * Get the current simulation tick.
     */
    public long currentTick() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/simulation/tick"))
                    .timeout(requestTimeout)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return parseTick(response.body());
            }
            throw new IOException("Get tick failed: " + response.statusCode());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to get current tick", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted", e);
        }
    }

    /**
     * Advance the simulation by one tick.
     *
     * @return the new tick value
     */
    public long tick() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/simulation/tick"))
                    .timeout(requestTimeout)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return parseTick(response.body());
            }
            throw new IOException("Advance tick failed: " + response.statusCode());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to advance tick", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted", e);
        }
    }

    /**
     * Advance the simulation by multiple ticks.
     *
     * @param count number of ticks to advance
     * @return the final tick value
     */
    public long tick(int count) {
        long lastTick = currentTick();
        for (int i = 0; i < count; i++) {
            lastTick = tick();
        }
        return lastTick;
    }

    /**
     * Start auto-advancing ticks.
     *
     * @param intervalMs interval between ticks in milliseconds
     * @return the status after starting
     */
    public Status play(long intervalMs) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/simulation/play?intervalMs=" + intervalMs))
                    .timeout(requestTimeout)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return parseStatus(response.body());
            }
            throw new IOException("Play failed: " + response.statusCode());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start play", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted", e);
        }
    }

    /**
     * Start auto-advancing with default 10ms interval.
     */
    public Status play() {
        return play(10);
    }

    /**
     * Stop auto-advancing ticks.
     */
    public Status stop() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/simulation/stop"))
                    .timeout(requestTimeout)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return parseStatus(response.body());
            }
            throw new IOException("Stop failed: " + response.statusCode());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to stop", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted", e);
        }
    }

    /**
     * Get the current simulation status.
     */
    public Status status() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/simulation/status"))
                    .timeout(requestTimeout)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return parseStatus(response.body());
            }
            throw new IOException("Get status failed: " + response.statusCode());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to get status", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted", e);
        }
    }

    /**
     * Check if simulation is auto-advancing.
     */
    public boolean isPlaying() {
        return status().playing();
    }

    private long parseTick(String json) {
        int start = json.indexOf("\"tick\":") + 7;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        return Long.parseLong(json.substring(start, end).trim());
    }

    private Status parseStatus(String json) {
        boolean playing = json.contains("\"playing\":true");
        long tick = 0;
        int tickStart = json.indexOf("\"tick\":");
        if (tickStart != -1) {
            tick = parseTick(json);
        }
        return new Status(playing, tick);
    }

    /**
     * Simulation status.
     */
    public record Status(boolean playing, long tick) {}
}
