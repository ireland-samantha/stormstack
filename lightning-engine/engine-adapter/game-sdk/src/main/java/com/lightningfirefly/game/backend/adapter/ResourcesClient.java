package com.lightningfirefly.game.backend.adapter;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Fluent client for resource operations.
 *
 * <p>Usage:
 * <pre>{@code
 * // List resources
 * var resources = client.resources().list();
 *
 * // Upload a resource
 * var resource = client.resources().upload()
 *     .name("player-sprite.png")
 *     .type("TEXTURE")
 *     .data(imageBytes)
 *     .execute();
 *
 * // Download a resource
 * byte[] data = client.resources().download(resourceId);
 *
 * // Delete a resource
 * client.resources().delete(resourceId);
 * }</pre>
 */
@Slf4j
public final class ResourcesClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    ResourcesClient(String baseUrl, HttpClient httpClient, Duration requestTimeout) {
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
    }

    /**
     * List all resources.
     */
    public List<Resource> list() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/resources"))
                    .timeout(requestTimeout)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return parseResourceList(response.body());
            }
            throw new IOException("List resources failed: " + response.statusCode());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list resources", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted", e);
        }
    }

    /**
     * Get a resource by ID.
     */
    public Optional<Resource> get(long resourceId) {
        return list().stream()
                .filter(r -> r.id() == resourceId)
                .findFirst();
    }

    /**
     * Start an upload operation.
     */
    public UploadBuilder upload() {
        return new UploadBuilder(this);
    }

    /**
     * Download a resource's data.
     */
    public byte[] download(long resourceId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/resources/" + resourceId + "/download"))
                    .timeout(requestTimeout)
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                return response.body();
            }
            throw new IOException("Download resource failed: " + response.statusCode());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to download resource " + resourceId, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted", e);
        }
    }

    /**
     * Delete a resource.
     */
    public boolean delete(long resourceId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/resources/" + resourceId))
                    .timeout(requestTimeout)
                    .DELETE()
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 204 || response.statusCode() == 200;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete resource " + resourceId, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted", e);
        }
    }

    Resource uploadResource(String name, String type, byte[] data) {
        try {
            // Build multipart request
            String boundary = UUID.randomUUID().toString();
            StringBuilder body = new StringBuilder();

            // File part
            body.append("--").append(boundary).append("\r\n");
            body.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(name).append("\"\r\n");
            body.append("Content-Type: application/octet-stream\r\n\r\n");

            // We need to handle binary data specially
            byte[] prefix = body.toString().getBytes();

            StringBuilder suffix = new StringBuilder();
            suffix.append("\r\n");

            // resourceName part
            suffix.append("--").append(boundary).append("\r\n");
            suffix.append("Content-Disposition: form-data; name=\"resourceName\"\r\n\r\n");
            suffix.append(name).append("\r\n");

            // resourceType part
            suffix.append("--").append(boundary).append("\r\n");
            suffix.append("Content-Disposition: form-data; name=\"resourceType\"\r\n\r\n");
            suffix.append(type).append("\r\n");

            suffix.append("--").append(boundary).append("--\r\n");

            byte[] suffixBytes = suffix.toString().getBytes();

            // Combine all parts
            byte[] fullBody = new byte[prefix.length + data.length + suffixBytes.length];
            System.arraycopy(prefix, 0, fullBody, 0, prefix.length);
            System.arraycopy(data, 0, fullBody, prefix.length, data.length);
            System.arraycopy(suffixBytes, 0, fullBody, prefix.length + data.length, suffixBytes.length);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/resources"))
                    .timeout(requestTimeout)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(fullBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 201 || response.statusCode() == 200) {
                return parseResource(response.body());
            }
            throw new IOException("Upload resource failed: " + response.statusCode() + " - " + response.body());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to upload resource " + name, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted", e);
        }
    }

    private List<Resource> parseResourceList(String json) {
        List<Resource> resources = new ArrayList<>();

        int pos = 0;
        while ((pos = json.indexOf("\"resourceId\":", pos)) != -1) {
            int idEnd = json.indexOf(",", pos);
            if (idEnd == -1) idEnd = json.indexOf("}", pos);
            long id = Long.parseLong(json.substring(pos + 13, idEnd).trim());

            String name = "";
            int namePos = json.indexOf("\"resourceName\":", pos);
            if (namePos != -1 && namePos < json.indexOf("}", pos) + 20) {
                int nameStart = json.indexOf("\"", namePos + 15);
                int nameEnd = json.indexOf("\"", nameStart + 1);
                if (nameStart != -1 && nameEnd != -1) {
                    name = json.substring(nameStart + 1, nameEnd);
                }
            }

            String type = "";
            int typePos = json.indexOf("\"resourceType\":", pos);
            if (typePos != -1 && typePos < json.indexOf("}", pos) + 40) {
                int typeStart = json.indexOf("\"", typePos + 15);
                int typeEnd = json.indexOf("\"", typeStart + 1);
                if (typeStart != -1 && typeEnd != -1) {
                    type = json.substring(typeStart + 1, typeEnd);
                }
            }

            resources.add(new Resource(id, name, type));
            pos = idEnd + 1;
        }

        return resources;
    }

    private Resource parseResource(String json) {
        long id = 0;
        int idPos = json.indexOf("\"resourceId\":");
        if (idPos != -1) {
            int idEnd = json.indexOf(",", idPos);
            if (idEnd == -1) idEnd = json.indexOf("}", idPos);
            id = Long.parseLong(json.substring(idPos + 13, idEnd).trim());
        }

        String name = "";
        int namePos = json.indexOf("\"resourceName\":");
        if (namePos != -1) {
            int nameStart = json.indexOf("\"", namePos + 15);
            int nameEnd = json.indexOf("\"", nameStart + 1);
            if (nameStart != -1 && nameEnd != -1) {
                name = json.substring(nameStart + 1, nameEnd);
            }
        }

        String type = "";
        int typePos = json.indexOf("\"resourceType\":");
        if (typePos != -1) {
            int typeStart = json.indexOf("\"", typePos + 15);
            int typeEnd = json.indexOf("\"", typeStart + 1);
            if (typeStart != -1 && typeEnd != -1) {
                type = json.substring(typeStart + 1, typeEnd);
            }
        }

        return new Resource(id, name, type);
    }

    /**
     * Resource information.
     */
    public record Resource(long id, String name, String type) {}

    /**
     * Builder for resource uploads.
     */
    public static class UploadBuilder {
        private final ResourcesClient client;
        private String name;
        private String type = "TEXTURE";
        private byte[] data;

        UploadBuilder(ResourcesClient client) {
            this.client = client;
        }

        public UploadBuilder name(String name) {
            this.name = name;
            return this;
        }

        public UploadBuilder type(String type) {
            this.type = type;
            return this;
        }

        public UploadBuilder data(byte[] data) {
            this.data = data;
            return this;
        }

        public Resource execute() {
            if (name == null) throw new IllegalStateException("name is required");
            if (data == null) throw new IllegalStateException("data is required");
            return client.uploadResource(name, type, data);
        }
    }
}
