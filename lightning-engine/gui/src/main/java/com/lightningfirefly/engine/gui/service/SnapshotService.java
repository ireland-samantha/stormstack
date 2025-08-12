package com.lightningfirefly.engine.gui.service;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for fetching snapshots via REST API.
 */
@Slf4j
public class SnapshotService {

    private final String baseUrl;
    private final HttpClient httpClient;

    public SnapshotService(String serverUrl) {
        this.baseUrl = serverUrl + "/api/snapshots";
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * Get snapshots for ALL matches.
     * Returns a list of snapshots, one per match.
     */
    public CompletableFuture<List<SnapshotData>> getAllSnapshots() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .GET()
                .header("Accept", "application/json")
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return parseSnapshotList(response.body());
                    } else {
                        log.error("Failed to get all snapshots: {} {}", response.statusCode(), response.body());
                        return new ArrayList<SnapshotData>();
                    }
                })
                .exceptionally(e -> {
                    log.error("Failed to get all snapshots", e);
                    return new ArrayList<SnapshotData>();
                });
    }

    /**
     * Get snapshot for a specific match.
     */
    public CompletableFuture<SnapshotData> getSnapshot(long matchId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/match/" + matchId))
                .GET()
                .header("Accept", "application/json")
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return parseSnapshot(response.body());
                    } else {
                        log.error("Failed to get snapshot for match {}: {}", matchId, response.statusCode());
                        return null;
                    }
                })
                .exceptionally(e -> {
                    log.error("Failed to get snapshot for match {}", matchId, e);
                    return null;
                });
    }

    /**
     * Shutdown the service.
     */
    public void shutdown() {
        // HttpClient doesn't need explicit shutdown
    }

    private List<SnapshotData> parseSnapshotList(String json) {
        List<SnapshotData> snapshots = new ArrayList<>();

        // Parse JSON array of snapshots
        // Format: [{"matchId":1,"tick":42,"data":{...}}, ...]
        if (json == null || json.trim().isEmpty() || json.equals("[]")) {
            return snapshots;
        }

        // Simple parsing - split by "},{"
        String trimmed = json.trim();
        if (trimmed.startsWith("[")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("]")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        if (trimmed.isEmpty()) {
            return snapshots;
        }

        // Split by objects - need to handle nested braces
        List<String> objects = splitJsonObjects(trimmed);
        for (String objJson : objects) {
            SnapshotData snapshot = parseSnapshot(objJson);
            if (snapshot != null) {
                snapshots.add(snapshot);
            }
        }

        return snapshots;
    }

    private List<String> splitJsonObjects(String json) {
        List<String> objects = new ArrayList<>();
        int depth = 0;
        int start = 0;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    objects.add(json.substring(start, i + 1));
                }
            }
        }

        return objects;
    }

    private SnapshotData parseSnapshot(String json) {
        try {
            // Parse matchId
            Pattern matchIdPattern = Pattern.compile("\"matchId\"\\s*:\\s*(\\d+)");
            Matcher matchIdMatcher = matchIdPattern.matcher(json);
            if (!matchIdMatcher.find()) {
                return null;
            }
            long matchId = Long.parseLong(matchIdMatcher.group(1));

            // Parse tick
            Pattern tickPattern = Pattern.compile("\"tick\"\\s*:\\s*(\\d+)");
            Matcher tickMatcher = tickPattern.matcher(json);
            long tick = 0;
            if (tickMatcher.find()) {
                tick = Long.parseLong(tickMatcher.group(1));
            }

            // Parse data
            Map<String, Map<String, List<Float>>> data = parseData(json);

            return new SnapshotData(matchId, tick, data);
        } catch (Exception e) {
            log.error("Failed to parse snapshot JSON", e);
            return null;
        }
    }

    private Map<String, Map<String, List<Float>>> parseData(String json) {
        Map<String, Map<String, List<Float>>> data = new HashMap<>();

        // Find the "data" section
        int dataStart = json.indexOf("\"data\"");
        if (dataStart == -1) {
            return data;
        }

        // Find the opening brace after "data":
        int braceStart = json.indexOf('{', dataStart + 6);
        if (braceStart == -1) {
            return data;
        }

        // Find matching closing brace
        int depth = 1;
        int braceEnd = braceStart + 1;
        while (braceEnd < json.length() && depth > 0) {
            char c = json.charAt(braceEnd);
            if (c == '{') depth++;
            else if (c == '}') depth--;
            braceEnd++;
        }

        String dataJson = json.substring(braceStart, braceEnd);

        // Parse module names and their data
        // Pattern: "ModuleName": { "COMPONENT": [...], ... }
        Pattern modulePattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\{");
        Matcher moduleMatcher = modulePattern.matcher(dataJson);

        while (moduleMatcher.find()) {
            String moduleName = moduleMatcher.group(1);
            int moduleStart = moduleMatcher.end() - 1;

            // Find matching closing brace for this module
            depth = 1;
            int moduleEnd = moduleStart + 1;
            while (moduleEnd < dataJson.length() && depth > 0) {
                char c = dataJson.charAt(moduleEnd);
                if (c == '{') depth++;
                else if (c == '}') depth--;
                moduleEnd++;
            }

            String moduleJson = dataJson.substring(moduleStart, moduleEnd);
            Map<String, List<Float>> moduleData = parseModuleData(moduleJson);
            data.put(moduleName, moduleData);
        }

        return data;
    }

    private Map<String, List<Float>> parseModuleData(String json) {
        Map<String, List<Float>> moduleData = new HashMap<>();

        // Pattern: "COMPONENT_NAME": [1.0, 2.0, 3.0]
        Pattern componentPattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\[([^\\]]*)\\]");
        Matcher componentMatcher = componentPattern.matcher(json);

        while (componentMatcher.find()) {
            String componentName = componentMatcher.group(1);
            String valuesStr = componentMatcher.group(2);
            List<Float> values = parseValues(valuesStr);
            moduleData.put(componentName, values);
        }

        return moduleData;
    }

    private List<Float> parseValues(String valuesStr) {
        List<Float> values = new ArrayList<>();
        if (valuesStr == null || valuesStr.trim().isEmpty()) {
            return values;
        }

        String[] parts = valuesStr.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                try {
                    values.add(Float.parseFloat(trimmed));
                } catch (NumberFormatException e) {
                    // Skip invalid values
                }
            }
        }

        return values;
    }

    /**
     * Snapshot data record.
     */
    public record SnapshotData(
            long matchId,
            long tick,
            Map<String, Map<String, List<Float>>> data
    ) {
        public Set<String> getModuleNames() {
            return data != null ? data.keySet() : Set.of();
        }

        public Map<String, List<Float>> getModuleData(String moduleName) {
            return data != null ? data.get(moduleName) : null;
        }

        public int getEntityCount() {
            if (data == null || data.isEmpty()) {
                return 0;
            }
            for (Map<String, List<Float>> moduleData : data.values()) {
                for (List<Float> values : moduleData.values()) {
                    return values.size();
                }
            }
            return 0;
        }
    }
}
