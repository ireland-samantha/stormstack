package com.lightningfirefly.engine.api.resource.adapter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * REST API adapter for command operations.
 */
public interface CommandAdapter {

    /**
     * Submit a command to be executed.
     *
     * @param matchId the match ID
     * @param commandName the command name
     * @param entityId the target entity ID
     * @param payload command payload data
     */
    void submitCommand(long matchId, String commandName, long entityId, Map<String, Object> payload) throws IOException;

    /**
     * Get available commands for a module.
     *
     * @param moduleName the module name
     * @return list of command names
     */
    List<String> getAvailableCommands(String moduleName) throws IOException;

    /**
     * HTTP-based implementation.
     */
    class HttpCommandAdapter implements CommandAdapter {
        private final HttpClient httpClient;
        private final String baseUrl;

        public HttpCommandAdapter(String baseUrl) {
            this(baseUrl, AdapterConfig.defaults());
        }

        public HttpCommandAdapter(String baseUrl, AdapterConfig config) {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(config.getConnectTimeout())
                    .build();
        }

        public HttpCommandAdapter(String baseUrl, HttpClient httpClient) {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            this.httpClient = httpClient;
        }

        @Override
        public void submitCommand(long matchId, String commandName, long entityId, Map<String, Object> payload) throws IOException {
            // Build payload JSON
            StringBuilder payloadJson = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : payload.entrySet()) {
                if (!first) payloadJson.append(",");
                payloadJson.append("\"").append(entry.getKey()).append("\":");
                Object value = entry.getValue();
                if (value instanceof String) {
                    payloadJson.append("\"").append(value).append("\"");
                } else {
                    payloadJson.append(value);
                }
                first = false;
            }
            payloadJson.append("}");

            String json = "{\"matchId\":" + matchId +
                    ",\"commandName\":\"" + commandName + "\"" +
                    ",\"entityId\":" + entityId +
                    ",\"payload\":" + payloadJson + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/commands"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            try {
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() != 200 && response.statusCode() != 201 && response.statusCode() != 202) {
                    throw new IOException("Submit command failed with status: " + response.statusCode());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        @Override
        public List<String> getAvailableCommands(String moduleName) throws IOException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/commands?module=" + moduleName))
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    // Simplified - would parse JSON array in real implementation
                    return List.of();
                }
                throw new IOException("Get commands failed with status: " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }
    }
}
