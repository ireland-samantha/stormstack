package com.lightningfirefly.engine.gui.panel;

import com.lightningfirefly.engine.gui.service.MatchService;
import com.lightningfirefly.engine.gui.service.MatchService.MatchInfo;
import com.lightningfirefly.engine.gui.service.SnapshotService;
import com.lightningfirefly.engine.gui.service.SnapshotWebSocketClient;
import com.lightningfirefly.engine.gui.service.SnapshotWebSocketClient.SnapshotData;
import com.lightningfirefly.engine.rendering.render2d.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Panel for displaying snapshot data with entities and their components.
 *
 * <p>This panel supports multiple matches with auto-connect capability.
 * It can connect to all matches from the API and display their snapshots
 * in a unified tree view organized by match.
 *
 * <p>This panel is decoupled from OpenGL implementation. It uses ComponentFactory
 * to create UI components and only depends on interfaces.
 */
@Slf4j
public class SnapshotPanel extends AbstractWindowComponent {

    private static final int DETAIL_PANEL_WIDTH = 350;

    private final Panel visualPanel;
    private final Panel detailPanel;
    private final ComponentFactory factory;
    private final ComponentFactory.Colours colours;
    private final String serverUrl;
    private final MatchService matchService;
    private final SnapshotService snapshotService;
    private final TreeView entityTree;
    private final Label statusLabel;
    private final Label tickLabel;
    private final Button loadAllButton;
    private final Button refreshButton;

    // Entity detail panel components
    private final Label detailTitleLabel;
    private final ListView componentList;

    // Support multiple matches with auto-reconnect
    private final Map<Long, SnapshotWebSocketClient> clients = new ConcurrentHashMap<>();
    private final Map<Long, SnapshotData> snapshots = new ConcurrentHashMap<>();
    private volatile boolean autoConnectStarted = false;

    // Currently selected entity info
    private Long selectedMatchId = null;
    private String selectedModuleName = null;
    private int selectedEntityIndex = -1;
    private Long selectedEntityId = null;

    // Legacy single-match client for backward compatibility
    private SnapshotWebSocketClient snapshotClient;

    private volatile SnapshotData latestSnapshot;
    private volatile boolean needsUpdate = false;

    public SnapshotPanel(ComponentFactory factory, int x, int y, int width, int height,
                         String serverUrl, long matchId) {
        super(x, y, width, height);
        this.factory = factory;
        this.colours = factory.getColours();
        this.serverUrl = serverUrl;
        this.matchService = new MatchService(serverUrl);
        this.snapshotService = new SnapshotService(serverUrl);

        // Calculate layout - left side for tree, right side for entity details
        int leftPanelWidth = width - DETAIL_PANEL_WIDTH - 20;

        // Create visual panel container (for the left side with tree)
        this.visualPanel = factory.createPanel(x, y, leftPanelWidth, height);
        this.visualPanel.setTitle("Snapshot Viewer");

        this.snapshotClient = new SnapshotWebSocketClient(serverUrl, matchId);

        // Create status label
        statusLabel = factory.createLabel(x + 10, y + 35, "Connecting...", 12.0f);
        statusLabel.setTextColor(colours.textSecondary());

        // Create tick label
        tickLabel = factory.createLabel(x + leftPanelWidth - 150, y + 35, "Tick: -", 12.0f);
        tickLabel.setTextColor(colours.textSecondary());

        // Create Load All button (primary action)
        loadAllButton = factory.createButton(x + 10, y + 55, 100, 28, "Load All");
        loadAllButton.setBackgroundColor(colours.accent());
        loadAllButton.setOnClick(this::loadAllSnapshots);

        // Create refresh button
        refreshButton = factory.createButton(x + 120, y + 55, 100, 28, "Refresh");
        refreshButton.setOnClick(this::loadAllSnapshots);

        // Create entity tree (takes up remaining height on left)
        int treeY = y + 95;
        int treeHeight = height - treeY - 10 + y;
        entityTree = factory.createTreeView(x + 10, treeY, leftPanelWidth - 20, treeHeight);
        entityTree.setOnSelect(this::onTreeSelectionChanged);

        // Add components to visual panel
        visualPanel.addChild((WindowComponent) statusLabel);
        visualPanel.addChild((WindowComponent) tickLabel);
        visualPanel.addChild((WindowComponent) loadAllButton);
        visualPanel.addChild((WindowComponent) refreshButton);
        visualPanel.addChild((WindowComponent) entityTree);

        // Create detail panel on the right side
        int detailX = x + leftPanelWidth + 10;
        this.detailPanel = factory.createPanel(detailX, y, DETAIL_PANEL_WIDTH, height);
        this.detailPanel.setTitle("Entity Components");

        // Create detail panel title label
        detailTitleLabel = factory.createLabel(detailX + 10, y + 35, "Select an entity", 14.0f);
        detailTitleLabel.setTextColor(colours.textPrimary());

        // Create component list
        int listY = y + 55;
        int listHeight = height - listY - 10 + y;
        componentList = factory.createListView(detailX + 10, listY, DETAIL_PANEL_WIDTH - 20, listHeight);

        detailPanel.addChild((WindowComponent) detailTitleLabel);
        detailPanel.addChild((WindowComponent) componentList);

        // Setup snapshot listener
        snapshotClient.addListener(this::onSnapshotReceived);
    }

