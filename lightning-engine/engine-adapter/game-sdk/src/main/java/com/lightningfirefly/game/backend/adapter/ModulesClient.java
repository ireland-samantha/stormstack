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

/**
 * Fluent client for module operations.
 *
 * <p>Usage:
 * <pre>{@code
 * // List all modules
 * var modules = client.modules().list();
 *
 * // Get a specific module
 * var module = client.modules().get("EntityModule");
 *
 * // Check if module exists
 * boolean exists = client.modules().exists("RenderModule");
 *
 * // Reload modules
 * client.modules().reload();
 * }</pre>
 */
@Slf4j
public final class ModulesClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    ModulesClient(String baseUrl, HttpClient httpClient, Duration requestTimeout) {
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
    }

    /**
     * List all installed modules.
     */
    public List<Module> list() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/modules"))
                    .timeout(requestTimeout)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return parseModuleList(response.body());
            }
            throw new IOException("List modules failed: " + response.statusCode());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list modules", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted", e);
        }
    }

    /**
     * Get a specific module by name.
     */
    public Optional<Module> get(String moduleName) {
        return list().stream()
                .filter(m -> m.name().equals(moduleName))
                .findFirst();
    }

    /**
     * Check if a module exists.
     */
    public boolean exists(String moduleName) {
        return get(moduleName).isPresent();
    }

    /**
     * Get all module names.
     */
    public List<String> names() {
        return list().stream().map(Module::name).toList();
    }

    /**
     * Reload all modules from disk.
     */
    public void reload() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/modules/reload"))
                    .timeout(requestTimeout)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 400) {
                throw new IOException("Reload modules failed: " + response.statusCode());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to reload modules", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted", e);
        }
    }

    private List<Module> parseModuleList(String json) {
        List<Module> modules = new ArrayList<>();

        // Parse array of modules
        int pos = 0;
        while ((pos = json.indexOf("\"name\":", pos)) != -1) {
            int nameStart = json.indexOf("\"", pos + 7);
            int nameEnd = json.indexOf("\"", nameStart + 1);
            if (nameStart == -1 || nameEnd == -1) break;

            String name = json.substring(nameStart + 1, nameEnd);

            // Try to find description
            String description = "";
            int descPos = json.indexOf("\"description\":", nameEnd);
            if (descPos != -1 && descPos < json.indexOf("}", nameEnd)) {
                int descStart = json.indexOf("\"", descPos + 14);
                int descEnd = json.indexOf("\"", descStart + 1);
                if (descStart != -1 && descEnd != -1) {
                    description = json.substring(descStart + 1, descEnd);
                }
            }

            modules.add(new Module(name, description));
            pos = nameEnd + 1;
        }

        return modules;
    }

    /**
     * Module information.
     */
    public record Module(String name, String description) {}
}
