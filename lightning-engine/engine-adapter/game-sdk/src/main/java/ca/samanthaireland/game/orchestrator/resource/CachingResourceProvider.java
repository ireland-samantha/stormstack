/*
 * Copyright (c) 2026 Samantha Ireland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */


package ca.samanthaireland.game.orchestrator.resource;

import ca.samanthaireland.game.orchestrator.ResourceDownloadEvent;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Decorator that adds local disk caching to a ResourceProvider.
 * <p>Downloads resources asynchronously and caches them to disk for fast access.
 * Thread-safe for concurrent resource requests.
 */
@Slf4j
public class CachingResourceProvider implements ResourceProvider {

    private final ResourceProvider delegate;
    private final Path cacheDirectory;
    private final ExecutorService downloadExecutor;

    private final Set<Long> cachedResourceIds = ConcurrentHashMap.newKeySet();
    private final Set<Long> pendingDownloads = ConcurrentHashMap.newKeySet();
    private final Map<Long, Path> resourceIdToPath = new ConcurrentHashMap<>();

    private Consumer<ResourceDownloadEvent> downloadListener;

    /**
     * Create a caching decorator with default thread pool.
     *
     * @param delegate       the underlying resource provider
     * @param cacheDirectory the directory to cache resources to
     */
    public CachingResourceProvider(ResourceProvider delegate, Path cacheDirectory) {
        this(delegate, cacheDirectory, createDefaultExecutor());
    }

    /**
     * Create a caching decorator with custom executor.
     *
     * @param delegate         the underlying resource provider
     * @param cacheDirectory   the directory to cache resources to
     * @param downloadExecutor the executor for async downloads
     */
    public CachingResourceProvider(ResourceProvider delegate, Path cacheDirectory, ExecutorService downloadExecutor) {
        this.delegate = delegate;
        this.cacheDirectory = cacheDirectory;
        this.downloadExecutor = downloadExecutor;
    }

    private static ExecutorService createDefaultExecutor() {
        return Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "ResourceDownloader");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public long uploadResource(String name, String type, byte[] data) throws IOException {
        return delegate.uploadResource(name, type, data);
    }

    @Override
    public Optional<ResourceData> downloadResource(long resourceId) throws IOException {
        // Check cache first
        Path cachedPath = resourceIdToPath.get(resourceId);
        if (cachedPath != null && Files.exists(cachedPath)) {
            byte[] data = Files.readAllBytes(cachedPath);
            // We don't have full metadata cached, return with minimal info
            return Optional.of(new ResourceData(resourceId, cachedPath.getFileName().toString(), "UNKNOWN", data));
        }

        // Download from delegate
        Optional<ResourceData> resourceOpt = delegate.downloadResource(resourceId);
        if (resourceOpt.isPresent()) {
            // Cache it
            cacheResource(resourceOpt.get());
        }
        return resourceOpt;
    }

    @Override
    public void deleteResource(long resourceId) throws IOException {
        delegate.deleteResource(resourceId);
        // Remove from cache
        cachedResourceIds.remove(resourceId);
        Path path = resourceIdToPath.remove(resourceId);
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("Failed to delete cached resource {}: {}", resourceId, e.getMessage());
            }
        }
    }

    @Override
    public void ensureResource(long resourceId) {
        if (cachedResourceIds.contains(resourceId) || pendingDownloads.contains(resourceId)) {
            return;
        }
        downloadResourceAsync(resourceId);
    }

    /**
     * Ensure multiple resources are available, downloading if necessary.
     *
     * @param resourceIds the resource IDs to ensure are available
     */
    public void ensureResources(Set<Long> resourceIds) {
        for (Long resourceId : resourceIds) {
            ensureResource(resourceId);
        }
    }

    @Override
    public Optional<Path> getLocalPath(long resourceId) {
        return Optional.ofNullable(resourceIdToPath.get(resourceId));
    }

    @Override
    public boolean isAvailableLocally(long resourceId) {
        return cachedResourceIds.contains(resourceId);
    }

    /**
     * Check if a resource download is pending.
     *
     * @param resourceId the resource ID
     * @return true if download is in progress
     */
    public boolean isDownloadPending(long resourceId) {
        return pendingDownloads.contains(resourceId);
    }

    @Override
    public void setDownloadListener(Consumer<ResourceDownloadEvent> listener) {
        this.downloadListener = listener;
        delegate.setDownloadListener(listener);
    }

    @Override
    public void clearCache() {
        cachedResourceIds.clear();
        resourceIdToPath.clear();
        delegate.clearCache();
    }

    @Override
    public Optional<Path> getCacheDirectory() {
        return Optional.of(cacheDirectory);
    }

    @Override
    public void close() {
        downloadExecutor.shutdownNow();
        clearCache();
        delegate.close();
    }

    /**
     * Download a resource asynchronously and cache to disk.
     */
    private void downloadResourceAsync(long resourceId) {
        if (!pendingDownloads.add(resourceId)) {
            return; // Already pending
        }

        CompletableFuture.runAsync(() -> {
            try {
                downloadAndCacheResource(resourceId);
            } catch (IOException e) {
                log.warn("Failed to download resource {}: {}", resourceId, e.getMessage());
                notifyListener(new ResourceDownloadEvent(
                        resourceId, null, ResourceDownloadEvent.Status.FAILED, e));
            } finally {
                pendingDownloads.remove(resourceId);
            }
        }, downloadExecutor);
    }

    /**
     * Download and cache a resource synchronously.
     */
    private void downloadAndCacheResource(long resourceId) throws IOException {
        Optional<ResourceData> resourceOpt = delegate.downloadResource(resourceId);
        if (resourceOpt.isEmpty()) {
            throw new IOException("Resource not found: " + resourceId);
        }

        ResourceData resource = resourceOpt.get();
        Path filePath = cacheResource(resource);

        notifyListener(new ResourceDownloadEvent(
                resourceId, filePath, ResourceDownloadEvent.Status.COMPLETED, null));
    }

    /**
     * Cache a resource to disk.
     */
    private Path cacheResource(ResourceData resource) throws IOException {
        Files.createDirectories(cacheDirectory);

        String fileName = determineFileName(resource);
        Path filePath = cacheDirectory.resolve(fileName);

        Files.write(filePath, resource.data());

        cachedResourceIds.add(resource.resourceId());
        resourceIdToPath.put(resource.resourceId(), filePath);

        log.debug("Cached resource {} to {}", resource.resourceId(), filePath);
        return filePath;
    }

    /**
     * Determine the file name for a resource.
     */
    private String determineFileName(ResourceData resource) {
        String name = resource.resourceName();
        if (name != null && !name.isBlank()) {
            return name;
        }

        String extension = switch (resource.resourceType()) {
            case "TEXTURE" -> ".png";
            case "AUDIO" -> ".wav";
            default -> ".bin";
        };
        return "resource_" + resource.resourceId() + extension;
    }

    private void notifyListener(ResourceDownloadEvent event) {
        Consumer<ResourceDownloadEvent> listener = this.downloadListener;
        if (listener != null) {
            listener.accept(event);
        }
    }
}
