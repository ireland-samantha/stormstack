/*
 * Copyright (c) 2026 Samantha Ireland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */


package ca.samanthaireland.game.orchestrator;

import ca.samanthaireland.engine.api.resource.adapter.EngineClient;
import ca.samanthaireland.game.renderering.GameRenderer;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simple orchestrator that connects backend snapshots to a GameRenderer.
 *
 * <p>Implements a clean data flow:
 * <ol>
 *   <li>Subscribe to snapshots via WebSocket</li>
 *   <li>Queue snapshots for rendering</li>
 *   <li>Render when snapshot changes</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>{@code
 * var client = EngineClient.connect("http://localhost:8080");
 * var renderer = new DefaultGameRenderer(window);
 *
 * var orchestrator = Orchestrator.create()
 *     .client(client)
 *     .renderer(renderer)
 *     .forMatch(matchId)
 *     .build();
 *
 * // Start the orchestration loop
 * orchestrator.start();
 *
 * // Or run for specific frames (testing)
 * orchestrator.runFrames(60);
 *
 * // Stop when done
 * orchestrator.stop();
 * }</pre>
 */
@Slf4j
public final class Orchestrator {

    private final EngineClient client;
    private final EngineClient.ContainerClient containerClient;
    private final GameRenderer renderer;
    private final long matchId;
    private final SpriteSnapshotMapper spriteMapper;
    private final Duration pollInterval;

    // WebSocket subscription
    private WebSocket webSocket;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    // Snapshot queue
    private final BlockingQueue<Snapshot> snapshotQueue = new LinkedBlockingQueue<>(10);
    private final AtomicReference<Snapshot> latestSnapshot = new AtomicReference<>();
    private volatile long lastRenderedTick = -1;

    // Polling fallback
    private ScheduledExecutorService pollExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Orchestrator(Builder builder) {
        this.client = Objects.requireNonNull(builder.client, "client is required");
        if (builder.containerId <= 0) {
            throw new IllegalArgumentException("containerId is required");
        }
        this.containerClient = client.container(builder.containerId);
        this.renderer = Objects.requireNonNull(builder.renderer, "renderer is required");
        this.matchId = builder.matchId;
        this.spriteMapper = builder.spriteMapper != null ? builder.spriteMapper : defaultMapper();
        this.pollInterval = builder.pollInterval;

        renderer.setSpriteMapper(spriteMapper);
    }

    /**
     * Create a new orchestrator builder.
     */
    public static Builder create() {
        return new Builder();
    }

