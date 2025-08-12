package com.lightningfirefly.engine.gui.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.*;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * WebSocket client for real-time snapshot subscription.
 *
 * <p>This class implements {@link AutoCloseable} to ensure proper resource cleanup.
 * Use try-with-resources or call {@link #close()} when done:
 *
 * <pre>{@code
 * try (var client = new SnapshotWebSocketClient(url, matchId)) {
 *     client.connect();
 *     // Use client...
 * }
 * }</pre>
 */
@Slf4j
@ClientEndpoint
public class SnapshotWebSocketClient implements AutoCloseable {

    private static final int INITIAL_RECONNECT_DELAY_MS = 1000;
    private static final int MAX_RECONNECT_DELAY_MS = 30000;
    private static final int MAX_RECONNECT_ATTEMPTS = 10;

    private final String serverUrl;
    private final long matchId;
    private final ObjectMapper objectMapper;
    private final List<Consumer<SnapshotData>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean autoReconnectEnabled = new AtomicBoolean(true);
    private final AtomicBoolean intentionalDisconnect = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    private Session session;
    private WebSocketContainer container;
    private ScheduledExecutorService reconnectExecutor;
    private ScheduledFuture<?> reconnectTask;

    public SnapshotWebSocketClient(String serverUrl, long matchId) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.matchId = matchId;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Connect to the WebSocket server with auto-reconnect enabled.
     */
    public void connect() {
        connectWithRetry(true);
    }

    /**
     * Connect to the WebSocket server.
     *
     * @param enableAutoReconnect if true, will automatically reconnect on disconnect
     */
    public void connectWithRetry(boolean enableAutoReconnect) {
        if (connected.get()) {
            return;
        }

        autoReconnectEnabled.set(enableAutoReconnect);
        intentionalDisconnect.set(false);
        reconnectAttempts.set(0);

        if (reconnectExecutor == null || reconnectExecutor.isShutdown()) {
            reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ws-reconnect-" + matchId);
                t.setDaemon(true);
                return t;
            });
        }

        doConnect();
    }

    private void doConnect() {
        try {
            container = ContainerProvider.getWebSocketContainer();
            String wsUrl = serverUrl.replace("http://", "ws://").replace("https://", "wss://");
            URI uri = URI.create(wsUrl + "/ws/snapshots/" + matchId);

            session = container.connectToServer(this, uri);
            connected.set(true);
            reconnectAttempts.set(0);
            log.info("Connected to WebSocket for match {}", matchId);
        } catch (Exception e) {
            log.warn("Failed to connect to WebSocket for match {}: {}", matchId, e.getMessage());
            connected.set(false);
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (!autoReconnectEnabled.get() || intentionalDisconnect.get()) {
            log.debug("Auto-reconnect disabled or intentional disconnect, not scheduling reconnect");
            return;
        }

        int attempts = reconnectAttempts.incrementAndGet();
        if (attempts > MAX_RECONNECT_ATTEMPTS) {
            log.warn("Max reconnect attempts ({}) reached for match {}", MAX_RECONNECT_ATTEMPTS, matchId);
            return;
        }

        // Exponential backoff with jitter
        int delay = Math.min(INITIAL_RECONNECT_DELAY_MS * (1 << (attempts - 1)), MAX_RECONNECT_DELAY_MS);
        delay += (int) (Math.random() * 500); // Add jitter

        log.info("Scheduling reconnect attempt {} for match {} in {}ms", attempts, matchId, delay);

        if (reconnectTask != null) {
            reconnectTask.cancel(false);
        }

        reconnectTask = reconnectExecutor.schedule(this::doConnect, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Disconnect from the WebSocket server.
     */
    public void disconnect() {
        intentionalDisconnect.set(true);
        autoReconnectEnabled.set(false);

        if (reconnectTask != null) {
            reconnectTask.cancel(false);
            reconnectTask = null;
        }

        if (session != null && session.isOpen()) {
            try {
                session.close();
            } catch (IOException e) {
                log.debug("Error closing WebSocket session: {}", e.getMessage());
            }
        }
        connected.set(false);
    }

    /**
     * Close this client and release resources.
     * Alias for {@link #disconnect()}.
     */
    @Override
    public void close() {
        disconnect();
        if (reconnectExecutor != null && !reconnectExecutor.isShutdown()) {
            reconnectExecutor.shutdownNow();
        }
    }

    /**
     * Enable or disable auto-reconnect.
     */
    public void setAutoReconnectEnabled(boolean enabled) {
        autoReconnectEnabled.set(enabled);
    }

    /**
     * Check if auto-reconnect is enabled.
     */
    public boolean isAutoReconnectEnabled() {
        return autoReconnectEnabled.get();
    }

    /**
     * Check if connected to the server.
     */
    public boolean isConnected() {
        return connected.get() && session != null && session.isOpen();
    }

    /**
     * Add a listener for snapshot updates.
     */
    public void addListener(Consumer<SnapshotData> listener) {
        listeners.add(listener);
    }

    /**
     * Remove a listener.
     */
    public void removeListener(Consumer<SnapshotData> listener) {
        listeners.remove(listener);
    }

    /**
     * Request an immediate snapshot (triggers server to send one).
     */
    public void requestSnapshot() {
        if (session != null && session.isOpen()) {
            try {
                log.debug("Requesting snapshot refresh");
                session.getBasicRemote().sendText("refresh");
            } catch (IOException e) {
                log.error("Failed to request snapshot: {}", e.getMessage(), e);
            }
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        log.info("Connected to snapshot WebSocket for match {}", matchId);
        connected.set(true);
    }

    @OnMessage
    public void onMessage(String message) {
        log.debug("Received WebSocket message, length={}", message.length());
        try {
            SnapshotData snapshot = parseSnapshotResponse(message);
                log.info("Parsed snapshot: matchId={}, tick={}, modules={}, entityCount={}",
                snapshot.matchId(), snapshot.tick(), snapshot.getModuleNames(), snapshot.getEntityCount());

            // Log detailed module/component info
            for (String moduleName : snapshot.getModuleNames()) {
                Map<String, List<Float>> moduleData = snapshot.getModuleData(moduleName);
                if (moduleData != null) {
                    Set<String> componentNames = moduleData.keySet();
                    int entityCount = moduleData.values().stream().findFirst().map(List::size).orElse(0);
                    log.debug("  Module '{}': components={}, entities={}", moduleName, componentNames, entityCount);
                }
            }

            for (Consumer<SnapshotData> listener : listeners) {
                try {
                    listener.accept(snapshot);
                } catch (Exception e) {
                    log.error("Error in snapshot listener: {}", e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse snapshot message: {}", e.getMessage(), e);
            log.debug("Raw message: {}", message);
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        log.info("Disconnected from snapshot WebSocket for match {}: {}", matchId, closeReason.getReasonPhrase());
        connected.set(false);

        // Schedule reconnect if not intentionally disconnected
        if (!intentionalDisconnect.get() && autoReconnectEnabled.get()) {
            scheduleReconnect();
        }
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        log.error("WebSocket error for match {}: {}", matchId, throwable.getMessage(), throwable);
        connected.set(false);

        // Schedule reconnect on error
        if (!intentionalDisconnect.get() && autoReconnectEnabled.get()) {
            scheduleReconnect();
        }
    }

    private SnapshotData parseSnapshotResponse(String json) throws IOException {
        Map<String, Object> response = objectMapper.readValue(json, new TypeReference<>() {});

        long matchId = ((Number) response.getOrDefault("matchId", 0)).longValue();
        long tick = ((Number) response.getOrDefault("tick", 0)).longValue();

        @SuppressWarnings("unchecked")
        Map<String, Map<String, List<Number>>> data =
            (Map<String, Map<String, List<Number>>>) response.get("data");

        // Convert Number lists to Float lists
        Map<String, Map<String, List<Float>>> snapshotData = new java.util.HashMap<>();
        if (data != null) {
            for (var moduleEntry : data.entrySet()) {
                Map<String, List<Float>> componentMap = new java.util.HashMap<>();
                for (var componentEntry : moduleEntry.getValue().entrySet()) {
                    List<Float> values = componentEntry.getValue().stream()
                        .map(Number::floatValue)
                        .toList();
                    componentMap.put(componentEntry.getKey(), values);
                    log.trace("    Component '{}': {} values", componentEntry.getKey(), values.size());
                }
                snapshotData.put(moduleEntry.getKey(), componentMap);
            }
        }

        return new SnapshotData(matchId, tick, snapshotData);
    }

    /**
     * Snapshot data received from the server.
     */
    public record SnapshotData(
        long matchId,
        long tick,
        Map<String, Map<String, List<Float>>> data
    ) {
        /**
         * Get all module names in this snapshot.
         */
        public java.util.Set<String> getModuleNames() {
            return data != null ? data.keySet() : java.util.Set.of();
        }

        /**
         * Get component data for a specific module.
         */
        public Map<String, List<Float>> getModuleData(String moduleName) {
            return data != null ? data.get(moduleName) : null;
        }

        /**
         * Get the number of entities (based on the first component's value count).
         */
        public int getEntityCount() {
            if (data == null || data.isEmpty()) {
                return 0;
            }
            for (var moduleData : data.values()) {
                for (var componentValues : moduleData.values()) {
                    return componentValues.size();
                }
            }
            return 0;
        }
    }
}
