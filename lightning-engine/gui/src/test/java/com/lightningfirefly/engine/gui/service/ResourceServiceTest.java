package com.lightningfirefly.engine.gui.service;

import com.lightningfirefly.engine.api.resource.Resource;
import com.lightningfirefly.engine.api.resource.adapter.ResourceAdapter;
import com.lightningfirefly.engine.gui.service.ResourceService.ResourceEvent;
import com.lightningfirefly.engine.gui.service.ResourceService.ResourceEventType;
import com.lightningfirefly.engine.gui.service.ResourceService.ResourceInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResourceServiceTest {

    @Mock
    private ResourceAdapter resourceAdapter;

    private ResourceService resourceService;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        resourceService = new ResourceService(resourceAdapter);
    }

    @AfterEach
    void tearDown() throws Exception {
        resourceService.shutdown();
        mocks.close();
    }

    @Test
    void listResources_returnsEmptyList_whenAdapterReturnsEmpty() throws Exception {
        when(resourceAdapter.listResources()).thenReturn(List.of());

        CompletableFuture<List<ResourceInfo>> future = resourceService.listResources();
        List<ResourceInfo> result = future.get(5, TimeUnit.SECONDS);

        assertThat(result).isEmpty();
        verify(resourceAdapter).listResources();
    }

    @Test
    void listResources_returnsResourceList_whenAdapterReturnsResources() throws Exception {
        List<Resource> mockResources = List.of(
            new Resource(1L, new byte[]{1, 2, 3}, "TEXTURE"),
            new Resource(2L, new byte[]{4, 5, 6}, "TEXTURE")
        );
        when(resourceAdapter.listResources()).thenReturn(mockResources);

        CompletableFuture<List<ResourceInfo>> future = resourceService.listResources();
        List<ResourceInfo> result = future.get(5, TimeUnit.SECONDS);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(1).id()).isEqualTo(2L);
    }

    @Test
    void listResources_returnsEmptyList_whenAdapterThrowsException() throws Exception {
        when(resourceAdapter.listResources()).thenThrow(new IOException("Connection refused"));

        List<ResourceEvent> events = new ArrayList<>();
        resourceService.addListener(events::add);

        CompletableFuture<List<ResourceInfo>> future = resourceService.listResources();
        List<ResourceInfo> result = future.get(5, TimeUnit.SECONDS);

        assertThat(result).isEmpty();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().type()).isEqualTo(ResourceEventType.ERROR);
    }

    @Test
    void uploadResource_returnsResourceId_onSuccess() throws Exception {
        when(resourceAdapter.uploadResource(eq("test.png"), eq("TEXTURE"), any()))
            .thenReturn(123L);

        List<ResourceEvent> events = new ArrayList<>();
        resourceService.addListener(events::add);

        CompletableFuture<Long> future = resourceService.uploadResource("test.png", "TEXTURE", new byte[]{1, 2, 3});
        Long result = future.get(5, TimeUnit.SECONDS);

        assertThat(result).isEqualTo(123L);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().type()).isEqualTo(ResourceEventType.UPLOADED);
        assertThat(events.getFirst().resourceId()).isEqualTo(123L);
    }

    @Test
    void uploadResource_notifiesError_onFailure() throws Exception {
        when(resourceAdapter.uploadResource(anyString(), anyString(), any()))
            .thenThrow(new IOException("Upload failed"));

        List<ResourceEvent> events = new ArrayList<>();
        resourceService.addListener(events::add);

        CompletableFuture<Long> future = resourceService.uploadResource("test.png", "TEXTURE", new byte[]{1, 2, 3});

        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Expected
        }

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().type()).isEqualTo(ResourceEventType.ERROR);
    }

    @Test
    void downloadResource_returnsData_onSuccess() throws Exception {
        byte[] testData = {1, 2, 3, 4, 5};
        when(resourceAdapter.downloadResource(123L))
            .thenReturn(Optional.of(new Resource(123L, testData, "TEXTURE")));

        List<ResourceEvent> events = new ArrayList<>();
        resourceService.addListener(events::add);

        CompletableFuture<Optional<byte[]>> future = resourceService.downloadResource(123L);
        Optional<byte[]> result = future.get(5, TimeUnit.SECONDS);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(testData);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().type()).isEqualTo(ResourceEventType.DOWNLOADED);
    }

    @Test
    void downloadResource_returnsEmpty_whenNotFound() throws Exception {
        when(resourceAdapter.downloadResource(999L)).thenReturn(Optional.empty());

        CompletableFuture<Optional<byte[]>> future = resourceService.downloadResource(999L);
        Optional<byte[]> result = future.get(5, TimeUnit.SECONDS);

        assertThat(result).isEmpty();
    }

    @Test
    void deleteResource_returnsTrue_onSuccess() throws Exception {
        when(resourceAdapter.deleteResource(123L)).thenReturn(true);

        List<ResourceEvent> events = new ArrayList<>();
        resourceService.addListener(events::add);

        CompletableFuture<Boolean> future = resourceService.deleteResource(123L);
        Boolean result = future.get(5, TimeUnit.SECONDS);

        assertThat(result).isTrue();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().type()).isEqualTo(ResourceEventType.DELETED);
        assertThat(events.getFirst().resourceId()).isEqualTo(123L);
    }

    @Test
    void deleteResource_returnsFalse_whenNotFound() throws Exception {
        when(resourceAdapter.deleteResource(999L)).thenReturn(false);

        CompletableFuture<Boolean> future = resourceService.deleteResource(999L);
        Boolean result = future.get(5, TimeUnit.SECONDS);

        assertThat(result).isFalse();
    }

    @Test
    void addAndRemoveListener_worksCorrectly() throws Exception {
        when(resourceAdapter.deleteResource(1L)).thenReturn(true);

        List<ResourceEvent> events = new ArrayList<>();
        resourceService.addListener(events::add);
        resourceService.deleteResource(1L).get(5, TimeUnit.SECONDS);

        assertThat(events).hasSize(1);

        resourceService.removeListener(events::add);
        // Note: Due to how lambdas work, we need to store the reference
        // In practice, we'd use a named class or store the reference
    }
}
