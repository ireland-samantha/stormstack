package com.lightningfirefly.engine.api.resource.adapter;

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

class HttpMatchAdapterTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> stringResponse;

    @Mock
    private HttpResponse<Void> voidResponse;

    private MatchAdapter.HttpMatchAdapter adapter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        adapter = new MatchAdapter.HttpMatchAdapter("http://localhost:8080", httpClient);
    }

    @Test
    void createMatch_success_returnsGeneratedId() throws Exception {
        when(stringResponse.statusCode()).thenReturn(201);
        // Server generates ID
        when(stringResponse.body()).thenReturn("{\"id\":100,\"enabledModuleNames\":[]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stringResponse);

        MatchAdapter.MatchResponse result = adapter.createMatch(List.of("module1", "module2"));

        // ID comes from server response
        assertThat(result.id()).isEqualTo(100L);
    }

    @Test
    void createMatch_failure() throws Exception {
        when(stringResponse.statusCode()).thenReturn(500);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stringResponse);

        assertThatThrownBy(() -> adapter.createMatch(List.of()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("status: 500");
    }

    @Test
    void getMatch_found() throws Exception {
        when(stringResponse.statusCode()).thenReturn(200);
        when(stringResponse.body()).thenReturn("{\"id\":42,\"enabledModuleNames\":[]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stringResponse);

        Optional<MatchAdapter.MatchResponse> result = adapter.getMatch(42L);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(42L);
    }

    @Test
    void getMatch_notFound() throws Exception {
        when(stringResponse.statusCode()).thenReturn(404);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stringResponse);

        Optional<MatchAdapter.MatchResponse> result = adapter.getMatch(999L);

        assertThat(result).isEmpty();
    }

    @Test
    void getAllMatches_success() throws Exception {
        when(stringResponse.statusCode()).thenReturn(200);
        when(stringResponse.body()).thenReturn("[]");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stringResponse);

        List<MatchAdapter.MatchResponse> result = adapter.getAllMatches();

        assertThat(result).isEmpty();
    }

    @Test
    void deleteMatch_success() throws Exception {
        when(voidResponse.statusCode()).thenReturn(204);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(voidResponse);

        boolean result = adapter.deleteMatch(100L);

        assertThat(result).isTrue();
    }

    @Test
    void deleteMatch_notFound() throws Exception {
        when(voidResponse.statusCode()).thenReturn(404);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(voidResponse);

        boolean result = adapter.deleteMatch(999L);

        assertThat(result).isFalse();
    }

    @Test
    void interrupted_throwsIOException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("Interrupted"));

        assertThatThrownBy(() -> adapter.getMatch(1L))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("interrupted");
    }
}