    /**
     * Load all snapshots via REST API (primary method).
     * This fetches snapshots for all matches in a single request.
     */
    public void loadAllSnapshots() {
        statusLabel.setText("Loading snapshots...");
        statusLabel.setTextColor(colours.textSecondary());

        snapshotService.getAllSnapshots().thenAccept(snapshotList -> {
            snapshots.clear();
            for (SnapshotService.SnapshotData restSnapshot : snapshotList) {
                // Convert from REST SnapshotData to WebSocket SnapshotData format
                SnapshotData wsSnapshot = new SnapshotData(
                        restSnapshot.matchId(),
                        restSnapshot.tick(),
                        restSnapshot.data()
                );
                snapshots.put(restSnapshot.matchId(), wsSnapshot);
                if (latestSnapshot == null || restSnapshot.tick() > latestSnapshot.tick()) {
                    latestSnapshot = wsSnapshot;
                }
            }
            log.info("Loaded snapshots for {} matches", snapshotList.size());
            statusLabel.setText("Loaded " + snapshotList.size() + " match(es)");
            statusLabel.setTextColor(colours.green());
            needsUpdate = true;
        });
    }

    private void toggleConnection() {
        if (snapshotClient.isConnected()) {
            disconnect();
        } else {
            connect();
        }
    }

    /**
     * Connect to the snapshot WebSocket.
     */
    public void connect() {
        try {
            snapshotClient.connect();
            statusLabel.setText("Status: Connected");
            statusLabel.setTextColor(colours.green());
        } catch (Exception e) {
            statusLabel.setText("Status: Error - " + e.getMessage());
            statusLabel.setTextColor(colours.red());
        }
    }

    /**
     * Disconnect from the snapshot WebSocket.
     */
    public void disconnect() {
        snapshotClient.disconnect();
        statusLabel.setText("Status: Disconnected");
        statusLabel.setTextColor(colours.textSecondary());
    }

    /**
     * Request a snapshot from the server.
     */
    public void requestSnapshot() {
        if (snapshotClient.isConnected()) {
            snapshotClient.requestSnapshot();
        }
    }

    /**
     * Request snapshots from all connected matches and refresh the tree.
     */
    public void requestAllSnapshots() {
        // Request from legacy client
        if (snapshotClient.isConnected()) {
            snapshotClient.requestSnapshot();
        }
        // Request from all multi-match clients
        for (SnapshotWebSocketClient client : clients.values()) {
            if (client.isConnected()) {
                client.requestSnapshot();
            }
        }
        // Trigger tree rebuild on next update cycle
        needsUpdate = true;
    }

    /**
     * Auto-connect to all matches from the API.
     */
    public void autoConnectToAllMatches() {
        statusLabel.setText("Loading matches...");
        statusLabel.setTextColor(colours.textSecondary());

        matchService.listMatches().thenAccept(matchInfos -> {
            log.info("Found {} matches to connect", matchInfos.size());
            int connectedCount = 0;
            for (MatchInfo match : matchInfos) {
                try {
                    connectToMatch(match.id());
                    connectedCount++;
                } catch (Exception e) {
                    log.warn("Failed to connect to match {}: {}", match.id(), e.getMessage());
                }
            }
            int finalCount = connectedCount;
            statusLabel.setText("Connected to " + finalCount + " matches");
            statusLabel.setTextColor(colours.green());
            needsUpdate = true;
        });
    }

