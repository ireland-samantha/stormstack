package com.lightningfirefly.games.common.resource;

import com.lightningfirefly.engine.api.resource.Resource;
import com.lightningfirefly.engine.api.resource.adapter.ResourceAdapter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Implementation of {@link LazyResourceLoader} using HTTP REST API.
 *
 * <p>Caches resources in memory and loads them on-demand from the backend.
 */
@Slf4j
public class HttpLazyResourceLoader implements LazyResourceLoader, AutoCloseable {

    private final ResourceAdapter resourceAdapter;
    private final Map<String, GameResource> cache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Optional<GameResource>>> pendingLoads = new ConcurrentHashMap<>();
    private final List<Consumer<ResourceLoadEvent>> loadListeners = new CopyOnWriteArrayList<>();
    private final ExecutorService executor;

    // Index from name to ID for faster lookups
    private volatile Map<String, Long> resourceIndex;

    public HttpLazyResourceLoader(ResourceAdapter resourceAdapter) {
        this.resourceAdapter = resourceAdapter;
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "ResourceLoader");
            t.setDaemon(true);
            return t;
        });
    }

    public HttpLazyResourceLoader(String serverUrl) {
        this(new ResourceAdapter.HttpResourceAdapter(serverUrl));
    }

    @Override
    public CompletableFuture<Optional<GameResource>> getResource(String resourceName) {
        // Check cache first
        GameResource cached = cache.get(resourceName);
        if (cached != null) {
            notifyListeners(new ResourceLoadEvent(
                    resourceName,
                    ResourceLoadEvent.EventType.CACHE_HIT,
                    Optional.of(cached)
            ));
            return CompletableFuture.completedFuture(Optional.of(cached));
        }

        // Check if already loading
        CompletableFuture<Optional<GameResource>> pending = pendingLoads.get(resourceName);
        if (pending != null) {
            return pending;
        }

        // Start loading
        CompletableFuture<Optional<GameResource>> future = CompletableFuture.supplyAsync(() -> {
            notifyListeners(new ResourceLoadEvent(
                    resourceName,
                    ResourceLoadEvent.EventType.LOAD_STARTED,
                    Optional.empty()
            ));

            try {
                // Ensure index is loaded
                ensureIndexLoaded();

                Long resourceId = resourceIndex.get(resourceName);
                if (resourceId == null) {
                    log.warn("Resource not found: {}", resourceName);
                    notifyListeners(new ResourceLoadEvent(
                            resourceName,
                            ResourceLoadEvent.EventType.LOAD_FAILED,
                            Optional.empty()
                    ));
                    return Optional.empty();
                }

                Optional<Resource> resource = resourceAdapter.downloadResource(resourceId);
                if (resource.isPresent()) {
                    Resource r = resource.get();
                    GameResource gameResource = new GameResource(
                            r.resourceId(),
                            resourceName,
                            parseResourceType(r.resourceType()),
                            r.blob()
                    );
                    cache.put(resourceName, gameResource);
                    notifyListeners(new ResourceLoadEvent(
                            resourceName,
                            ResourceLoadEvent.EventType.LOAD_COMPLETED,
                            Optional.of(gameResource)
                    ));
                    return Optional.of(gameResource);
                }

                notifyListeners(new ResourceLoadEvent(
                        resourceName,
                        ResourceLoadEvent.EventType.LOAD_FAILED,
                        Optional.empty()
                ));
                return Optional.empty();

            } catch (IOException e) {
                log.error("Failed to load resource {}: {}", resourceName, e.getMessage());
                notifyListeners(new ResourceLoadEvent(
                        resourceName,
                        ResourceLoadEvent.EventType.LOAD_FAILED,
                        Optional.empty()
                ));
                return Optional.empty();
            } finally {
                pendingLoads.remove(resourceName);
            }
        }, executor);

        pendingLoads.put(resourceName, future);
        return future;
    }

    @Override
    public boolean isLoaded(String resourceName) {
        return cache.containsKey(resourceName);
    }

    @Override
    public CompletableFuture<Void> preload(String resourceName) {
        return getResource(resourceName).thenAccept(r -> {});
    }

    @Override
    public CompletableFuture<Void> preloadAll(String... resourceNames) {
        CompletableFuture<?>[] futures = new CompletableFuture[resourceNames.length];
        for (int i = 0; i < resourceNames.length; i++) {
            futures[i] = preload(resourceNames[i]);
        }
        return CompletableFuture.allOf(futures);
    }

    @Override
    public CompletableFuture<Long> uploadResource(String name, GameResource.ResourceType type, byte[] data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long id = resourceAdapter.uploadResource(name, type.name(), data);
                // Update index
                if (resourceIndex != null) {
                    resourceIndex.put(name, id);
                }
                // Add to cache
                cache.put(name, new GameResource(id, name, type, data));
                log.info("Uploaded resource {} with ID {}", name, id);
                return id;
            } catch (IOException e) {
                log.error("Failed to upload resource {}: {}", name, e.getMessage());
                throw new CompletionException(e);
            }
        }, executor);
    }

    @Override
    public void clearCache() {
        cache.clear();
        resourceIndex = null;
    }

    @Override
    public void addLoadListener(Consumer<ResourceLoadEvent> listener) {
        loadListeners.add(listener);
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void ensureIndexLoaded() throws IOException {
        if (resourceIndex == null) {
            synchronized (this) {
                if (resourceIndex == null) {
                    Map<String, Long> index = new ConcurrentHashMap<>();
                    List<Resource> resources = resourceAdapter.listResources();
                    for (Resource r : resources) {
                        if (r.resourceName() != null) {
                            index.put(r.resourceName(), r.resourceId());
                        }
                    }
                    resourceIndex = index;
                    log.info("Loaded resource index with {} entries", index.size());
                }
            }
        }
    }

    private GameResource.ResourceType parseResourceType(String type) {
        if (type == null) return GameResource.ResourceType.TEXTURE;
        try {
            return GameResource.ResourceType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return GameResource.ResourceType.TEXTURE;
        }
    }

    private void notifyListeners(ResourceLoadEvent event) {
        for (Consumer<ResourceLoadEvent> listener : loadListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.error("Error in resource load listener: {}", e.getMessage(), e);
            }
        }
    }
}
