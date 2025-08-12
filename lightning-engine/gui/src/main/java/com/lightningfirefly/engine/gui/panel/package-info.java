/**
 * GUI Panels for the Lightning Engine application.
 *
 * <h2>Overview</h2>
 * Panels are container components that extend {@link com.lightningfirefly.engine.rendering.render2d.impl.opengl.GLPanel}
 * and provide domain-specific functionality.
 *
 * <h2>Available Panels</h2>
 *
 * <h3>SnapshotPanel</h3>
 * Displays real-time ECS snapshot data:
 * <ul>
 *   <li>WebSocket connection to snapshot endpoint</li>
 *   <li>TreeView showing entities grouped by module</li>
 *   <li>Component values displayed per entity</li>
 *   <li>Auto-refresh on new snapshots</li>
 * </ul>
 *
 * <pre>{@code
 * SnapshotPanel panel = new SnapshotPanel(x, y, width, height, serverUrl, matchId);
 * panel.connect();  // Start receiving snapshots
 *
 * // In render loop
 * panel.update();
 *
 * // Cleanup
 * panel.dispose();
 * }</pre>
 *
 * <h3>ResourcePanel</h3>
 * Browse and download engine resources:
 * <ul>
 *   <li>Lists available resources from REST API</li>
 *   <li>Downloads to user's Downloads directory</li>
 *   <li>Shows resource type and metadata</li>
 * </ul>
 *
 * <pre>{@code
 * ResourcePanel panel = new ResourcePanel(x, y, width, height, serverUrl);
 *
 * // In render loop
 * panel.update();
 *
 * // Cleanup
 * panel.dispose();
 * }</pre>
 *
 * <h2>Creating New Panels</h2>
 * <pre>{@code
 * @Slf4j
 * public class MyPanel extends Panel {
 *
 *     public MyPanel(int x, int y, int width, int height) {
 *         super(x, y, width, height);
 *         setTitle("My Panel");
 *
 *         // Add child components
 *         Label label = new Label(x + 10, y + 30, "Hello");
 *         addChild(label);
 *
 *         Button button = new Button(x + 10, y + 50, 100, 30, "Click");
 *         button.setOnClick(this::onButtonClick);
 *         addChild(button);
 *     }
 *
 *     private void onButtonClick() {
 *         log.info("Button clicked!");
 *     }
 *
 *     public void update() {
 *         // Called every frame - update UI state here
 *     }
 *
 *     public void dispose() {
 *         // Cleanup resources (connections, threads, etc.)
 *     }
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * Panels may receive data from background threads (WebSocket, HTTP).
 * Use volatile fields and update UI only in the {@code update()} method
 * which is called from the render thread.
 *
 * @see com.lightningfirefly.engine.gui.panel.SnapshotPanel
 * @see com.lightningfirefly.engine.gui.panel.ResourcePanel
 * @see com.lightningfirefly.engine.rendering.render2d.impl.opengl.GLPanel
 */
package com.lightningfirefly.engine.gui.panel;