    /**
     * Connect to a specific match by ID.
     */
    public void connectToMatch(long matchId) {
        if (clients.containsKey(matchId)) {
            log.debug("Already connected to match {}", matchId);
            return;
        }

        SnapshotWebSocketClient client = new SnapshotWebSocketClient(serverUrl, matchId);
        client.addListener(snapshot -> onMatchSnapshotReceived(matchId, snapshot));

        try {
            client.connect();
            clients.put(matchId, client);
            log.info("Connected to match {}", matchId);

            // Request initial snapshot
            client.requestSnapshot();
        } catch (Exception e) {
            log.error("Failed to connect to match {}", matchId, e);
        }
    }

    /**
     * Disconnect from a specific match.
     */
    public void disconnectFromMatch(long matchId) {
        SnapshotWebSocketClient client = clients.remove(matchId);
        if (client != null) {
            client.disconnect();
            snapshots.remove(matchId);
            needsUpdate = true;
        }
    }

    /**
     * Disconnect from all matches.
     */
    public void disconnectAll() {
        // Disconnect legacy client
        if (snapshotClient.isConnected()) {
            snapshotClient.disconnect();
        }
        // Disconnect all multi-match clients
        for (SnapshotWebSocketClient client : clients.values()) {
            client.disconnect();
        }
        clients.clear();
        snapshots.clear();
        statusLabel.setText("Status: Disconnected");
        statusLabel.setTextColor(colours.textSecondary());
        needsUpdate = true;
    }

    private void onMatchSnapshotReceived(long matchId, SnapshotData snapshot) {
        snapshots.put(matchId, snapshot);
        this.latestSnapshot = snapshot;
        // Don't set needsUpdate - only rebuild tree on explicit Refresh click
        // This prevents the tree from constantly refreshing while the user is interacting

        // Update the selected entity's details if it's from this match
        if (selectedMatchId != null && selectedMatchId == matchId) {
            needsDetailUpdate = true;
        }
    }

    private void onSnapshotReceived(SnapshotData snapshot) {
        this.latestSnapshot = snapshot;
        // Don't set needsUpdate - only rebuild tree on explicit Refresh click
    }

    // Flag for updating just the detail panel, not the whole tree
    private volatile boolean needsDetailUpdate = false;

    /**
     * Get the number of connected matches.
     */
    public int getConnectedMatchCount() {
        return clients.size() + (snapshotClient.isConnected() ? 1 : 0);
    }

    /**
     * Get all snapshots by match ID.
     */
    public Map<Long, SnapshotData> getAllSnapshots() {
        return new HashMap<>(snapshots);
    }

