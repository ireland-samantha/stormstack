package com.lightningfirefly.game.orchestrator.resource;

import com.lightningfirefly.engine.api.resource.Resource;
import com.lightningfirefly.engine.api.resource.adapter.ResourceAdapter;
import com.lightningfirefly.game.orchestrator.ResourceDownloadEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ResourceProviderAdapter")
class ResourceProviderAdapterTest {

    private MockResourceAdapter mockAdapter;
    private ResourceProviderAdapter provider;

    @BeforeEach
    void setUp() {
        mockAdapter = new MockResourceAdapter();
        provider = new ResourceProviderAdapter(mockAdapter);
    }

    @Nested
    @DisplayName("uploadResource")
    class UploadResource {

        @Test
        @DisplayName("should delegate to ResourceAdapter")
        void delegatesToAdapter() throws IOException {
            mockAdapter.uploadReturnId = 42L;

            long result = provider.uploadResource("test.png", "TEXTURE", new byte[]{1, 2, 3});

            assertThat(result).isEqualTo(42L);
            assertThat(mockAdapter.uploadedName).isEqualTo("test.png");
            assertThat(mockAdapter.uploadedType).isEqualTo("TEXTURE");
            assertThat(mockAdapter.uploadedData).containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("should propagate IOException")
        void propagatesIOException() {
            mockAdapter.throwOnUpload = new IOException("Upload failed");

            assertThatThrownBy(() -> provider.uploadResource("test.png", "TEXTURE", new byte[]{1}))
                    .isInstanceOf(IOException.class)
                    .hasMessage("Upload failed");
        }
    }

    @Nested
    @DisplayName("downloadResource")
    class DownloadResource {

        @Test
        @DisplayName("should delegate to ResourceAdapter and convert result")
        void delegatesToAdapterAndConverts() throws IOException {
            mockAdapter.downloadReturnResource = new Resource(42L, new byte[]{1, 2, 3}, "TEXTURE", "test.png");

            Optional<ResourceProvider.ResourceData> result = provider.downloadResource(42L);

            assertThat(result).isPresent();
            assertThat(result.get().resourceId()).isEqualTo(42L);
            assertThat(result.get().resourceName()).isEqualTo("test.png");
            assertThat(result.get().resourceType()).isEqualTo("TEXTURE");
            assertThat(result.get().data()).containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("should return empty when adapter returns empty")
        void returnsEmptyWhenAdapterReturnsEmpty() throws IOException {
            mockAdapter.downloadReturnResource = null;

            Optional<ResourceProvider.ResourceData> result = provider.downloadResource(99L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("deleteResource")
    class DeleteResource {

        @Test
        @DisplayName("should delegate to ResourceAdapter")
        void delegatesToAdapter() throws IOException {
            provider.deleteResource(42L);

            assertThat(mockAdapter.deletedResourceId).isEqualTo(42L);
        }
    }

    @Nested
    @DisplayName("caching operations")
    class CachingOperations {

        @Test
        @DisplayName("ensureResource should be no-op for base adapter")
        void ensureResourceIsNoOp() {
            // Should not throw or have any side effects
            provider.ensureResource(42L);
        }

        @Test
        @DisplayName("getLocalPath should always return empty")
        void getLocalPathReturnsEmpty() {
            assertThat(provider.getLocalPath(42L)).isEmpty();
        }

        @Test
        @DisplayName("isAvailableLocally should always return false")
        void isAvailableLocallyReturnsFalse() {
            assertThat(provider.isAvailableLocally(42L)).isFalse();
        }

        @Test
        @DisplayName("clearCache should be no-op")
        void clearCacheIsNoOp() {
            // Should not throw
            provider.clearCache();
        }

        @Test
        @DisplayName("getCacheDirectory should return empty")
        void getCacheDirectoryReturnsEmpty() {
            assertThat(provider.getCacheDirectory()).isEmpty();
        }
    }

    @Nested
    @DisplayName("downloadListener")
    class DownloadListener {

        @Test
        @DisplayName("should store and return download listener")
        void storesDownloadListener() {
            AtomicReference<ResourceDownloadEvent> eventRef = new AtomicReference<>();

            provider.setDownloadListener(eventRef::set);

            assertThat(provider.getDownloadListener()).isNotNull();
        }
    }

    @Nested
    @DisplayName("close")
    class Close {

        @Test
        @DisplayName("should be no-op for base adapter")
        void closeIsNoOp() {
            // Should not throw
            provider.close();
        }
    }

    /**
     * Mock ResourceAdapter for testing.
     */
    static class MockResourceAdapter implements ResourceAdapter {
        long uploadReturnId = 0;
        String uploadedName;
        String uploadedType;
        byte[] uploadedData;
        IOException throwOnUpload;

        Resource downloadReturnResource;
        Long deletedResourceId;

        @Override
        public long uploadResource(String name, String type, byte[] data) throws IOException {
            if (throwOnUpload != null) throw throwOnUpload;
            uploadedName = name;
            uploadedType = type;
            uploadedData = data;
            return uploadReturnId;
        }

        @Override
        public Optional<Resource> downloadResource(long resourceId) throws IOException {
            return Optional.ofNullable(downloadReturnResource);
        }

        @Override
        public boolean deleteResource(long resourceId) throws IOException {
            deletedResourceId = resourceId;
            return true;
        }

        @Override
        public java.util.List<Resource> listResources() throws IOException {
            return java.util.Collections.emptyList();
        }

        @Override
        public byte[] downloadChunk(long resourceId, int chunkIndex, int chunkSize) throws IOException {
            return new byte[0];
        }

        @Override
        public int getTotalChunks(long resourceId, int chunkSize) throws IOException {
            return 0;
        }
    }
}
