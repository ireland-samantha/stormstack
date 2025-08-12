package com.lightningfirefly.engine.api.resource.adapter;

import com.lightningfirefly.engine.api.resource.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * REST API adapter for resource operations.
 * Provides HTTP client methods for uploading and downloading resources.
 */
public interface ResourceAdapter {

    /**
     * Upload a resource to the server.
     *
     * @param resourceName the name of the resource
     * @param resourceType the type of the resource (e.g., "TEXTURE")
     * @param data the binary data
     * @return the created resource ID
     */
    long uploadResource(String resourceName, String resourceType, byte[] data) throws IOException;

    /**
     * Download a resource by ID.
     *
     * @param resourceId the resource ID
     * @return the resource data, or empty if not found
     */
    Optional<Resource> downloadResource(long resourceId) throws IOException;

    /**
     * Download a specific chunk of a resource.
     *
     * @param resourceId the resource ID
     * @param chunkIndex the chunk index (0-based)
     * @param chunkSize the size of each chunk
     * @return the chunk data
     */
    byte[] downloadChunk(long resourceId, int chunkIndex, int chunkSize) throws IOException;

    /**
     * Get the total number of chunks for a resource.
     *
     * @param resourceId the resource ID
     * @param chunkSize the chunk size to use for calculation
     * @return the total number of chunks
     */
    int getTotalChunks(long resourceId, int chunkSize) throws IOException;

    /**
     * List all available resources.
     *
     * @return list of resource metadata
     */
    List<Resource> listResources() throws IOException;

    /**
     * Delete a resource.
     *
     * @param resourceId the resource ID
     * @return true if deleted successfully
     */
    boolean deleteResource(long resourceId) throws IOException;

    /**
     * Default HTTP-based implementation of the ResourceAdapter.
     */
    class HttpResourceAdapter implements ResourceAdapter {
        private final HttpClient httpClient;
        private final String baseUrl;

        public HttpResourceAdapter(String baseUrl) {
            this(baseUrl, AdapterConfig.defaults());
        }

        public HttpResourceAdapter(String baseUrl, AdapterConfig config) {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(config.getConnectTimeout())
                    .build();
        }

        public HttpResourceAdapter(String baseUrl, HttpClient httpClient) {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            this.httpClient = httpClient;
        }

        @Override
        public long uploadResource(String resourceName, String resourceType, byte[] data) throws IOException {
            String boundary = "WebKitFormBoundary" + System.currentTimeMillis();

            String body = "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"resourceName\"\r\n\r\n" +
                    resourceName + "\r\n" +
                    "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"resourceType\"\r\n\r\n" +
                    resourceType + "\r\n" +
                    "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"" + resourceName + "\"\r\n" +
                    "Content-Type: application/octet-stream\r\n\r\n";

            byte[] prefix = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] requestBody = new byte[prefix.length + data.length + suffix.length];
            System.arraycopy(prefix, 0, requestBody, 0, prefix.length);
            System.arraycopy(data, 0, requestBody, prefix.length, data.length);
            System.arraycopy(suffix, 0, requestBody, prefix.length + data.length, suffix.length);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/resources"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 201) {
                    // Parse resourceId from JSON response
                    String responseBody = response.body();
                    int idStart = responseBody.indexOf("\"resourceId\":") + 13;
                    int idEnd = responseBody.indexOf(",", idStart);
                    if (idEnd == -1) idEnd = responseBody.indexOf("}", idStart);
                    return Long.parseLong(responseBody.substring(idStart, idEnd).trim());
                }
                throw new IOException("Upload failed with status: " + response.statusCode() + ", body: " + response.body());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Upload interrupted", e);
            }
        }

        @Override
        public Optional<Resource> downloadResource(long resourceId) throws IOException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/resources/" + resourceId + "/data"))
                    .GET()
                    .build();

            try {
                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 200) {
                    return Optional.of(new Resource(resourceId, response.body(), "TEXTURE"));
                } else if (response.statusCode() == 404) {
                    return Optional.empty();
                }
                throw new IOException("Download failed with status: " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Download interrupted", e);
            }
        }

        @Override
        public byte[] downloadChunk(long resourceId, int chunkIndex, int chunkSize) throws IOException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/resources/" + resourceId + "/chunks/" + chunkIndex + "?chunkSize=" + chunkSize))
                    .GET()
                    .build();

            try {
                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 200) {
                    return response.body();
                }
                throw new IOException("Chunk download failed with status: " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Download interrupted", e);
            }
        }

        @Override
        public int getTotalChunks(long resourceId, int chunkSize) throws IOException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/resources/" + resourceId + "/chunks?chunkSize=" + chunkSize))
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    String body = response.body();
                    int start = body.indexOf("\"totalChunks\":") + 14;
                    int end = body.indexOf(",", start);
                    if (end == -1) end = body.indexOf("}", start);
                    return Integer.parseInt(body.substring(start, end).trim());
                }
                throw new IOException("Failed to get chunk info with status: " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        @Override
        public List<Resource> listResources() throws IOException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/resources"))
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return parseResourceList(response.body());
                }
                throw new IOException("List failed with status: " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        private List<Resource> parseResourceList(String json) {
            List<Resource> resources = new java.util.ArrayList<>();
            if (json == null || json.isBlank() || json.equals("[]")) {
                return resources;
            }

            // Simple JSON array parsing - each object has resourceId, resourceName, resourceType, size
            // Format: [{"resourceId":1,"resourceName":"texture.png","resourceType":"TEXTURE","size":1024},...]
            String content = json.trim();
            if (content.startsWith("[")) {
                content = content.substring(1);
            }
            if (content.endsWith("]")) {
                content = content.substring(0, content.length() - 1);
            }

            // Split by },{ to get individual objects
            int braceDepth = 0;
            int objectStart = -1;
            for (int i = 0; i < content.length(); i++) {
                char c = content.charAt(i);
                if (c == '{') {
                    if (braceDepth == 0) {
                        objectStart = i;
                    }
                    braceDepth++;
                } else if (c == '}') {
                    braceDepth--;
                    if (braceDepth == 0 && objectStart >= 0) {
                        String objectJson = content.substring(objectStart, i + 1);
                        Resource resource = parseResourceObject(objectJson);
                        if (resource != null) {
                            resources.add(resource);
                        }
                        objectStart = -1;
                    }
                }
            }

            return resources;
        }

        private Resource parseResourceObject(String json) {
            try {
                long resourceId = extractLongField(json, "resourceId");
                String resourceName = extractStringField(json, "resourceName");
                String resourceType = extractStringField(json, "resourceType");
                // We don't download the blob for list operations
                return new Resource(resourceId, new byte[0], resourceType, resourceName);
            } catch (Exception e) {
                return null;
            }
        }

        private long extractLongField(String json, String fieldName) {
            String pattern = "\"" + fieldName + "\":";
            int start = json.indexOf(pattern);
            if (start == -1) return 0;
            start += pattern.length();
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
                end++;
            }
            return Long.parseLong(json.substring(start, end).trim());
        }

        private String extractStringField(String json, String fieldName) {
            String pattern = "\"" + fieldName + "\":\"";
            int start = json.indexOf(pattern);
            if (start == -1) return "";
            start += pattern.length();
            int end = json.indexOf("\"", start);
            if (end == -1) return "";
            return json.substring(start, end);
        }

        @Override
        public boolean deleteResource(long resourceId) throws IOException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/resources/" + resourceId))
                    .DELETE()
                    .build();

            try {
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                return response.statusCode() == 204;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Delete interrupted", e);
            }
        }
    }
}
