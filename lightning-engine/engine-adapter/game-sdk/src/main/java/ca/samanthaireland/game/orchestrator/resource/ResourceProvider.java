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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Abstraction for resource management operations.
 * <p>Provides upload, download, and caching capabilities for game resources
 * such as textures and audio files.
 */
public interface ResourceProvider extends AutoCloseable {

    /**
     * Upload a resource to the server.
     *
     * @param name the resource name
     * @param type the resource type (e.g., "TEXTURE", "AUDIO")
     * @param data the resource bytes
     * @return the server-assigned resource ID
     * @throws IOException if upload fails
     */
    long uploadResource(String name, String type, byte[] data) throws IOException;

    /**
     * Download a resource by ID.
     *
     * @param resourceId the resource ID
     * @return the resource data, or empty if not found
     * @throws IOException if download fails
     */
    Optional<ResourceData> downloadResource(long resourceId) throws IOException;

    /**
     * Delete a resource from the server.
     *
     * @param resourceId the resource ID to delete
     * @throws IOException if deletion fails
     */
    void deleteResource(long resourceId) throws IOException;

    /**
     * Ensure a resource is available locally, downloading if necessary.
     * <p>This method may trigger an asynchronous download.
     *
     * @param resourceId the resource ID to ensure is available
     */
    void ensureResource(long resourceId);

    /**
     * Get the local file path for a resource if available.
     *
     * @param resourceId the resource ID
     * @return the local path, or empty if not available locally
     */
    Optional<Path> getLocalPath(long resourceId);

    /**
     * Check if a resource is available locally.
     *
     * @param resourceId the resource ID
     * @return true if the resource is available locally
     */
    boolean isAvailableLocally(long resourceId);

    /**
     * Set a listener for resource download events.
     *
     * @param listener the listener to notify on download events
     */
    void setDownloadListener(Consumer<ResourceDownloadEvent> listener);

    /**
     * Clear any local resource cache.
     */
    void clearCache();

    /**
     * Get the cache directory path.
     *
     * @return the cache directory, or empty if caching is not supported
     */
    Optional<Path> getCacheDirectory();

    /**
     * Shutdown and release resources.
     */
    @Override
    void close();

    /**
     * Resource data returned from download operations.
     *
     * @param resourceId   the resource ID
     * @param resourceName the resource name
     * @param resourceType the resource type
     * @param data         the resource bytes
     */
    record ResourceData(long resourceId, String resourceName, String resourceType, byte[] data) {
    }
}
