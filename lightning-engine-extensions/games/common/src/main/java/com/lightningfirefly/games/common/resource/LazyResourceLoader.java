package com.lightningfirefly.games.common.resource;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Interface for lazy-loading game resources.
 *
 * <p>Resources are loaded on-demand from the backend API and cached locally.
 * This supports efficient resource management for large game assets.
 */
public interface LazyResourceLoader {

    /**
     * Get a resource, loading it if necessary.
     *
     * <p>If the resource is not in cache, it will be loaded asynchronously.
     * The returned future completes when the resource is available.
     *
     * @param resourceName the resource name
     * @return future with the resource
     */
    CompletableFuture<Optional<GameResource>> getResource(String resourceName);

    /**
     * Check if a resource is loaded in cache.
     *
     * @param resourceName the resource name
     * @return true if loaded
     */
    boolean isLoaded(String resourceName);

    /**
     * Preload a resource into cache.
     *
     * @param resourceName the resource name
     * @return future that completes when preloaded
     */
    CompletableFuture<Void> preload(String resourceName);

    /**
     * Preload multiple resources.
     *
     * @param resourceNames the resource names
     * @return future that completes when all are preloaded
     */
    CompletableFuture<Void> preloadAll(String... resourceNames);

    /**
     * Upload a resource to the backend.
     *
     * @param name the resource name
     * @param type the resource type
     * @param data the resource data
     * @return future with the created resource ID
     */
    CompletableFuture<Long> uploadResource(String name, GameResource.ResourceType type, byte[] data);

    /**
     * Clear cached resources.
     */
    void clearCache();

    /**
     * Add a listener for resource load events.
     *
     * @param listener the listener
     */
    void addLoadListener(Consumer<ResourceLoadEvent> listener);

    /**
     * Resource load event.
     */
    record ResourceLoadEvent(
            String resourceName,
            EventType type,
            Optional<GameResource> resource
    ) {
        public enum EventType {
            LOAD_STARTED,
            LOAD_COMPLETED,
            LOAD_FAILED,
            CACHE_HIT
        }
    }
}
