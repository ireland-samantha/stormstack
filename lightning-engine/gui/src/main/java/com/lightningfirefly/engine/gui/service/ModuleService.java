package com.lightningfirefly.engine.gui.service;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for managing modules via REST API.
 */
@Slf4j
public class ModuleService {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final List<Consumer<ModuleEvent>> listeners = new CopyOnWriteArrayList<>();

    public ModuleService(String serverUrl) {
        this.baseUrl = serverUrl + "/api/modules";
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * Add a listener for module events.
     */
    public void addListener(Consumer<ModuleEvent> listener) {
        listeners.add(listener);
    }

    /**
     * Get all available modules.
     */
    public CompletableFuture<List<ModuleInfo>> listModules() {
        log.debug("Fetching modules from: {}", baseUrl);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .GET()
                .header("Accept", "application/json")
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    log.debug("Modules response: status={}, body={}", response.statusCode(), response.body());
                    if (response.statusCode() == 200) {
                        List<ModuleInfo> modules = parseModuleList(response.body());
                        log.info("Parsed {} modules from response", modules.size());
                        return modules;
                    } else {
                        log.error("Failed to list modules: {} {}", response.statusCode(), response.body());
                        notifyListeners(new ModuleEvent(ModuleEventType.ERROR, null,
                                "Failed to list modules: " + response.statusCode()));
                        return new ArrayList<ModuleInfo>();
                    }
                })
                .exceptionally(e -> {
                    log.error("Failed to list modules: {}", e.getMessage(), e);
                    notifyListeners(new ModuleEvent(ModuleEventType.ERROR, null, e.getMessage()));
                    return new ArrayList<ModuleInfo>();
                });
    }

    /**
     * Upload a JAR module.
     */
    public CompletableFuture<Boolean> uploadModule(Path jarPath) {
        try {
            String fileName = jarPath.getFileName().toString();
            byte[] fileBytes = Files.readAllBytes(jarPath);

            String boundary = "---WebKitFormBoundary" + System.currentTimeMillis();
            String body = buildMultipartBody(boundary, fileName, fileBytes);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/upload"))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.getBytes()))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() == 201) {
                            log.info("Uploaded module: {}", fileName);
                            notifyListeners(new ModuleEvent(ModuleEventType.UPLOADED, fileName,
                                    "Module " + fileName + " uploaded"));
                            return true;
                        } else {
                            log.error("Failed to upload module: {} {}", response.statusCode(), response.body());
                            notifyListeners(new ModuleEvent(ModuleEventType.ERROR, fileName,
                                    "Failed to upload: " + response.statusCode()));
                            return false;
                        }
                    })
                    .exceptionally(e -> {
                        log.error("Failed to upload module: {}", fileName, e);
                        notifyListeners(new ModuleEvent(ModuleEventType.ERROR, fileName, e.getMessage()));
                        return false;
                    });
        } catch (IOException e) {
            log.error("Failed to read JAR file: {}", jarPath, e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Uninstall a module.
     */
    public CompletableFuture<Boolean> uninstallModule(String moduleName) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/" + moduleName))
                .DELETE()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 204) {
                        log.info("Uninstalled module: {}", moduleName);
                        notifyListeners(new ModuleEvent(ModuleEventType.UNINSTALLED, moduleName,
                                "Module " + moduleName + " uninstalled"));
                        return true;
                    } else {
                        log.error("Failed to uninstall module {}: {}", moduleName, response.statusCode());
                        notifyListeners(new ModuleEvent(ModuleEventType.ERROR, moduleName,
                                "Failed to uninstall: " + response.statusCode()));
                        return false;
                    }
                })
                .exceptionally(e -> {
                    log.error("Failed to uninstall module: {}", moduleName, e);
                    notifyListeners(new ModuleEvent(ModuleEventType.ERROR, moduleName, e.getMessage()));
                    return false;
                });
    }

    /**
     * Reload all modules.
     */
    public CompletableFuture<Boolean> reloadModules() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/reload"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        log.info("Reloaded modules");
                        notifyListeners(new ModuleEvent(ModuleEventType.RELOADED, null,
                                "Modules reloaded"));
                        return true;
                    } else {
                        log.error("Failed to reload modules: {}", response.statusCode());
                        notifyListeners(new ModuleEvent(ModuleEventType.ERROR, null,
                                "Failed to reload: " + response.statusCode()));
                        return false;
                    }
                })
                .exceptionally(e -> {
                    log.error("Failed to reload modules", e);
                    notifyListeners(new ModuleEvent(ModuleEventType.ERROR, null, e.getMessage()));
                    return false;
                });
    }

    private void notifyListeners(ModuleEvent event) {
        for (Consumer<ModuleEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.error("Error in module event listener", e);
            }
        }
    }

    private String buildMultipartBody(String boundary, String fileName, byte[] fileBytes) {
        StringBuilder sb = new StringBuilder();
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(fileName).append("\"\r\n");
        sb.append("Content-Type: application/java-archive\r\n");
        sb.append("\r\n");
        sb.append(new String(fileBytes));
        sb.append("\r\n");
        sb.append("--").append(boundary).append("--\r\n");
        return sb.toString();
    }

    private List<ModuleInfo> parseModuleList(String json) {
        List<ModuleInfo> modules = new ArrayList<>();
        // Split on },{  to separate array elements, then parse each object
        // Match individual JSON objects in the array
        Pattern objectPattern = Pattern.compile("\\{[^{}]+\\}");
        Matcher objectMatcher = objectPattern.matcher(json);

        while (objectMatcher.find()) {
            String obj = objectMatcher.group();

            // Extract individual fields (order-independent)
            String name = extractStringField(obj, "name");
            String flagComponent = extractStringField(obj, "flagComponentName");
            int enabledMatches = extractIntField(obj, "enabledMatches", 0);

            if (name != null) {
                modules.add(new ModuleInfo(name, flagComponent, enabledMatches));
            }
        }
        return modules;
    }

    private String extractStringField(String json, String fieldName) {
        // Match "fieldName":"value" or "fieldName":null
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*(?:\"([^\"]*)\"|null)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1); // Returns null if matched "null" literal
        }
        return null;
    }

    private int extractIntField(String json, String fieldName, int defaultValue) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return defaultValue;
    }

    /**
     * Shutdown the service.
     */
    public void shutdown() {
        // HttpClient doesn't need explicit shutdown
    }

    /**
     * Module information record.
     */
    public record ModuleInfo(String name, String flagComponent, int enabledMatches) {}

    /**
     * Module event types.
     */
    public enum ModuleEventType {
        UPLOADED, UNINSTALLED, RELOADED, ERROR
    }

    /**
     * Module event record.
     */
    public record ModuleEvent(ModuleEventType type, String moduleName, String message) {}
}
