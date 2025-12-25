package com.lightningfirefly.game.orchestrator.resource;

import com.lightningfirefly.game.orchestrator.ResourceDownloadEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CachingResourceProvider")
class CachingResourceProviderTest {

    @TempDir
    Path tempDir;

    private MockResourceProvider mockDelegate;
    private CachingResourceProvider provider;

    @BeforeEach
    void setUp() {
        mockDelegate = new MockResourceProvider();
        provider = new CachingResourceProvider(mockDelegate, tempDir);
    }

    @AfterEach
    void tearDown() {
        provider.close();
    }

    @Nested
    @DisplayName("uploadResource")
    class UploadResource {

        @Test
        @DisplayName("should delegate to underlying provider")
        void delegatesToProvider() throws IOException {
            mockDelegate.uploadReturnId = 42L;

            long result = provider.uploadResource("test.png", "TEXTURE", new byte[]{1, 2, 3});

            assertThat(result).isEqualTo(42L);
            assertThat(mockDelegate.uploadedName).isEqualTo("test.png");
        }
    }

    @Nested
    @DisplayName("downloadResource")
    class DownloadResource {

        @Test
        @DisplayName("should download from delegate and cache to disk")
        void downloadsAndCaches() throws IOException {
            mockDelegate.downloadReturnData = new ResourceProvider.ResourceData(
                    42L, "test.png", "TEXTURE", new byte[]{1, 2, 3});

            Optional<ResourceProvider.ResourceData> result = provider.downloadResource(42L);

            assertThat(result).isPresent();
            assertThat(result.get().resourceId()).isEqualTo(42L);

            // Should be cached now
            assertThat(provider.isAvailableLocally(42L)).isTrue();
            assertThat(provider.getLocalPath(42L)).isPresent();
            assertThat(Files.exists(provider.getLocalPath(42L).get())).isTrue();
        }

        @Test
        @DisplayName("should return from cache on second download")
        void returnsCachedOnSecondDownload() throws IOException {
            mockDelegate.downloadReturnData = new ResourceProvider.ResourceData(
                    42L, "test.png", "TEXTURE", new byte[]{1, 2, 3});

            // First download
            provider.downloadResource(42L);
            int downloadCountAfterFirst = mockDelegate.downloadCount;

            // Second download should hit cache
            Optional<ResourceProvider.ResourceData> result = provider.downloadResource(42L);

            assertThat(result).isPresent();
            // Download count should only increase by 1 (for reading from disk, not from delegate)
            // Actually, the second call reads from disk, so delegate count stays same
            assertThat(mockDelegate.downloadCount).isEqualTo(downloadCountAfterFirst);
        }

        @Test
        @DisplayName("should return empty when delegate returns empty")
        void returnsEmptyWhenDelegateEmpty() throws IOException {
            mockDelegate.downloadReturnData = null;

            Optional<ResourceProvider.ResourceData> result = provider.downloadResource(99L);

            assertThat(result).isEmpty();
            assertThat(provider.isAvailableLocally(99L)).isFalse();
        }
    }

    @Nested
    @DisplayName("deleteResource")
    class DeleteResource {

        @Test
        @DisplayName("should delete from delegate and remove from cache")
        void deletesFromDelegateAndCache() throws IOException {
            // First cache the resource
            mockDelegate.downloadReturnData = new ResourceProvider.ResourceData(
                    42L, "test.png", "TEXTURE", new byte[]{1, 2, 3});
            provider.downloadResource(42L);
            Path cachedPath = provider.getLocalPath(42L).orElseThrow();

            // Delete it
            provider.deleteResource(42L);

            assertThat(mockDelegate.deletedResourceId).isEqualTo(42L);
            assertThat(provider.isAvailableLocally(42L)).isFalse();
            assertThat(provider.getLocalPath(42L)).isEmpty();
            assertThat(Files.exists(cachedPath)).isFalse();
        }
    }

    @Nested
    @DisplayName("ensureResource")
    class EnsureResource {

