package com.lightningfirefly.game.orchestrator.resource;

import com.lightningfirefly.engine.api.resource.Resource;
import com.lightningfirefly.engine.api.resource.adapter.ResourceAdapter;
import com.lightningfirefly.game.orchestrator.ResourceDownloadEvent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * ResourceProvider implementation that delegates to a ResourceAdapter.
 * <p>This is the base implementation that performs direct server operations
 * without any caching. Use {@link CachingResourceProvider} to add caching.
 */
public class ResourceProviderAdapter implements ResourceProvider {

    private final ResourceAdapter resourceAdapter;
    private Consumer<ResourceDownloadEvent> downloadListener;

    /**
     * Create a new adapter wrapping a ResourceAdapter.
     *
     * @param resourceAdapter the underlying resource adapter
     */
    public ResourceProviderAdapter(ResourceAdapter resourceAdapter) {
        this.resourceAdapter = resourceAdapter;
    }

    @Override
    public long uploadResource(String name, String type, byte[] data) throws IOException {
        return resourceAdapter.uploadResource(name, type, data);
    }

    @Override
    public Optional<ResourceData> downloadResource(long resourceId) throws IOException {
        Optional<Resource> resourceOpt = resourceAdapter.downloadResource(resourceId);
        return resourceOpt.map(r -> new ResourceData(
                r.resourceId(),
                r.resourceName(),
                r.resourceType(),
                r.blob()
        ));
    }

    @Override
    public void deleteResource(long resourceId) throws IOException {
        resourceAdapter.deleteResource(resourceId);
    }

    @Override
    public void ensureResource(long resourceId) {
        // No caching - this is a no-op for the base adapter
        // The CachingResourceProvider decorator handles this
    }

    @Override
    public Optional<Path> getLocalPath(long resourceId) {
        // No caching - always empty for base adapter
        return Optional.empty();
    }

    @Override
    public boolean isAvailableLocally(long resourceId) {
        // No caching - never available locally for base adapter
        return false;
    }

    @Override
    public void setDownloadListener(Consumer<ResourceDownloadEvent> listener) {
        this.downloadListener = listener;
    }

    /**
     * Get the current download listener.
     *
     * @return the download listener, or null if not set
     */
    protected Consumer<ResourceDownloadEvent> getDownloadListener() {
        return downloadListener;
    }

    @Override
    public void clearCache() {
        // No cache to clear for base adapter
    }

    @Override
    public Optional<Path> getCacheDirectory() {
        // No cache directory for base adapter
        return Optional.empty();
    }

    @Override
    public void close() {
        // No resources to release for base adapter
    }
}
