package com.lightningfirefly.game.backend.adapter;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Fluent client for snapshot operations.
 *
 * <p>Usage:
 * <pre>{@code
 * // Fetch snapshot for a match
 * var snapshot = client.snapshots().forMatch(matchId).fetch();
 *
 * // Access module data
 * var renderModule = snapshot.module("RenderModule");
 * var spriteX = renderModule.component("SPRITE_X");
 *
 * // Get entity IDs
 * var entityIds = snapshot.entityIds();
 *
 * // Check if component has values
 * boolean hasSprites = snapshot.module("RenderModule").has("SPRITE_X");
 * }</pre>
 */
@Slf4j
public final class SnapshotsClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    SnapshotsClient(String baseUrl, HttpClient httpClient, Duration requestTimeout) {
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
    }

    /**
     * Target a specific match for snapshot operations.
     */
    public MatchSnapshots forMatch(long matchId) {
        return new MatchSnapshots(this, matchId);
    }

    /**
     * Fetch all snapshots.
     */
    public List<Snapshot> fetchAll() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/snapshots"))
                    .timeout(requestTimeout)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return parseSnapshotList(response.body());
            }
            throw new IOException("Fetch snapshots failed: " + response.statusCode());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to fetch snapshots", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted", e);
        }
    }

    Snapshot fetchForMatch(long matchId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/snapshots/match/" + matchId))
                    .timeout(requestTimeout)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return parseSnapshot(response.body(), matchId);
            }
            throw new IOException("Fetch snapshot failed: " + response.statusCode());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to fetch snapshot for match " + matchId, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted", e);
        }
    }

    private Snapshot parseSnapshot(String json, long matchId) {
        // Parse snapshotData field
        Map<String, Map<String, List<Float>>> data = new LinkedHashMap<>();

        int dataStart = json.indexOf("\"snapshotData\":");
        if (dataStart == -1) {
            dataStart = json.indexOf("\"data\":");
        }
        if (dataStart == -1) {
            return new Snapshot(matchId, 0, data);
        }

        // Parse tick
        long tick = 0;
        int tickStart = json.indexOf("\"tick\":");
        if (tickStart != -1) {
            int tickEnd = json.indexOf(",", tickStart);
            if (tickEnd == -1) tickEnd = json.indexOf("}", tickStart);
            tick = Long.parseLong(json.substring(tickStart + 7, tickEnd).trim());
        }

        // Extract the data object
        int objStart = json.indexOf("{", dataStart);
        if (objStart == -1) return new Snapshot(matchId, tick, data);

        // Parse each module
        int pos = objStart + 1;
        while (pos < json.length()) {
            // Find module name
            int moduleNameStart = json.indexOf("\"", pos);
            if (moduleNameStart == -1) break;
            int moduleNameEnd = json.indexOf("\"", moduleNameStart + 1);
            if (moduleNameEnd == -1) break;
            String moduleName = json.substring(moduleNameStart + 1, moduleNameEnd);

            // Find module data object
            int colonPos = json.indexOf(":", moduleNameEnd);
            int moduleObjStart = json.indexOf("{", colonPos);
            if (moduleObjStart == -1) break;

            // Find matching closing brace
            int braceCount = 1;
            int moduleObjEnd = moduleObjStart + 1;
            while (moduleObjEnd < json.length() && braceCount > 0) {
                if (json.charAt(moduleObjEnd) == '{') braceCount++;
                else if (json.charAt(moduleObjEnd) == '}') braceCount--;
                moduleObjEnd++;
            }

            String moduleJson = json.substring(moduleObjStart, moduleObjEnd);
            Map<String, List<Float>> moduleData = parseModuleData(moduleJson);
            if (!moduleData.isEmpty()) {
                data.put(moduleName, moduleData);
            }

            pos = moduleObjEnd;
        }

        return new Snapshot(matchId, tick, data);
    }

    private Map<String, List<Float>> parseModuleData(String json) {
        Map<String, List<Float>> moduleData = new LinkedHashMap<>();
        int pos = 1;

        while (pos < json.length()) {
            // Find component name
            int compNameStart = json.indexOf("\"", pos);
            if (compNameStart == -1) break;
            int compNameEnd = json.indexOf("\"", compNameStart + 1);
            if (compNameEnd == -1) break;
            String compName = json.substring(compNameStart + 1, compNameEnd);

            // Find array
            int arrayStart = json.indexOf("[", compNameEnd);
            if (arrayStart == -1) break;
            int arrayEnd = json.indexOf("]", arrayStart);
            if (arrayEnd == -1) break;

            String arrayStr = json.substring(arrayStart + 1, arrayEnd);
            List<Float> values = new ArrayList<>();
            if (!arrayStr.trim().isEmpty()) {
                for (String val : arrayStr.split(",")) {
                    String trimmed = val.trim();
                    if (!trimmed.isEmpty()) {
                        try {
                            values.add(Float.parseFloat(trimmed));
                        } catch (NumberFormatException e) {
                            // Skip non-numeric values
                        }
                    }
                }
            }
            moduleData.put(compName, values);

            pos = arrayEnd + 1;
        }

        return moduleData;
    }

    private List<Snapshot> parseSnapshotList(String json) {
        List<Snapshot> snapshots = new ArrayList<>();
        // Simplified - would need better JSON parsing
        int pos = 0;
        int matchCount = 0;
        while ((pos = json.indexOf("\"matchId\":", pos)) != -1) {
            int idEnd = json.indexOf(",", pos);
            if (idEnd == -1) idEnd = json.indexOf("}", pos);
            long matchId = Long.parseLong(json.substring(pos + 10, idEnd).trim());

            // Find the snapshot object bounds
            int objStart = json.lastIndexOf("{", pos);
            int braceCount = 1;
            int objEnd = objStart + 1;
            while (objEnd < json.length() && braceCount > 0) {
                if (json.charAt(objEnd) == '{') braceCount++;
                else if (json.charAt(objEnd) == '}') braceCount--;
                objEnd++;
            }

            String snapshotJson = json.substring(objStart, objEnd);
            snapshots.add(parseSnapshot(snapshotJson, matchId));

            pos = objEnd;
            matchCount++;
            if (matchCount > 100) break; // Safety limit
        }
        return snapshots;
    }

    /**
     * Snapshot operations for a specific match.
     */
    public static class MatchSnapshots {
        private final SnapshotsClient client;
        private final long matchId;

        MatchSnapshots(SnapshotsClient client, long matchId) {
            this.client = client;
            this.matchId = matchId;
        }

        /**
         * Fetch the current snapshot.
         */
        public Snapshot fetch() {
            return client.fetchForMatch(matchId);
        }
    }

    /**
     * A snapshot of ECS state.
     */
    public record Snapshot(long matchId, long tick, Map<String, Map<String, List<Float>>> data) {

        /**
         * Access a module's data.
         */
        public ModuleData module(String moduleName) {
            return new ModuleData(data.getOrDefault(moduleName, Map.of()));
        }

        /**
         * Check if a module exists in the snapshot.
         */
        public boolean hasModule(String moduleName) {
            return data.containsKey(moduleName);
        }

        /**
         * Get all module names.
         */
        public Set<String> moduleNames() {
            return data.keySet();
        }

        /**
         * Get entity IDs from any module that has ENTITY_ID.
         */
        public List<Float> entityIds() {
            for (Map<String, List<Float>> moduleData : data.values()) {
                List<Float> entityIds = moduleData.get("ENTITY_ID");
                if (entityIds != null && !entityIds.isEmpty()) {
                    return entityIds;
                }
            }
            return List.of();
        }
    }

    /**
     * Data for a single module in a snapshot.
     */
    public record ModuleData(Map<String, List<Float>> components) {

        /**
         * Get a component's values.
         */
        public List<Float> component(String componentName) {
            return components.getOrDefault(componentName, List.of());
        }

        /**
         * Check if a component has values.
         */
        public boolean has(String componentName) {
            List<Float> values = components.get(componentName);
            return values != null && !values.isEmpty();
        }

        /**
         * Get all component names.
         */
        public Set<String> componentNames() {
            return components.keySet();
        }

        /**
         * Get the first value of a component, or default.
         */
        public float first(String componentName, float defaultValue) {
            List<Float> values = components.get(componentName);
            return (values != null && !values.isEmpty()) ? values.get(0) : defaultValue;
        }
    }
}
