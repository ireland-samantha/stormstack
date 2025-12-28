package com.lightningfirefly.engine.api.resource.adapter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * REST API adapter for module operations.
 */
public interface ModuleAdapter {

    /**
     * Get all available modules.
     *
     * @return list of module responses
     */
    List<ModuleResponse> getAllModules() throws IOException;

    /**
     * Get a specific module by name.
     *
     * @param moduleName the module name
     * @return the module if found
     */
    Optional<ModuleResponse> getModule(String moduleName) throws IOException;

    /**
     * Upload a JAR module.
     *
     * @param fileName the JAR file name
     * @param jarData the JAR file bytes
     * @return list of all modules after upload
     */
    List<ModuleResponse> uploadModule(String fileName, byte[] jarData) throws IOException;

    /**
     * Uninstall a module.
     *
     * @param moduleName the module name
     * @return true if uninstalled
     */
    boolean uninstallModule(String moduleName) throws IOException;

    /**
     * Reload all modules from disk.
     *
     * @return list of all modules after reload
     */
    List<ModuleResponse> reloadModules() throws IOException;

    /**
     * Module response DTO.
     */
    record ModuleResponse(String name, String flagComponentName, int enabledMatchCount) {}

    /**
     * HTTP-based implementation.
     */
    class HttpModuleAdapter implements ModuleAdapter {
        private final HttpClient httpClient;
        private final String baseUrl;

        public HttpModuleAdapter(String baseUrl) {
            this(baseUrl, AdapterConfig.defaults());
        }

        public HttpModuleAdapter(String baseUrl, AdapterConfig config) {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(config.getConnectTimeout())
                    .build();
        }

        public HttpModuleAdapter(String baseUrl, HttpClient httpClient) {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            this.httpClient = httpClient;
        }

        @Override
        public List<ModuleResponse> getAllModules() throws IOException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/modules"))
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return parseModuleList(response.body());
                }
                throw new IOException("Get modules failed with status: " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        @Override
        public Optional<ModuleResponse> getModule(String moduleName) throws IOException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/modules/" + moduleName))
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return Optional.of(parseModuleResponse(response.body()));
                } else if (response.statusCode() == 404) {
                    return Optional.empty();
                }
                throw new IOException("Get module failed with status: " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        @Override
        public List<ModuleResponse> uploadModule(String fileName, byte[] jarData) throws IOException {
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
                    .uri(URI.create(baseUrl + "/api/modules/upload"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 201) {
                    return parseModuleList(response.body());
                }
                throw new IOException("Upload module failed with status: " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Upload interrupted", e);
            }
        }

        @Override
        public boolean uninstallModule(String moduleName) throws IOException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/modules/" + moduleName))
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
        public List<ModuleResponse> reloadModules() throws IOException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/modules/reload"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return parseModuleList(response.body());
                }
                throw new IOException("Reload modules failed with status: " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        private List<ModuleResponse> parseModuleList(String json) {
            List<ModuleResponse> modules = new ArrayList<>();
            if (json == null || json.equals("[]")) {
                return modules;
            }

            // Simple JSON array parsing
            int idx = 0;
            while ((idx = json.indexOf("{", idx)) != -1) {
                int end = json.indexOf("}", idx);
                if (end == -1) break;
                String obj = json.substring(idx, end + 1);
                modules.add(parseModuleResponse(obj));
                idx = end + 1;
            }
            return modules;
        }

        private ModuleResponse parseModuleResponse(String json) {
            String name = extractStringValue(json, "name");
            String flagComponentName = extractStringValue(json, "flagComponentName");
            int enabledMatchCount = extractIntValue(json, "enabledMatchCount");
            return new ModuleResponse(name, flagComponentName, enabledMatchCount);
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
