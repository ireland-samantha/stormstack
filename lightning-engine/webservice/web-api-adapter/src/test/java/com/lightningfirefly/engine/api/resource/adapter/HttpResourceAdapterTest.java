package com.lightningfirefly.engine.api.resource.adapter;

import com.lightningfirefly.engine.api.resource.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class HttpResourceAdapterTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> stringResponse;

    @Mock
    private HttpResponse<byte[]> byteResponse;

    @Mock
    private HttpResponse<Void> voidResponse;

    private ResourceAdapter.HttpResourceAdapter adapter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        adapter = new ResourceAdapter.HttpResourceAdapter("http://localhost:8080", httpClient);
    }

    @Test
    void uploadResource_success() throws Exception {
        when(stringResponse.statusCode()).thenReturn(201);
        when(stringResponse.body()).thenReturn("{\"resourceId\":42,\"name\":\"texture.png\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stringResponse);

        byte[] data = "fake image data".getBytes();
        long result = adapter.uploadResource("texture.png", "TEXTURE", data);

        assertThat(result).isEqualTo(42L);
    }

    @Test
    void uploadResource_failure() throws Exception {
        when(stringResponse.statusCode()).thenReturn(500);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stringResponse);

        assertThatThrownBy(() -> adapter.uploadResource("test.png", "TEXTURE", new byte[0]))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("status: 500");
    }

    @Test
    void downloadResource_found() throws Exception {
        byte[] resourceData = "resource content".getBytes();
        when(byteResponse.statusCode()).thenReturn(200);
        when(byteResponse.body()).thenReturn(resourceData);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(byteResponse);

        Optional<Resource> result = adapter.downloadResource(42L);

        assertThat(result).isPresent();
        assertThat(result.get().resourceId()).isEqualTo(42L);
        assertThat(result.get().blob()).isEqualTo(resourceData);
    }

    @Test
    void downloadResource_notFound() throws Exception {
        when(byteResponse.statusCode()).thenReturn(404);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(byteResponse);

        Optional<Resource> result = adapter.downloadResource(999L);

        assertThat(result).isEmpty();
    }

    @Test
    void downloadChunk_success() throws Exception {
        byte[] chunkData = "chunk content".getBytes();
        when(byteResponse.statusCode()).thenReturn(200);
        when(byteResponse.body()).thenReturn(chunkData);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(byteResponse);

        byte[] result = adapter.downloadChunk(42L, 0, 1024);

        assertThat(result).isEqualTo(chunkData);
    }

    @Test
    void downloadChunk_failure() throws Exception {
        when(byteResponse.statusCode()).thenReturn(404);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(byteResponse);

        assertThatThrownBy(() -> adapter.downloadChunk(42L, 99, 1024))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("status: 404");
    }

    @Test
    void getTotalChunks_success() throws Exception {
        when(stringResponse.statusCode()).thenReturn(200);
        when(stringResponse.body()).thenReturn("{\"totalChunks\":5,\"chunkSize\":1024}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stringResponse);

        int result = adapter.getTotalChunks(42L, 1024);

        assertThat(result).isEqualTo(5);
    }

    @Test
    void listResources_empty() throws Exception {
        when(stringResponse.statusCode()).thenReturn(200);
        when(stringResponse.body()).thenReturn("[]");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stringResponse);

        List<Resource> result = adapter.listResources();

        assertThat(result).isEmpty();
    }

    @Test
    void listResources_withResources() throws Exception {
        String json = """
            [
                {"resourceId":1,"resourceName":"texture1.png","resourceType":"TEXTURE","size":1024},
                {"resourceId":2,"resourceName":"texture2.png","resourceType":"TEXTURE","size":2048},
                {"resourceId":3,"resourceName":"sound.wav","resourceType":"AUDIO","size":8192}
            ]
            """;
        when(stringResponse.statusCode()).thenReturn(200);
        when(stringResponse.body()).thenReturn(json);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stringResponse);

        List<Resource> result = adapter.listResources();

        assertThat(result).hasSize(3);

        // Verify first resource
        assertThat(result.get(0).resourceId()).isEqualTo(1L);
        assertThat(result.get(0).resourceName()).isEqualTo("texture1.png");
        assertThat(result.get(0).resourceType()).isEqualTo("TEXTURE");

        // Verify second resource
        assertThat(result.get(1).resourceId()).isEqualTo(2L);
        assertThat(result.get(1).resourceName()).isEqualTo("texture2.png");
        assertThat(result.get(1).resourceType()).isEqualTo("TEXTURE");

        // Verify third resource
        assertThat(result.get(2).resourceId()).isEqualTo(3L);
        assertThat(result.get(2).resourceName()).isEqualTo("sound.wav");
        assertThat(result.get(2).resourceType()).isEqualTo("AUDIO");
    }

    @Test
    void listResources_singleResource() throws Exception {
        String json = """
            [{"resourceId":42,"resourceName":"test.png","resourceType":"TEXTURE","size":512}]
            """;
        when(stringResponse.statusCode()).thenReturn(200);
        when(stringResponse.body()).thenReturn(json);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stringResponse);

        List<Resource> result = adapter.listResources();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).resourceId()).isEqualTo(42L);
        assertThat(result.get(0).resourceName()).isEqualTo("test.png");
        assertThat(result.get(0).resourceType()).isEqualTo("TEXTURE");
    }

    @Test
    void listResources_withNestedJson() throws Exception {
        // Test that parser handles complex JSON correctly
        String json = """
            [{"resourceId":1,"resourceName":"test.json","resourceType":"CONFIG","size":100,"metadata":{"key":"value"}}]
            """;
        when(stringResponse.statusCode()).thenReturn(200);
        when(stringResponse.body()).thenReturn(json);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stringResponse);

        List<Resource> result = adapter.listResources();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).resourceId()).isEqualTo(1L);
        assertThat(result.get(0).resourceName()).isEqualTo("test.json");
    }

    @Test
    void deleteResource_success() throws Exception {
        when(voidResponse.statusCode()).thenReturn(204);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(voidResponse);

        boolean result = adapter.deleteResource(42L);

        assertThat(result).isTrue();
    }

    @Test
    void deleteResource_notFound() throws Exception {
        when(voidResponse.statusCode()).thenReturn(404);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(voidResponse);

        boolean result = adapter.deleteResource(999L);

        assertThat(result).isFalse();
    }

    @Test
    void interrupted_throwsIOException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("Interrupted"));

        assertThatThrownBy(() -> adapter.downloadResource(42L))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("interrupted");
    }
}
