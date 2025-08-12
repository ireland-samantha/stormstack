package com.lightningfirefly.engine.quarkus.api.websocket;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.websockets.next.BasicWebSocketConnector;
import io.quarkus.websockets.next.WebSocketClientConnection;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the SnapshotWebSocket endpoint.
 *
 * <p>Tests verify that the WebSocket endpoint:
 * <ul>
 *   <li>Accepts connections</li>
 *   <li>Broadcasts snapshot data</li>
 *   <li>Responds to text messages</li>
 * </ul>
 */
@QuarkusTest
class SnapshotWebSocketTest {

    @TestHTTPResource("/ws/snapshots/1")
    URI wsUri;

    @Inject
    BasicWebSocketConnector connector;

    @Test
    @Timeout(30)
    void shouldConnectAndReceiveSnapshot() throws Exception {
        LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();

        WebSocketClientConnection connection = connector
                .baseUri(wsUri)
                .onTextMessage((c, m) -> messages.add(m))
                .connectAndAwait();

        try {
            // Wait for at least one message (broadcast happens every 100ms by default)
            String message = messages.poll(10, TimeUnit.SECONDS);

            assertThat(message).isNotNull();
            assertThat(message).contains("matchId");
            assertThat(message).contains("tick");
        } finally {
            connection.closeAndAwait();
        }
    }

    @Test
    @Timeout(30)
    void shouldReceiveSnapshotOnMessage() throws Exception {
        LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();

        WebSocketClientConnection connection = connector
                .baseUri(wsUri)
                .onTextMessage((c, m) -> messages.add(m))
                .connectAndAwait();

        try {
            // Clear any initial broadcast messages
            messages.poll(1, TimeUnit.SECONDS);
            messages.clear();

            // Send a refresh request
            connection.sendTextAndAwait("refresh");

            // Should receive a response
            String message = messages.poll(5, TimeUnit.SECONDS);

            assertThat(message).isNotNull();
            assertThat(message).contains("matchId");
        } finally {
            connection.closeAndAwait();
        }
    }

    @Test
    @Timeout(30)
    void shouldReceiveMultipleSnapshots() throws Exception {
        LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();

        WebSocketClientConnection connection = connector
                .baseUri(wsUri)
                .onTextMessage((c, m) -> messages.add(m))
                .connectAndAwait();

        try {
            // Wait for multiple broadcast messages
            String message1 = messages.poll(10, TimeUnit.SECONDS);
            String message2 = messages.poll(2, TimeUnit.SECONDS);

            assertThat(message1).isNotNull();
            assertThat(message2).isNotNull();
        } finally {
            connection.closeAndAwait();
        }
    }
}