        @Test
        @DisplayName("should trigger async download")
        void triggersAsyncDownload() throws Exception {
            mockDelegate.downloadReturnData = new ResourceProvider.ResourceData(
                    42L, "test.png", "TEXTURE", new byte[]{1, 2, 3});

            CountDownLatch latch = new CountDownLatch(1);
            provider.setDownloadListener(event -> {
                if (event.status() == ResourceDownloadEvent.Status.COMPLETED) {
                    latch.countDown();
                }
            });

            provider.ensureResource(42L);

            boolean completed = latch.await(5, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
            assertThat(provider.isAvailableLocally(42L)).isTrue();
        }

        @Test
        @DisplayName("should not download again if already cached")
        void skipsIfAlreadyCached() throws Exception {
            mockDelegate.downloadReturnData = new ResourceProvider.ResourceData(
                    42L, "test.png", "TEXTURE", new byte[]{1, 2, 3});

            // First ensure
            CountDownLatch firstLatch = new CountDownLatch(1);
            provider.setDownloadListener(event -> firstLatch.countDown());
            provider.ensureResource(42L);
            firstLatch.await(5, TimeUnit.SECONDS);

            int downloadCountAfterFirst = mockDelegate.downloadCount;

            // Second ensure should skip
            provider.ensureResource(42L);

            // Give it a moment
            Thread.sleep(100);

            assertThat(mockDelegate.downloadCount).isEqualTo(downloadCountAfterFirst);
        }

        @Test
        @DisplayName("should notify listener on failure")
        void notifiesOnFailure() throws Exception {
            mockDelegate.throwOnDownload = new IOException("Download failed");

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<ResourceDownloadEvent> eventRef = new AtomicReference<>();
            provider.setDownloadListener(event -> {
                eventRef.set(event);
                latch.countDown();
            });

            provider.ensureResource(42L);

            boolean completed = latch.await(5, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
            assertThat(eventRef.get().status()).isEqualTo(ResourceDownloadEvent.Status.FAILED);
            assertThat(eventRef.get().error()).isNotNull();
        }
    }

    @Nested
    @DisplayName("isDownloadPending")
    class IsDownloadPending {

        @Test
        @DisplayName("should return true during download")
        void returnsTrueDuringDownload() throws Exception {
            // Use a slow download
            mockDelegate.downloadDelay = 500;
            mockDelegate.downloadReturnData = new ResourceProvider.ResourceData(
                    42L, "test.png", "TEXTURE", new byte[]{1, 2, 3});

            provider.ensureResource(42L);

            // Should be pending immediately after
            assertThat(provider.isDownloadPending(42L)).isTrue();

            // Wait for completion
            Thread.sleep(600);

            assertThat(provider.isDownloadPending(42L)).isFalse();
        }
    }

    @Nested
    @DisplayName("clearCache")
    class ClearCache {

        @Test
        @DisplayName("should clear all tracking")
        void clearsAllTracking() throws IOException {
            mockDelegate.downloadReturnData = new ResourceProvider.ResourceData(
                    42L, "test.png", "TEXTURE", new byte[]{1, 2, 3});
            provider.downloadResource(42L);

            assertThat(provider.isAvailableLocally(42L)).isTrue();

            provider.clearCache();

            assertThat(provider.isAvailableLocally(42L)).isFalse();
            assertThat(provider.getLocalPath(42L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("getCacheDirectory")
    class GetCacheDirectory {

        @Test
        @DisplayName("should return configured cache directory")
        void returnsConfiguredDirectory() {
            assertThat(provider.getCacheDirectory()).hasValue(tempDir);
        }
    }

    @Nested
    @DisplayName("fileName generation")
    class FileNameGeneration {

        @Test
        @DisplayName("should use resource name when available")
        void usesResourceNameWhenAvailable() throws IOException {
            mockDelegate.downloadReturnData = new ResourceProvider.ResourceData(
                    42L, "my-texture.png", "TEXTURE", new byte[]{1, 2, 3});

            provider.downloadResource(42L);

            Path path = provider.getLocalPath(42L).orElseThrow();
            assertThat(path.getFileName().toString()).isEqualTo("my-texture.png");
        }

        @Test
        @DisplayName("should generate name with extension for textures")
        void generatesNameForTextures() throws IOException {
            mockDelegate.downloadReturnData = new ResourceProvider.ResourceData(
                    42L, null, "TEXTURE", new byte[]{1, 2, 3});

            provider.downloadResource(42L);

            Path path = provider.getLocalPath(42L).orElseThrow();
            assertThat(path.getFileName().toString()).isEqualTo("resource_42.png");
        }

        @Test
        @DisplayName("should generate name with extension for audio")
        void generatesNameForAudio() throws IOException {
            mockDelegate.downloadReturnData = new ResourceProvider.ResourceData(
                    42L, "", "AUDIO", new byte[]{1, 2, 3});

            provider.downloadResource(42L);

            Path path = provider.getLocalPath(42L).orElseThrow();
            assertThat(path.getFileName().toString()).isEqualTo("resource_42.wav");
        }

        @Test
        @DisplayName("should generate name with .bin for unknown types")
        void generatesNameForUnknownTypes() throws IOException {
            mockDelegate.downloadReturnData = new ResourceProvider.ResourceData(
                    42L, null, "UNKNOWN", new byte[]{1, 2, 3});

            provider.downloadResource(42L);

            Path path = provider.getLocalPath(42L).orElseThrow();
            assertThat(path.getFileName().toString()).isEqualTo("resource_42.bin");
        }
    }

    /**
     * Mock ResourceProvider for testing the caching decorator.
     */
    static class MockResourceProvider implements ResourceProvider {
        long uploadReturnId = 0;
        String uploadedName;

        ResourceData downloadReturnData;
        int downloadCount = 0;
        long downloadDelay = 0;
        IOException throwOnDownload;

        Long deletedResourceId;

        @Override
        public long uploadResource(String name, String type, byte[] data) throws IOException {
            uploadedName = name;
            return uploadReturnId;
        }

        @Override
        public Optional<ResourceData> downloadResource(long resourceId) throws IOException {
            if (throwOnDownload != null) throw throwOnDownload;
            downloadCount++;
            if (downloadDelay > 0) {
                try {
                    Thread.sleep(downloadDelay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return Optional.ofNullable(downloadReturnData);
        }

        @Override
        public void deleteResource(long resourceId) throws IOException {
            deletedResourceId = resourceId;
        }

        @Override
        public void ensureResource(long resourceId) {
            // No-op for mock
        }

        @Override
        public Optional<Path> getLocalPath(long resourceId) {
            return Optional.empty();
        }

        @Override
        public boolean isAvailableLocally(long resourceId) {
            return false;
        }

        @Override
        public void setDownloadListener(java.util.function.Consumer<ResourceDownloadEvent> listener) {
            // No-op for mock
        }

        @Override
        public void clearCache() {
            // No-op for mock
        }

        @Override
        public Optional<Path> getCacheDirectory() {
            return Optional.empty();
        }

        @Override
        public void close() {
            // No-op for mock
        }
    }
}
