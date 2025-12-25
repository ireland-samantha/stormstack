package com.lightningfirefly.game.renderering;

import com.lightningfirefly.game.orchestrator.Snapshot;
import com.lightningfirefly.game.orchestrator.SpriteSnapshotMapperImpl;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple application that boots up a GameRenderer and renders snapshots from the server.
 *
 * <p>Usage:
 * <pre>{@code
 * java SnapshotRendererApplication --server http://localhost:8080 --match 1
 * }</pre>
 *
 * <p>Options:
 * <ul>
 *   <li>--server, -s: Server URL (default: http://localhost:8080)</li>
 *   <li>--match, -m: Match ID to subscribe to (required)</li>
 *   <li>--width, -w: Window width (default: 800)</li>
 *   <li>--height, -h: Window height (default: 600)</li>
 *   <li>--poll: Polling interval in milliseconds (default: 100)</li>
 * </ul>
 */
@Slf4j
public class SnapshotRendererApplication {

    private final String serverUrl;
    private final long matchId;
    private final int windowWidth;
    private final int windowHeight;
    private final int pollIntervalMs;

    private final HttpClient httpClient;
    private GameRenderer renderer;
    private volatile Snapshot latestSnapshot;
    private volatile boolean running = true;

    public SnapshotRendererApplication(String serverUrl, long matchId, int windowWidth, int windowHeight, int pollIntervalMs) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.matchId = matchId;
        this.windowWidth = windowWidth;
        this.windowHeight = windowHeight;
        this.pollIntervalMs = pollIntervalMs;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Start the application.
     * This blocks until the window is closed.
     */
    public void run() {
        log.info("Starting SnapshotRendererApplication");
        log.info("Server: {}, Match: {}", serverUrl, matchId);

        // Configure sprite mapper
        SpriteSnapshotMapperImpl mapper = new SpriteSnapshotMapperImpl()
                .defaultSize(48, 48)
                .textureResolver(this::resolveTexture);

        // Create renderer
        renderer = GameRendererBuilder.create()
                .windowSize(windowWidth, windowHeight)
                .title("Snapshot Renderer - Match " + matchId)
                .spriteMapper(mapper)
                .build();

        renderer.setOnError(e -> log.error("Renderer error", e));

        // Start polling thread
        Thread poller = new Thread(this::pollSnapshots, "SnapshotPoller");
        poller.setDaemon(true);
        poller.start();

        // Start render loop (blocks until window closed)
        renderer.start(() -> {
            if (latestSnapshot != null) {
                renderer.renderSnapshot(latestSnapshot);
            }
        });

        // Cleanup
        running = false;
        log.info("Application stopped");
    }

    /**
     * Poll snapshots from the server.
     */
    private void pollSnapshots() {
        log.info("Starting snapshot polling every {}ms", pollIntervalMs);

        while (running) {
            try {
                Snapshot snapshot = fetchSnapshot();
                if (snapshot != null) {
                    latestSnapshot = snapshot;
                    log.debug("Received snapshot with {} modules",
                            snapshot.components() != null ? snapshot.components().size() : 0);
                }
            } catch (Exception e) {
                log.warn("Failed to fetch snapshot: {}", e.getMessage());
            }

            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Fetch a snapshot from the server.
     */
    private Snapshot fetchSnapshot() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/api/snapshots/match/" + matchId))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            return null;
        }

        return parseSnapshotFromJson(response.body());
    }

    /**
     * Parse snapshot JSON into Snapshot record.
     */
    private Snapshot parseSnapshotFromJson(String json) {
        Map<String, Map<String, List<Float>>> data = new HashMap<>();

        // Find the "snapshot" object in JSON
        Pattern modulePattern = Pattern.compile("\"(\\w+)\"\\s*:\\s*\\{([^}]+)\\}");
        Matcher moduleMatcher = modulePattern.matcher(json);

        while (moduleMatcher.find()) {
            String moduleName = moduleMatcher.group(1);
            if (moduleName.equals("snapshot") || moduleName.equals("matchId") || moduleName.equals("tick")) {
                continue;
            }

            String moduleContent = moduleMatcher.group(2);
            Map<String, List<Float>> componentData = new HashMap<>();

            // Parse component arrays
            Pattern componentPattern = Pattern.compile("\"(\\w+)\"\\s*:\\s*\\[([^\\]]*)]");
            Matcher componentMatcher = componentPattern.matcher(moduleContent);

            while (componentMatcher.find()) {
                String componentName = componentMatcher.group(1);
                String valuesStr = componentMatcher.group(2);

                List<Float> values = new ArrayList<>();
                if (!valuesStr.isEmpty()) {
                    for (String val : valuesStr.split(",")) {
                        val = val.trim();
                        if (!val.isEmpty()) {
                            try {
                                values.add(Float.parseFloat(val));
                            } catch (NumberFormatException e) {
                                // Skip non-numeric values
                            }
                        }
                    }
                }

                if (!values.isEmpty()) {
                    componentData.put(componentName, values);
                }
            }

            if (!componentData.isEmpty()) {
                data.put(moduleName, componentData);
            }
        }

        return new Snapshot(data);
    }

    /**
     * Resolve resource ID to texture path.
     */
    private String resolveTexture(long resourceId) {
        // Default checker textures based on resource ID parity
        return resourceId % 2 == 0 ? "textures/red-checker.png" : "textures/black-checker.png";
    }

    /**
     * Main entry point.
     */
    public static void main(String[] args) {
        String serverUrl = "http://localhost:8080";
        long matchId = -1;
        int windowWidth = 800;
        int windowHeight = 600;
        int pollIntervalMs = 100;

        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--server", "-s" -> {
                    if (i + 1 < args.length) {
                        serverUrl = args[++i];
                    }
                }
                case "--match", "-m" -> {
                    if (i + 1 < args.length) {
                        matchId = Long.parseLong(args[++i]);
                    }
                }
                case "--width", "-w" -> {
                    if (i + 1 < args.length) {
                        windowWidth = Integer.parseInt(args[++i]);
                    }
                }
                case "--height" -> {
                    if (i + 1 < args.length) {
                        windowHeight = Integer.parseInt(args[++i]);
                    }
                }
                case "--poll" -> {
                    if (i + 1 < args.length) {
                        pollIntervalMs = Integer.parseInt(args[++i]);
                    }
                }
                case "--help" -> {
                    printUsage();
                    return;
                }
            }
        }

        if (matchId < 0) {
            System.err.println("Error: Match ID is required");
            printUsage();
            System.exit(1);
        }

        SnapshotRendererApplication app = new SnapshotRendererApplication(
                serverUrl, matchId, windowWidth, windowHeight, pollIntervalMs);
        app.run();
    }

    private static void printUsage() {
        System.out.println("Usage: SnapshotRendererApplication [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --server, -s <url>    Server URL (default: http://localhost:8080)");
        System.out.println("  --match, -m <id>      Match ID to render (required)");
        System.out.println("  --width, -w <pixels>  Window width (default: 800)");
        System.out.println("  --height <pixels>     Window height (default: 600)");
        System.out.println("  --poll <ms>           Polling interval in ms (default: 100)");
        System.out.println("  --help                Show this help message");
    }
}
