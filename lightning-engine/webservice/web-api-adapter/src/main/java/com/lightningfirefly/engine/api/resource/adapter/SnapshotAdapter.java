package com.lightningfirefly.engine.api.resource.adapter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * REST API adapter for snapshot operations.
 */
public interface SnapshotAdapter {

    /**
     * Get a snapshot for a specific match.
     *
     * @param matchId the match ID
     * @return the snapshot if the match exists
     */
    Optional<SnapshotResponse> getMatchSnapshot(long matchId) throws IOException;

    /**
     * Snapshot response DTO.
     */
    record SnapshotResponse(long matchId, long tick, String snapshotData) {}

    /**
     * HTTP-based implementation.
     */
    class HttpSnapshotAdapter implements SnapshotAdapter {
        private final HttpClient httpClient;
        private final String baseUrl;

        public HttpSnapshotAdapter(String baseUrl) {
            this(baseUrl, AdapterConfig.defaults());
        }

        public HttpSnapshotAdapter(String baseUrl, AdapterConfig config) {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(config.getConnectTimeout())
                    .build();
        }

        public HttpSnapshotAdapter(String baseUrl, HttpClient httpClient) {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            this.httpClient = httpClient;
        }

        @Override
        public Optional<SnapshotResponse> getMatchSnapshot(long matchId) throws IOException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/snapshots/match/" + matchId))
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return Optional.of(parseSnapshotResponse(response.body()));
                } else if (response.statusCode() == 404) {
                    return Optional.empty();
                }
                throw new IOException("Get snapshot failed with status: " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        private SnapshotResponse parseSnapshotResponse(String json) {
            long matchId = extractLongValue(json, "matchId");
            long tick = extractLongValue(json, "tick");
            // The snapshot data could be complex; return as raw JSON string
            String snapshotData = extractObjectValue(json, "snapshot");
            return new SnapshotResponse(matchId, tick, snapshotData);
        }

        private long extractLongValue(String json, String key) {
            String searchKey = "\"" + key + "\":";
            int start = json.indexOf(searchKey);
            if (start == -1) return 0;

            start += searchKey.length();
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
                end++;
            }
            if (end == start) return 0;
            return Long.parseLong(json.substring(start, end).trim());
        }

        private String extractObjectValue(String json, String key) {
            String searchKey = "\"" + key + "\":";
            int start = json.indexOf(searchKey);
            if (start == -1) return null;

            start += searchKey.length();
            // Skip whitespace
            while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
                start++;
            }

            if (start >= json.length()) return null;
            char c = json.charAt(start);

            if (c == '{') {
                // Find matching closing brace
                int braceCount = 1;
                int end = start + 1;
                while (end < json.length() && braceCount > 0) {
                    char ch = json.charAt(end);
                    if (ch == '{') braceCount++;
                    else if (ch == '}') braceCount--;
                    end++;
                }
                return json.substring(start, end);
            } else if (c == '[') {
                // Find matching closing bracket
                int bracketCount = 1;
                int end = start + 1;
                while (end < json.length() && bracketCount > 0) {
                    char ch = json.charAt(end);
                    if (ch == '[') bracketCount++;
                    else if (ch == ']') bracketCount--;
                    end++;
                }
                return json.substring(start, end);
            } else if (c == 'n') {
                return null;
            }

            return null;
        }
    }
}
