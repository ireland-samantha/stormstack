package com.lightningfirefly.engine.api.resource.adapter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * REST API adapter for simulation tick operations.
 */
public interface SimulationAdapter {

    /**
     * Get the current simulation tick.
     *
     * @return the current tick
     */
    long getCurrentTick() throws IOException;

    /**
     * Advance the simulation by one tick.
     *
     * @return the new tick after advancing
     */
    long advanceTick() throws IOException;

    /**
     * HTTP-based implementation.
     */
    class HttpSimulationAdapter implements SimulationAdapter {
        private final HttpClient httpClient;
        private final String baseUrl;

        public HttpSimulationAdapter(String baseUrl) {
            this(baseUrl, AdapterConfig.defaults());
        }

        public HttpSimulationAdapter(String baseUrl, AdapterConfig config) {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(config.getConnectTimeout())
                    .build();
        }

        public HttpSimulationAdapter(String baseUrl, HttpClient httpClient) {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            this.httpClient = httpClient;
        }

        @Override
        public long getCurrentTick() throws IOException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/simulation/tick"))
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return parseTickResponse(response.body());
                }
                throw new IOException("Get tick failed with status: " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        @Override
        public long advanceTick() throws IOException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/simulation/tick"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return parseTickResponse(response.body());
                }
                throw new IOException("Advance tick failed with status: " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        private long parseTickResponse(String json) {
            // Parse {"tick": 123}
            String searchKey = "\"tick\":";
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
    }
}
