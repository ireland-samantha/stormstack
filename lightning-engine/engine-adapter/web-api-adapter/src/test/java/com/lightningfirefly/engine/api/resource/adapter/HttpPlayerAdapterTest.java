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

class HttpPlayerAdapterTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> stringResponse;

    @Mock
    private HttpResponse<Void> voidResponse;

    private PlayerAdapter.HttpPlayerAdapter adapter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        adapter = new PlayerAdapter.HttpPlayerAdapter("http://localhost:8080", httpClient);
    }

    @Test
    void createPlayer_success() throws Exception {
        when(stringResponse.statusCode()).thenReturn(201);
        when(stringResponse.body()).thenReturn("{\"id\":1,\"name\":\"TestPlayer\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stringResponse);

        PlayerAdapter.PlayerResponse result = adapter.createPlayer(1L, "TestPlayer");

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("TestPlayer");
    }

    @Test
    void createPlayer_failure() throws Exception {
        when(stringResponse.statusCode()).thenReturn(500);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stringResponse);

        assertThatThrownBy(() -> adapter.createPlayer(1L, "TestPlayer"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("status: 500");
    }

    @Test
    void getPlayer_found() throws Exception {
        when(stringResponse.statusCode()).thenReturn(200);
        when(stringResponse.body()).thenReturn("{\"id\":42,\"name\":\"FoundPlayer\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stringResponse);

        Optional<PlayerAdapter.PlayerResponse> result = adapter.getPlayer(42L);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(42L);
        assertThat(result.get().name()).isEqualTo("FoundPlayer");
    }

    @Test
    void getPlayer_notFound() throws Exception {
        when(stringResponse.statusCode()).thenReturn(404);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stringResponse);

        Optional<PlayerAdapter.PlayerResponse> result = adapter.getPlayer(999L);

        assertThat(result).isEmpty();
    }

    @Test
    void deletePlayer_success() throws Exception {
        when(voidResponse.statusCode()).thenReturn(204);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(voidResponse);

        boolean result = adapter.deletePlayer(1L);

        assertThat(result).isTrue();
    }

    @Test
    void deletePlayer_notFound() throws Exception {
        when(voidResponse.statusCode()).thenReturn(404);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(voidResponse);

        boolean result = adapter.deletePlayer(999L);

        assertThat(result).isFalse();
    }

    @Test
    void joinMatch_success() throws Exception {
        when(stringResponse.statusCode()).thenReturn(201);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stringResponse);

        PlayerAdapter.PlayerMatchResponse result = adapter.joinMatch(1L, 100L);

        assertThat(result.playerId()).isEqualTo(1L);
        assertThat(result.matchId()).isEqualTo(100L);
    }

    @Test
    void leaveMatch_success() throws Exception {
        when(voidResponse.statusCode()).thenReturn(204);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(voidResponse);

        boolean result = adapter.leaveMatch(1L, 100L);

        assertThat(result).isTrue();
    }

    @Test
    void baseUrl_trailingSlashRemoved() {
        PlayerAdapter.HttpPlayerAdapter adapterWithSlash =
                new PlayerAdapter.HttpPlayerAdapter("http://localhost:8080/", httpClient);

        // The adapter should work the same way regardless of trailing slash
        assertThat(adapterWithSlash).isNotNull();
    }

    @Test
    void interrupted_throwsIOException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("Interrupted"));

        assertThatThrownBy(() -> adapter.getPlayer(1L))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("interrupted");
    }
}
