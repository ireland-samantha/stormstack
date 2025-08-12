package com.lightningfirefly.engine.api.resource.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class HttpSnapshotAdapterTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> stringResponse;

    private SnapshotAdapter.HttpSnapshotAdapter adapter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        adapter = new SnapshotAdapter.HttpSnapshotAdapter("http://localhost:8080", httpClient);
    }

    @Test
    void getMatchSnapshot_found() throws Exception {
        when(stringResponse.statusCode()).thenReturn(200);
        when(stringResponse.body()).thenReturn("{\"matchId\":100,\"tick\":42,\"snapshot\":{\"entities\":[]}}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stringResponse);

        Optional<SnapshotAdapter.SnapshotResponse> result = adapter.getMatchSnapshot(100L);

        assertThat(result).isPresent();
        assertThat(result.get().matchId()).isEqualTo(100L);
        assertThat(result.get().tick()).isEqualTo(42L);
        assertThat(result.get().snapshotData()).isEqualTo("{\"entities\":[]}");
    }

    @Test
    void getMatchSnapshot_notFound() throws Exception {
        when(stringResponse.statusCode()).thenReturn(404);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stringResponse);

        Optional<SnapshotAdapter.SnapshotResponse> result = adapter.getMatchSnapshot(999L);

        assertThat(result).isEmpty();
    }

    @Test
    void getMatchSnapshot_failure() throws Exception {
        when(stringResponse.statusCode()).thenReturn(500);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stringResponse);

        assertThatThrownBy(() -> adapter.getMatchSnapshot(100L))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("status: 500");
    }

    @Test
    void getMatchSnapshot_withArraySnapshot() throws Exception {
        when(stringResponse.statusCode()).thenReturn(200);
        when(stringResponse.body()).thenReturn("{\"matchId\":100,\"tick\":1,\"snapshot\":[{\"id\":1},{\"id\":2}]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stringResponse);

        Optional<SnapshotAdapter.SnapshotResponse> result = adapter.getMatchSnapshot(100L);

        assertThat(result).isPresent();
        assertThat(result.get().snapshotData()).isEqualTo("[{\"id\":1},{\"id\":2}]");
    }

    @Test
    void getMatchSnapshot_withNullSnapshot() throws Exception {
        when(stringResponse.statusCode()).thenReturn(200);
        when(stringResponse.body()).thenReturn("{\"matchId\":100,\"tick\":1,\"snapshot\":null}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stringResponse);

        Optional<SnapshotAdapter.SnapshotResponse> result = adapter.getMatchSnapshot(100L);

        assertThat(result).isPresent();
        assertThat(result.get().snapshotData()).isNull();
    }

    @Test
    void interrupted_throwsIOException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("Interrupted"));

        assertThatThrownBy(() -> adapter.getMatchSnapshot(100L))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("interrupted");
    }
}