    /**
     * Handle tree selection changes.
     */
    private void onTreeSelectionChanged(TreeNode selectedNode) {
        if (selectedNode == null) {
            clearEntityDetails();
            return;
        }

        // Determine what was selected by traversing the tree path
        // Structure: Matches (root) -> Match X -> Module -> Entity
        String label = selectedNode.getLabel();

        // Check if it's an entity node (starts with "Entity ")
        if (label.startsWith("Entity ")) {
            TreeNode moduleNode = findParent(selectedNode);
            TreeNode matchNode = moduleNode != null ? findParent(moduleNode) : null;

            if (matchNode != null && moduleNode != null) {
                // Parse match ID from "Match X (Tick: Y)"
                String matchLabel = matchNode.getLabel();
                try {
                    long matchId = parseMatchIdFromLabel(matchLabel);
                    String moduleName = moduleNode.getLabel();

                    // Parse entity ID or index from label
                    // Format: "Entity 123" (ENTITY_ID) or "Entity (idx: 0)" (fallback)
                    Long entityId = null;
                    int entityIndex = -1;

                    if (label.contains("(idx: ")) {
                        // Fallback index format: "Entity (idx: 0)"
                        String indexPart = label.substring(label.indexOf("(idx: ") + 6, label.indexOf(")"));
                        entityIndex = Integer.parseInt(indexPart);
                    } else {
                        // ENTITY_ID format: "Entity 123"
                        entityId = Long.parseLong(label.replace("Entity ", ""));
                        // Find the array index for this entity ID
                        entityIndex = findEntityIndexByEntityId(matchId, moduleName, entityId);
                    }

                    if (entityIndex >= 0) {
                        showEntityDetails(matchId, moduleName, entityIndex, entityId);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse entity selection: {}", e.getMessage());
                }
            }
        } else {
            clearEntityDetails();
        }
    }

    /**
     * Find the array index for an entity given its ENTITY_ID.
     */
    private int findEntityIndexByEntityId(long matchId, String moduleName, long entityId) {
        SnapshotData snapshot = snapshots.get(matchId);
        if (snapshot == null && latestSnapshot != null && latestSnapshot.matchId() == matchId) {
            snapshot = latestSnapshot;
        }
        if (snapshot == null) return -1;

        Map<String, List<Float>> moduleData = snapshot.getModuleData(moduleName);
        if (moduleData == null) return -1;

        List<Float> entityIds = moduleData.get("ENTITY_ID");
        if (entityIds == null) return -1;

        for (int i = 0; i < entityIds.size(); i++) {
            Float id = entityIds.get(i);
            if (id != null && !Float.isNaN(id) && id.longValue() == entityId) {
                return i;
            }
        }
        return -1;
    }

    private long parseMatchIdFromLabel(String label) {
        // Format: "Match X (Tick: Y)" or just "Match X"
        String matchPart = label.split("\\(")[0].trim();
        return Long.parseLong(matchPart.replace("Match ", ""));
    }

    private TreeNode findParent(TreeNode node) {
        // Find parent by searching all nodes
        for (TreeNode root : entityTree.getRootNodes()) {
            TreeNode parent = findParentRecursive(root, node);
            if (parent != null) return parent;
        }
        return null;
    }

    private TreeNode findParentRecursive(TreeNode current, TreeNode target) {
        for (TreeNode child : current.getChildren()) {
            if (child == target) return current;
            TreeNode found = findParentRecursive(child, target);
            if (found != null) return found;
        }
        return null;
    }

    private void showEntityDetails(long matchId, String moduleName, int entityIndex, Long entityId) {
        selectedMatchId = matchId;
        selectedModuleName = moduleName;
        selectedEntityIndex = entityIndex;
        selectedEntityId = entityId;

        SnapshotData snapshot = snapshots.get(matchId);
        if (snapshot == null) {
            // Try legacy client
            if (latestSnapshot != null && latestSnapshot.matchId() == matchId) {
                snapshot = latestSnapshot;
            }
        }

        if (snapshot == null) {
            detailTitleLabel.setText("No data for Match " + matchId);
            componentList.setItems(List.of());
            return;
        }

        // Use entity ID in title if available, otherwise fall back to index
        String entityLabel = entityId != null ? String.valueOf(entityId) : "(idx: " + entityIndex + ")";
        detailTitleLabel.setText("Match " + matchId + " / " + moduleName + " / Entity " + entityLabel);

        Map<String, List<Float>> moduleData = snapshot.getModuleData(moduleName);
        if (moduleData == null) {
            componentList.setItems(List.of("No module data"));
            return;
        }

        List<String> items = new ArrayList<>();
        List<String> componentNames = new ArrayList<>(moduleData.keySet());
        componentNames.sort(String::compareTo);

        for (String componentName : componentNames) {
            List<Float> values = moduleData.get(componentName);
            if (entityIndex < values.size()) {
                float value = values.get(entityIndex);
                String displayValue = formatComponentValue(componentName, value);
                items.add(componentName + ": " + displayValue);
            }
        }

        componentList.setItems(items);
    }

    private void clearEntityDetails() {
        selectedMatchId = null;
        selectedModuleName = null;
        selectedEntityIndex = -1;
        selectedEntityId = null;
        detailTitleLabel.setText("Select an entity");
        componentList.setItems(List.of());
    }

    /**
     * Update the panel. Call this from the render loop.
     */
    public void update() {
        // Auto-connect to WebSocket on first update
        if (!autoConnectStarted) {
            autoConnectStarted = true;
            autoConnectToAllMatches();
        }

        // Rebuild tree only when explicitly requested (via Refresh button or initial load)
        if (needsUpdate) {
            needsUpdate = false;
            updateEntityTreeForAllMatches();
        }

        // Update tick label continuously to show latest data
        if (latestSnapshot != null) {
            String tickText = "Tick: " + latestSnapshot.tick();
            if (!tickLabel.getText().equals(tickText)) {
                tickLabel.setText(tickText);
            }
        }

        // Update entity details when selected match gets new data (without rebuilding tree)
        if (needsDetailUpdate) {
            needsDetailUpdate = false;
            if (selectedMatchId != null && selectedModuleName != null && selectedEntityIndex >= 0) {
                showEntityDetails(selectedMatchId, selectedModuleName, selectedEntityIndex, selectedEntityId);
            }
        }

        // Update connection status for WebSocket connections
        int connectedCount = getConnectedMatchCount();
        if (connectedCount > 0) {
            String statusText = "Connected to " + connectedCount + " match(es)";
            if (!statusLabel.getText().equals(statusText) && !statusLabel.getText().startsWith("Loaded")) {
                statusLabel.setText(statusText);
                statusLabel.setTextColor(colours.green());
            }
        }
    }

    /**
     * Update the entity tree to show all connected matches.
     */
    private void updateEntityTreeForAllMatches() {
        // Save expansion state before clearing
        Map<String, Boolean> expansionState = saveExpansionState();

        entityTree.clearNodes();

        if (snapshots.isEmpty() && (latestSnapshot == null || latestSnapshot.data() == null)) {
            TreeNode emptyNode = factory.createTreeNode("No snapshots - waiting for data...");
            entityTree.addRootNode(emptyNode);
            return;
        }

        TreeNode rootNode = factory.createTreeNode("Matches");
        entityTree.addRootNode(rootNode);
        rootNode.setExpanded(expansionState.getOrDefault("root", true));

        // Add all snapshots from multi-match clients
        List<Long> matchIds = new ArrayList<>(snapshots.keySet());
        matchIds.sort(Long::compareTo);

        for (Long matchId : matchIds) {
            SnapshotData snapshot = snapshots.get(matchId);
            addMatchToTree(rootNode, matchId, snapshot, expansionState);
        }

        // Also add legacy single-match snapshot if not already present
        if (latestSnapshot != null && !snapshots.containsKey(latestSnapshot.matchId())) {
            addMatchToTree(rootNode, latestSnapshot.matchId(), latestSnapshot, expansionState);
        }

        log.debug("Tree built with {} matches", rootNode.getChildren().size());
    }

    private void addMatchToTree(TreeNode rootNode, long matchId, SnapshotData snapshot,
                                 Map<String, Boolean> expansionState) {
        if (snapshot.data() == null || snapshot.data().isEmpty()) {
            return;
        }

        // Create match node
        String matchLabel = String.format("Match %d (Tick: %d)", matchId, snapshot.tick());
        TreeNode matchNode = factory.createTreeNode(matchLabel);
        matchNode.setExpanded(expansionState.getOrDefault("match:" + matchId, true));
        rootNode.addChild(matchNode);

        // Add modules
        for (String moduleName : snapshot.getModuleNames()) {
            Map<String, List<Float>> moduleData = snapshot.getModuleData(moduleName);
            if (moduleData == null || moduleData.isEmpty()) continue;

            TreeNode moduleNode = factory.createTreeNode(moduleName);
            moduleNode.setExpanded(expansionState.getOrDefault("module:" + matchId + ":" + moduleName, true));
            matchNode.addChild(moduleNode);

            // Get entity IDs from the ENTITY_ID component if available
            List<Float> entityIds = moduleData.get("ENTITY_ID");

            // Get entity count from any component
            List<String> componentNames = new ArrayList<>(moduleData.keySet());
            if (componentNames.isEmpty()) continue;
            int entityCount = moduleData.get(componentNames.getFirst()).size();

            // Add entity nodes using ENTITY_ID if available, otherwise use index
            for (int i = 0; i < entityCount; i++) {
                String entityLabel;
                if (entityIds != null && i < entityIds.size()) {
                    Float entityIdF = entityIds.get(i);
                    if (entityIdF != null && !Float.isNaN(entityIdF)) {
                        entityLabel = "Entity " + entityIdF.longValue();
                    } else {
                        entityLabel = "Entity (idx: " + i + ")";
                    }
                } else {
                    entityLabel = "Entity (idx: " + i + ")";
                }
                TreeNode entityNode = factory.createTreeNode(entityLabel);
                entityNode.setExpanded(expansionState.getOrDefault("entity:" + matchId + ":" + moduleName + ":" + i, true));
                moduleNode.addChild(entityNode);
            }
        }
    }

    private void updateEntityTree(SnapshotData snapshot) {
        // Legacy method - now delegates to multi-match version
        updateEntityTreeForAllMatches();
    }

    /**
     * Save the current expansion state of all nodes.
     */
    private Map<String, Boolean> saveExpansionState() {
        Map<String, Boolean> state = new HashMap<>();
        List<? extends TreeNode> rootNodes = entityTree.getRootNodes();

        if (rootNodes.isEmpty()) {
            return state;
        }

        // Tree structure: Matches (root) -> Match X -> Module -> Entity
        TreeNode rootNode = rootNodes.getFirst();
        state.put("root", rootNode.isExpanded());

        for (TreeNode matchNode : rootNode.getChildren()) {
            String matchLabel = matchNode.getLabel();
            try {
                long matchId = parseMatchIdFromLabel(matchLabel);
                state.put("match:" + matchId, matchNode.isExpanded());

                for (TreeNode moduleNode : matchNode.getChildren()) {
                    String moduleName = moduleNode.getLabel();
                    state.put("module:" + matchId + ":" + moduleName, moduleNode.isExpanded());
                }
            } catch (Exception e) {
                // Skip malformed nodes
            }
        }

        return state;
    }

    private String formatComponentValue(String componentName, float value) {
        if (Float.isNaN(value)) {
            return "null";
        }
        // Display integer values without decimal point if they're whole numbers
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    /**
     * Cleanup resources.
     */
    public void dispose() {
        disconnectAll();
        matchService.shutdown();
        snapshotService.shutdown();
    }

    /**
     * Get the underlying WebSocket client.
     */
    public SnapshotWebSocketClient getSnapshotClient() {
        return snapshotClient;
    }

    /**
     * Get the current snapshot data.
     */
    public SnapshotData getLatestSnapshot() {
        return latestSnapshot;
    }

    /**
     * Get the entity tree view (for testing).
     */
    public TreeView getEntityTree() {
        return entityTree;
    }

    /**
     * Set snapshot data and update the tree (for testing).
     */
    public void setSnapshotData(SnapshotData snapshot) {
        this.latestSnapshot = snapshot;
        updateEntityTree(snapshot);
        tickLabel.setText("Tick: " + snapshot.tick());
    }

    /**
     * Get the detail panel (for testing).
     */
    public Panel getDetailPanel() {
        return detailPanel;
    }

    /**
     * Get the component list (for testing).
     */
    public ListView getComponentList() {
        return componentList;
    }

    // Delegate rendering and input to both panels

    @Override
    public void render(long nvg) {
        if (visible) {
            visualPanel.render(nvg);
            detailPanel.render(nvg);
        }
    }

    @Override
    public boolean onMouseClick(int mx, int my, int button, int action) {
        if (!visible) return false;

        // Check detail panel first (it's on top visually on the right)
        if (detailPanel.contains(mx, my)) {
            return detailPanel.onMouseClick(mx, my, button, action);
        }

        return contains(mx, my) && visualPanel.onMouseClick(mx, my, button, action);
    }

    @Override
    public boolean onMouseMove(int mx, int my) {
        if (!visible) return false;
        detailPanel.onMouseMove(mx, my);
        return visualPanel.onMouseMove(mx, my);
    }

    @Override
    public boolean onMouseScroll(int mx, int my, double scrollX, double scrollY) {
        if (!visible) return false;

        if (detailPanel.contains(mx, my)) {
            return detailPanel.onMouseScroll(mx, my, scrollX, scrollY);
        }

        return contains(mx, my) && visualPanel.onMouseScroll(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean onKeyPress(int key, int action) {
        return onKeyPress(key, action, 0);
    }

    @Override
    public boolean onKeyPress(int key, int action, int mods) {
        if (!visible) return false;
        // Try detail panel first, then visual panel
        if (detailPanel.onKeyPress(key, action, mods)) {
            return true;
        }
        return visualPanel.onKeyPress(key, action, mods);
    }

    @Override
    public boolean onCharInput(int codepoint) {
        if (!visible) return false;
        // Try detail panel first, then visual panel
        if (detailPanel.onCharInput(codepoint)) {
            return true;
        }
        return visualPanel.onCharInput(codepoint);
    }
}
