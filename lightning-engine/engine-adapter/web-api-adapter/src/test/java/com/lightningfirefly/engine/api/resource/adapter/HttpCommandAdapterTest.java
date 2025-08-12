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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class HttpCommandAdapterTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> stringResponse;

    @Mock
    private HttpResponse<Void> voidResponse;

    private CommandAdapter.HttpCommandAdapter adapter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        adapter = new CommandAdapter.HttpCommandAdapter("http://localhost:8080", httpClient);
    }

    @Test
    void submitCommand_success200() throws Exception {
        when(voidResponse.statusCode()).thenReturn(200);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(voidResponse);

        // Should not throw
        adapter.submitCommand(100L, "move", 1L, Map.of("x", 5, "y", 10));
    }

    @Test
    void submitCommand_success201() throws Exception {
        when(voidResponse.statusCode()).thenReturn(201);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(voidResponse);

        // Should not throw
        adapter.submitCommand(100L, "attack", 2L, Map.of("target", 3));
    }

    @Test
    void submitCommand_success202() throws Exception {
        when(voidResponse.statusCode()).thenReturn(202);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(voidResponse);

        // Should not throw
        adapter.submitCommand(100L, "queue", 1L, Map.of());
    }

    @Test
    void submitCommand_withStringValue() throws Exception {
        when(voidResponse.statusCode()).thenReturn(200);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(voidResponse);

        // Should not throw
        adapter.submitCommand(100L, "chat", 1L, Map.of("message", "hello world"));
    }

    @Test
    void submitCommand_failure() throws Exception {
        when(voidResponse.statusCode()).thenReturn(400);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(voidResponse);

        assertThatThrownBy(() -> adapter.submitCommand(100L, "invalid", 1L, Map.of()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("status: 400");
    }

    @Test
    void getAvailableCommands_success() throws Exception {
        when(stringResponse.statusCode()).thenReturn(200);
        when(stringResponse.body()).thenReturn("[]");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stringResponse);

        List<String> result = adapter.getAvailableCommands("chess");

        assertThat(result).isEmpty();
    }

    @Test
    void getAvailableCommands_failure() throws Exception {
        when(stringResponse.statusCode()).thenReturn(500);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(stringResponse);

        assertThatThrownBy(() -> adapter.getAvailableCommands("chess"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("status: 500");
    }

    @Test
    void interrupted_throwsIOException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("Interrupted"));

        assertThatThrownBy(() -> adapter.submitCommand(100L, "test", 1L, Map.of()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("interrupted");
    }
}
