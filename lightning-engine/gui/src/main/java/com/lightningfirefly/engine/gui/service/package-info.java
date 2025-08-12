/**
 * Backend Services for the Lightning Engine GUI.
 *
 * <h2>Overview</h2>
 * Services handle communication with the Lightning Engine server:
 * <ul>
 *   <li>WebSocket connections for real-time data</li>
 *   <li>REST API calls for resources and configuration</li>
 * </ul>
 *
 * <h2>Available Services</h2>
 *
 * <h3>SnapshotWebSocketClient</h3>
 * WebSocket client for receiving ECS snapshots:
 *
 * <pre>{@code
 * SnapshotWebSocketClient client = new SnapshotWebSocketClient(
 *     "http://localhost:8080",
 *     matchId
 * );
 *
 * // Add listener for snapshots
 * client.addListener(snapshot -> {
 *     log.info("Tick: {}", snapshot.tick());
 *     for (String module : snapshot.getModuleNames()) {
 *         Map<String, List<Long>> data = snapshot.getModuleData(module);
 *         // Process component data...
 *     }
 * });
 *
 * // Connect and start receiving
 * client.connect();
 *
 * // Request snapshot on demand
 * client.requestSnapshot();
 *
 * // Disconnect when done
 * client.disconnect();
 * }</pre>
 *
 * <h4>SnapshotData Format</h4>
 * <pre>
 * SnapshotData
 *   ├── matchId: long
 *   ├── tick: int
 *   └── data: Map&lt;ModuleName, Map&lt;ComponentName, List&lt;Long&gt;&gt;&gt;
 *
 * Example:
 *   matchId: 1
 *   tick: 42
 *   data:
 *     "GameFactory":
 *       "POSITION_X": [100, 200, 300]  // Entity 0, 1, 2
 *       "POSITION_Y": [50, 60, 70]     // Entity 0, 1, 2
 *     "PhysicsModule":
 *       "VELOCITY_X": [10, 0, -5]
 *       "VELOCITY_Y": [0, 0, 0]
 * </pre>
 *
 * <h3>ResourceService</h3>
 * REST client for resource management:
 *
 * <pre>{@code
 * ResourceService service = new ResourceService("http://localhost:8080");
 *
 * // List all resources
 * service.listResources().thenAccept(resources -> {
 *     for (ResourceInfo info : resources) {
 *         log.info("Resource: {} ({})", info.name(), info.type());
 *     }
 * });
 *
 * // Download resource to file
 * Path target = Path.of(System.getProperty("user.home"), "Downloads", "texture.png");
 * service.downloadResourceToFile(resourceId, target)
 *     .thenAccept(bytes -> log.info("Downloaded {} bytes", bytes));
 * }</pre>
 *
 * <h2>Error Handling</h2>
 * Services use CompletableFuture for async operations. Handle errors with:
 *
 * <pre>{@code
 * service.listResources()
 *     .thenAccept(this::processResources)
 *     .exceptionally(error -> {
 *         log.error("Failed to list resources", error);
 *         return null;
 *     });
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * Service callbacks run on IO threads. Don't update UI directly - set volatile
 * flags and update in the render thread.
 *
 * @see com.lightningfirefly.engine.gui.service.SnapshotWebSocketClient
 * @see com.lightningfirefly.engine.gui.service.ResourceService
 */
package com.lightningfirefly.engine.gui.service;
