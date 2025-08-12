package com.lightningfirefly.engine.gui.service;

import com.lightningfirefly.engine.api.resource.Resource;
import com.lightningfirefly.engine.api.resource.adapter.ResourceAdapter;
import com.lightningfirefly.engine.api.resource.adapter.ResourceAdapter.HttpResourceAdapter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Service for managing resources via the REST API.
 */
@Slf4j
public class ResourceService {

    private final ResourceAdapter resourceAdapter;
    private final ExecutorService executor;
    private final List<Consumer<ResourceEvent>> listeners = new ArrayList<>();

    public ResourceService(String serverUrl) {
        this.resourceAdapter = new HttpResourceAdapter(serverUrl);
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ResourceService-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    public ResourceService(ResourceAdapter resourceAdapter) {
        this.resourceAdapter = resourceAdapter;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ResourceService-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Add a listener for resource events.
     */
    public void addListener(Consumer<ResourceEvent> listener) {
        listeners.add(listener);
    }

    /**
     * Remove a listener.
     */
    public void removeListener(Consumer<ResourceEvent> listener) {
        listeners.remove(listener);
    }

    /**
     * List all resources asynchronously.
     */
    public CompletableFuture<List<ResourceInfo>> listResources() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Resource> resources = resourceAdapter.listResources();
                return resources.stream()
                    .map(r -> new ResourceInfo(r.resourceId(), r.resourceName(), r.resourceType()))
                    .toList();
            } catch (IOException e) {
                notifyListeners(new ResourceEvent(ResourceEventType.ERROR, -1, e.getMessage()));
                return List.of();
            }
        }, executor);
    }

    /**
     * Upload a resource asynchronously.
     */
    public CompletableFuture<Long> uploadResource(String name, String type, byte[] data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long resourceId = resourceAdapter.uploadResource(name, type, data);
                notifyListeners(new ResourceEvent(ResourceEventType.UPLOADED, resourceId, name));
                return resourceId;
            } catch (IOException e) {
                notifyListeners(new ResourceEvent(ResourceEventType.ERROR, -1, "Upload failed: " + e.getMessage()));
                throw new RuntimeException(e);
            }
        }, executor);
    }

    /**
     * Upload a resource from a file path asynchronously.
     */
    public CompletableFuture<Long> uploadResourceFromFile(Path filePath, String type) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String name = filePath.getFileName().toString();
                byte[] data = Files.readAllBytes(filePath);
                long resourceId = resourceAdapter.uploadResource(name, type, data);
                notifyListeners(new ResourceEvent(ResourceEventType.UPLOADED, resourceId, name));
                return resourceId;
            } catch (IOException e) {
                notifyListeners(new ResourceEvent(ResourceEventType.ERROR, -1, "Upload failed: " + e.getMessage()));
                throw new RuntimeException(e);
            }
        }, executor);
    }

    /**
     * Download a resource asynchronously.
     */
    public CompletableFuture<Optional<byte[]>> downloadResource(long resourceId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<Resource> resource = resourceAdapter.downloadResource(resourceId);
                if (resource.isPresent()) {
                    notifyListeners(new ResourceEvent(ResourceEventType.DOWNLOADED, resourceId, null));
                    return Optional.of(resource.get().blob());
                }
                return Optional.empty();
            } catch (IOException e) {
                notifyListeners(new ResourceEvent(ResourceEventType.ERROR, resourceId, "Download failed: " + e.getMessage()));
                return Optional.empty();
            }
        }, executor);
    }

    /**
     * Download a resource to a file asynchronously.
     */
    public CompletableFuture<Boolean> downloadResourceToFile(long resourceId, Path targetPath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<Resource> resource = resourceAdapter.downloadResource(resourceId);
                if (resource.isPresent()) {
                    Files.write(targetPath, resource.get().blob());
                    notifyListeners(new ResourceEvent(ResourceEventType.DOWNLOADED, resourceId, targetPath.toString()));
                    return true;
                }
                return false;
            } catch (IOException e) {
                notifyListeners(new ResourceEvent(ResourceEventType.ERROR, resourceId, "Download failed: " + e.getMessage()));
                return false;
            }
        }, executor);
    }

    /**
     * Delete a resource asynchronously.
     */
    public CompletableFuture<Boolean> deleteResource(long resourceId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean deleted = resourceAdapter.deleteResource(resourceId);
                if (deleted) {
                    notifyListeners(new ResourceEvent(ResourceEventType.DELETED, resourceId, null));
                }
                return deleted;
            } catch (IOException e) {
                notifyListeners(new ResourceEvent(ResourceEventType.ERROR, resourceId, "Delete failed: " + e.getMessage()));
                return false;
            }
        }, executor);
    }

    /**
     * Shutdown the service.
     */
    public void shutdown() {
        executor.shutdown();
    }

    private void notifyListeners(ResourceEvent event) {
        for (Consumer<ResourceEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.error("Error in resource listener: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Resource information.
     */
    public record ResourceInfo(long id, String name, String type) {}

    /**
     * Resource event type.
     */
    public enum ResourceEventType {
        UPLOADED,
        DOWNLOADED,
        DELETED,
        ERROR
    }

    /**
     * Resource event.
     */
    public record ResourceEvent(ResourceEventType type, long resourceId, String message) {}
}