    /**
     * Start the orchestration loop.
     * This method blocks until stop() is called.
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Already running");
        }

        connectWebSocket();
        startPollingFallback();

        renderer.start(() -> renderLatestSnapshot());
    }

    /**
     * Start the orchestration loop asynchronously.
     */
    public void startAsync() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Already running");
        }

        connectWebSocket();
        startPollingFallback();

        renderer.startAsync(() -> renderLatestSnapshot());
    }

    /**
     * Run for a specific number of frames (useful for testing).
     */
    public void runFrames(int frames) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Already running");
        }

        try {
            // Fetch initial snapshot via HTTP
            fetchSnapshot();

            renderer.runFrames(frames, () -> renderLatestSnapshot());
        } finally {
            running.set(false);
        }
    }

    /**
     * Run for a specific number of frames with callback (useful for testing).
     */
    public void runFrames(int frames, Runnable onFrame) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Already running");
        }

        try {
            // Fetch initial snapshot via HTTP
            fetchSnapshot();

            renderer.runFrames(frames, () -> {
                renderLatestSnapshot();
                if (onFrame != null) onFrame.run();
            });
        } finally {
            running.set(false);
        }
    }

    /**
     * Stop the orchestration loop.
     */
    public void stop() {
        running.set(false);

        if (pollExecutor != null) {
            pollExecutor.shutdownNow();
            pollExecutor = null;
        }

        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Closing");
            webSocket = null;
        }

        connected.set(false);
        renderer.stop();
    }

    /**
     * Check if running.
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Check if connected to WebSocket.
     */
    public boolean isWebSocketConnected() {
        return connected.get() && webSocket != null;
    }

    /**
     * Get the current match ID.
     */
    public long matchId() {
        return matchId;
    }

    /**
     * Get the latest snapshot.
     */
    public Snapshot latestSnapshot() {
        return latestSnapshot.get();
    }

    /**
     * Manually fetch the latest snapshot from the server.
     */
    public void fetchSnapshot() {
        try {
            var snapshotOpt = containerClient.getSnapshot(matchId);
            snapshotOpt.ifPresent(snapshot -> {
                var parsed = client.parseSnapshot(snapshot.data());
                onSnapshotReceived(new Snapshot(snapshot.tick(), parsed.data()));
            });
        } catch (Exception e) {
            log.warn("Failed to fetch snapshot: {}", e.getMessage());
        }
    }

    /**
     * Tick the simulation and fetch snapshot.
     */
    public void tickAndFetch() {
        containerClient.tick();
        fetchSnapshot();
    }

    // ========== Internal ==========

    private void renderLatestSnapshot() {
        Snapshot snapshot = latestSnapshot.get();
        if (snapshot == null) {
            return;
        }

        // Only re-render if tick changed
        if (snapshot.tick() != lastRenderedTick) {
            lastRenderedTick = snapshot.tick();
            renderer.renderSnapshot(snapshot);
        }
    }

    private void onSnapshotReceived(Snapshot snapshot) {
        latestSnapshot.set(snapshot);

        // Also try to queue (non-blocking)
        snapshotQueue.offer(snapshot);
    }

    private void connectWebSocket() {
        String wsUrl = client.baseUrl()
                .replace("http://", "ws://")
                .replace("https://", "wss://")
                + "/ws/snapshots/" + matchId;

        try {
            HttpClient httpClient = HttpClient.newHttpClient();
            webSocket = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(URI.create(wsUrl), new WebSocketListener())
                    .orTimeout(10, TimeUnit.SECONDS)
                    .join();

            connected.set(true);
            log.info("Connected to WebSocket for match {} at {}", matchId, wsUrl);
        } catch (Exception e) {
            log.warn("WebSocket connection failed, using HTTP polling: {}", e.getMessage());
            connected.set(false);
        }
    }

    private void startPollingFallback() {
        // Always start polling as backup
        pollExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "orchestrator-poll");
            t.setDaemon(true);
            return t;
        });

        pollExecutor.scheduleAtFixedRate(() -> {
            if (running.get()) {
                fetchSnapshot();
            }
        }, 0, pollInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    private SpriteSnapshotMapper defaultMapper() {
        return new SpriteSnapshotMapperImpl()
                .defaultSize(32, 32)
                .entityIdComponent("ENTITY_ID")
                .textureResolver(entityId -> "textures/default.png");
    }

    private class WebSocketListener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            log.debug("WebSocket opened for match {}", matchId);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                try {
                    String json = buffer.toString();
                    buffer.setLength(0);
                    parseAndProcessSnapshot(json);
                } catch (Exception e) {
                    log.warn("Failed to parse WebSocket message: {}", e.getMessage());
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.debug("WebSocket closed: {} - {}", statusCode, reason);
            connected.set(false);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("WebSocket error: {}", error.getMessage());
            connected.set(false);
        }

        private void parseAndProcessSnapshot(String json) {
            try {
                // Parse tick
                long tick = 0;
                int tickStart = json.indexOf("\"tick\":");
                if (tickStart != -1) {
                    int tickEnd = findValueEnd(json, tickStart + 7);
                    tick = Long.parseLong(json.substring(tickStart + 7, tickEnd).trim());
                }

                // Parse data field
                Map<String, Map<String, List<Float>>> snapshotData = new LinkedHashMap<>();
                int dataStart = json.indexOf("\"data\":");
                if (dataStart != -1) {
                    int objStart = json.indexOf("{", dataStart);
                    if (objStart != -1) {
                        snapshotData = parseModuleMap(json, objStart);
                    }
                }

                Snapshot snapshot = new Snapshot(tick, snapshotData);
                onSnapshotReceived(snapshot);
                log.debug("WebSocket snapshot received: tick={}, modules={}", tick, snapshotData.keySet());
            } catch (Exception e) {
                log.warn("Error parsing WebSocket snapshot JSON: {}", e.getMessage());
            }
        }

        private int findValueEnd(String json, int start) {
            int pos = start;
            while (pos < json.length()) {
                char c = json.charAt(pos);
                if (c == ',' || c == '}' || c == ']') {
                    return pos;
                }
                pos++;
            }
            return json.length();
        }

        private Map<String, Map<String, List<Float>>> parseModuleMap(String json, int objStart) {
            Map<String, Map<String, List<Float>>> modules = new LinkedHashMap<>();
            int pos = objStart + 1;

            while (pos < json.length()) {
                // Skip whitespace
                while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) pos++;
                if (pos >= json.length() || json.charAt(pos) == '}') break;

                // Parse module name
                int nameStart = json.indexOf("\"", pos);
                if (nameStart == -1) break;
                int nameEnd = json.indexOf("\"", nameStart + 1);
                if (nameEnd == -1) break;
                String moduleName = json.substring(nameStart + 1, nameEnd);

                // Find module object
                int colonPos = json.indexOf(":", nameEnd);
                int moduleObjStart = json.indexOf("{", colonPos);
                if (moduleObjStart == -1) break;

                // Find matching closing brace
                int braceCount = 1;
                int moduleObjEnd = moduleObjStart + 1;
                while (moduleObjEnd < json.length() && braceCount > 0) {
                    if (json.charAt(moduleObjEnd) == '{') braceCount++;
                    else if (json.charAt(moduleObjEnd) == '}') braceCount--;
                    moduleObjEnd++;
                }

                String moduleJson = json.substring(moduleObjStart, moduleObjEnd);
                Map<String, List<Float>> componentData = parseComponentMap(moduleJson);
                if (!componentData.isEmpty()) {
                    modules.put(moduleName, componentData);
                }

                pos = moduleObjEnd;
            }

            return modules;
        }

        private Map<String, List<Float>> parseComponentMap(String json) {
            Map<String, List<Float>> components = new LinkedHashMap<>();
            int pos = 1;

            while (pos < json.length()) {
                // Find component name
                int nameStart = json.indexOf("\"", pos);
                if (nameStart == -1) break;
                int nameEnd = json.indexOf("\"", nameStart + 1);
                if (nameEnd == -1) break;
                String componentName = json.substring(nameStart + 1, nameEnd);

                // Find array
                int arrayStart = json.indexOf("[", nameEnd);
                if (arrayStart == -1) break;
                int arrayEnd = json.indexOf("]", arrayStart);
                if (arrayEnd == -1) break;

                String arrayStr = json.substring(arrayStart + 1, arrayEnd);
                List<Float> values = new ArrayList<>();
                if (!arrayStr.trim().isEmpty()) {
                    for (String val : arrayStr.split(",")) {
                        String trimmed = val.trim();
                        if (!trimmed.isEmpty()) {
                            try {
                                values.add(Float.parseFloat(trimmed));
                            } catch (NumberFormatException e) {
                                // Skip non-numeric values
                            }
                        }
                    }
                }
                components.put(componentName, values);

                pos = arrayEnd + 1;
            }

            return components;
        }
    }

    /**
     * Builder for Orchestrator.
     */
    public static class Builder {
        private EngineClient client;
        private long containerId;
        private GameRenderer renderer;
        private long matchId;
        private SpriteSnapshotMapper spriteMapper;
        private Duration pollInterval = Duration.ofMillis(100);

        public Builder client(EngineClient client) {
            this.client = client;
            return this;
        }

        public Builder forContainer(long containerId) {
            this.containerId = containerId;
            return this;
        }

        public Builder renderer(GameRenderer renderer) {
            this.renderer = renderer;
            return this;
        }

        public Builder forMatch(long matchId) {
            this.matchId = matchId;
            return this;
        }

        public Builder spriteMapper(SpriteSnapshotMapper mapper) {
            this.spriteMapper = mapper;
            return this;
        }

        public Builder pollInterval(Duration interval) {
            this.pollInterval = interval;
            return this;
        }

        public Orchestrator build() {
            return new Orchestrator(this);
        }
    }
}
