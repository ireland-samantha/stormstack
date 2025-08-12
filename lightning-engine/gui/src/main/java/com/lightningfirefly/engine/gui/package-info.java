/**
 * Lightning Engine GUI Application.
 *
 * <h2>Overview</h2>
 * Desktop GUI client for the Lightning Engine, providing:
 * <ul>
 *   <li>Real-time snapshot viewing via WebSocket</li>
 *   <li>Resource browsing and downloading</li>
 *   <li>Entity-Component visualization in tree view</li>
 * </ul>
 *
 * <h2>Architecture</h2>
 * This module uses only GUI abstractions from rendering-core.
 * No OpenGL or rendering-specific code should be imported here.
 *
 * <pre>
 * EngineGuiApplication (entry point)
 *     ├── Window (abstraction from rendering-core)
 *     ├── SnapshotPanel (panel subpackage)
 *     │       └── SnapshotWebSocketClient (service subpackage)
 *     └── ResourcePanel (panel subpackage)
 *             └── ResourceService (service subpackage)
 * </pre>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link com.lightningfirefly.engine.gui.EngineGuiApplication} - Main entry point</li>
 * </ul>
 *
 * <h2>Subpackages</h2>
 * <ul>
 *   <li>{@code panel} - UI panels (SnapshotPanel, ResourcePanel)</li>
 *   <li>{@code service} - Backend services (WebSocket client, REST client)</li>
 * </ul>
 *
 * <h2>Running the Application</h2>
 * <pre>
 * java -jar engine-gui.jar [options]
 *
 * Options:
 *   -s, --server &lt;url&gt;    Server URL (default: http://localhost:8080)
 *   -m, --match &lt;id&gt;      Match ID to subscribe to (default: 1)
 *   -h, --help            Show help message
 * </pre>
 *
 * <h2>Dependencies</h2>
 * <ul>
 *   <li>rendering-core - GUI framework abstractions</li>
 *   <li>jakarta.websocket-api - WebSocket client</li>
 *   <li>jakarta.json - JSON parsing</li>
 *   <li>tyrus-standalone-client - WebSocket implementation</li>
 * </ul>
 *
 * @see com.lightningfirefly.engine.gui.EngineGuiApplication
 * @see com.lightningfirefly.engine.gui.panel
 * @see com.lightningfirefly.engine.gui.service
 */
package com.lightningfirefly.engine.gui;
