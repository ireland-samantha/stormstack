package com.lightningfirefly.engine.api.resource.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class HttpSimulationAdapterTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> stringResponse;

    private SimulationAdapter.HttpSimulationAdapter adapter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        adapter = new SimulationAdapter.HttpSimulationAdapter("http://localhost:8080", httpClient);
    }

    @Test
    void getCurrentTick_success() throws Exception {
        when(stringResponse.statusCode()).thenReturn(200);
        when(stringResponse.body()).thenReturn("{\"tick\":42}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stringResponse);

        long result = adapter.getCurrentTick();

        assertThat(result).isEqualTo(42L);
    }

    @Test
    void getCurrentTick_failure() throws Exception {
        when(stringResponse.statusCode()).thenReturn(500);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stringResponse);

        assertThatThrownBy(() -> adapter.getCurrentTick())
                .isInstanceOf(IOException.class)
                .hasMessageContaining("status: 500");
    }

    @Test
    void advanceTick_success() throws Exception {
        when(stringResponse.statusCode()).thenReturn(200);
        when(stringResponse.body()).thenReturn("{\"tick\":43}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stringResponse);

        long result = adapter.advanceTick();

        assertThat(result).isEqualTo(43L);
    }

    @Test
    void advanceTick_failure() throws Exception {
        when(stringResponse.statusCode()).thenReturn(500);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stringResponse);

        assertThatThrownBy(() -> adapter.advanceTick())
                .isInstanceOf(IOException.class)
                .hasMessageContaining("status: 500");
    }

    @Test
    void interrupted_throwsIOException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("Interrupted"));

        assertThatThrownBy(() -> adapter.getCurrentTick())
                .isInstanceOf(IOException.class)
                .hasMessageContaining("interrupted");
    }

    @Test
    void parseTickResponse_withZero() throws Exception {
        when(stringResponse.statusCode()).thenReturn(200);
        when(stringResponse.body()).thenReturn("{\"tick\":0}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stringResponse);

        long result = adapter.getCurrentTick();

        assertThat(result).isEqualTo(0L);
    }
}
