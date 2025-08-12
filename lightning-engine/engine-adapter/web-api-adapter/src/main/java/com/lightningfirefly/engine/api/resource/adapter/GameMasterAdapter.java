package com.lightningfirefly.engine.api.resource.adapter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * REST API adapter for game master operations.
 */
public interface GameMasterAdapter {

    /**
     * Get all available game masters.
     *
     * @return list of game master responses
     */
    List<GameMasterResponse> getAllGameMasters() throws IOException;

    /**
     * Get a specific game master by name.
     *
     * @param gameMasterName the game master name
     * @return the game master if found
     */
    Optional<GameMasterResponse> getGameMaster(String gameMasterName) throws IOException;

    /**
     * Upload a JAR game master.
     *
     * @param fileName the JAR file name
     * @param jarData  the JAR file bytes
     * @return list of all game masters after upload
     */
    List<GameMasterResponse> uploadGameMaster(String fileName, byte[] jarData) throws IOException;

    /**
     * Uninstall a game master.
     *
     * @param gameMasterName the game master name
     * @return true if uninstalled
     */
    boolean uninstallGameMaster(String gameMasterName) throws IOException;

    /**
     * Reload all game masters from disk.
     *
     * @return list of all game masters after reload
     */
    List<GameMasterResponse> reloadGameMasters() throws IOException;

    /**
     * Check if a game master is installed.
     *
     * @param gameMasterName the game master name
     * @return true if installed
     */
    default boolean hasGameMaster(String gameMasterName) throws IOException {
        return getGameMaster(gameMasterName).isPresent();
    }

    /**
     * Game master response DTO.
     */
    record GameMasterResponse(String name, int enabledMatchCount) {}

    /**
     * HTTP-based implementation.
     */
    class HttpGameMasterAdapter implements GameMasterAdapter {
        private final HttpClient httpClient;
        private final String baseUrl;

        public HttpGameMasterAdapter(String baseUrl) {
            this(baseUrl, AdapterConfig.defaults());
        }

        public HttpGameMasterAdapter(String baseUrl, AdapterConfig config) {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(config.getConnectTimeout())
                    .build();
        }

        public HttpGameMasterAdapter(String baseUrl, HttpClient httpClient) {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            this.httpClient = httpClient;
        }

        @Override
        public List<GameMasterResponse> getAllGameMasters() throws IOException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/gamemasters"))
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return parseGameMasterList(response.body());
                }
                throw new IOException("Get game masters failed with status: " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        @Override
        public Optional<GameMasterResponse> getGameMaster(String gameMasterName) throws IOException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/gamemasters/" + gameMasterName))
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return Optional.of(parseGameMasterResponse(response.body()));
                } else if (response.statusCode() == 404) {
                    return Optional.empty();
                }
                throw new IOException("Get game master failed with status: " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        @Override
        public List<GameMasterResponse> uploadGameMaster(String fileName, byte[] jarData) throws IOException {
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();

            String header = "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n" +
                    "Content-Type: application/java-archive\r\n\r\n";

            byte[] prefix = header.getBytes();
            byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes();
            byte[] requestBody = new byte[prefix.length + jarData.length + suffix.length];
            System.arraycopy(prefix, 0, requestBody, 0, prefix.length);
            System.arraycopy(jarData, 0, requestBody, prefix.length, jarData.length);
            System.arraycopy(suffix, 0, requestBody, prefix.length + jarData.length, suffix.length);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/gamemasters/upload"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 201) {
                    return parseGameMasterList(response.body());
                }
                throw new IOException("Upload game master failed with status: " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Upload interrupted", e);
            }
        }

        @Override
        public boolean uninstallGameMaster(String gameMasterName) throws IOException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/gamemasters/" + gameMasterName))
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

        @Override
        public List<GameMasterResponse> reloadGameMasters() throws IOException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/gamemasters/reload"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return parseGameMasterList(response.body());
                }
                throw new IOException("Reload game masters failed with status: " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        private List<GameMasterResponse> parseGameMasterList(String json) {
            List<GameMasterResponse> gameMasters = new ArrayList<>();
            if (json == null || json.equals("[]")) {
                return gameMasters;
            }

            // Simple JSON array parsing
            int idx = 0;
            while ((idx = json.indexOf("{", idx)) != -1) {
                int end = json.indexOf("}", idx);
                if (end == -1) break;
                String obj = json.substring(idx, end + 1);
                gameMasters.add(parseGameMasterResponse(obj));
                idx = end + 1;
            }
            return gameMasters;
        }

        private GameMasterResponse parseGameMasterResponse(String json) {
            String name = extractStringValue(json, "name");
            int enabledMatchCount = extractIntValue(json, "enabledMatchCount");
            return new GameMasterResponse(name, enabledMatchCount);
        }

        private String extractStringValue(String json, String key) {
            String searchKey = "\"" + key + "\":";
            int start = json.indexOf(searchKey);
            if (start == -1) return null;

            start += searchKey.length();
            // Skip whitespace
            while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
                start++;
            }

            if (start >= json.length()) return null;
            if (json.charAt(start) == 'n') return null; // null

            if (json.charAt(start) == '"') {
                start++;
                int end = json.indexOf("\"", start);
                if (end == -1) return null;
                return json.substring(start, end);
            }
            return null;
        }

        private int extractIntValue(String json, String key) {
            String searchKey = "\"" + key + "\":";
            int start = json.indexOf(searchKey);
            if (start == -1) return 0;

            start += searchKey.length();
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
                end++;
            }
            if (end == start) return 0;
            return Integer.parseInt(json.substring(start, end).trim());
        }
    }
}
