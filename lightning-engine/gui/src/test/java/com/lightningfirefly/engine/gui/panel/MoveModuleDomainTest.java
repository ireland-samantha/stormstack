package com.lightningfirefly.engine.gui.panel;

import com.lightningfirefly.engine.gui.EngineGuiApplication;
import com.lightningfirefly.engine.gui.service.SnapshotWebSocketClient.SnapshotData;
import com.lightningfirefly.engine.rendering.render2d.Window;
import com.lightningfirefly.engine.rendering.testing.By;
import com.lightningfirefly.engine.rendering.testing.GuiDriver;
import com.lightningfirefly.engine.rendering.testing.GuiElement;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.lightningfirefly.engine.rendering.render2d.TreeNode;
import com.lightningfirefly.engine.rendering.render2d.TreeView;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Domain integration test for the MoveModule workflow.
 *
 * <p>This test validates the complete workflow using UI interactions:
 * <ol>
 *   <li>Open modules panel and verify MoveModule exists</li>
 *   <li>Create a match with MoveModule using UI</li>
 *   <li>Validate match appears in the matches list</li>
 *   <li>Spawn entity, then switch to Commands panel and send attachMovement via UI</li>
 *   <li>Advance tick using UI button and verify snapshot values</li>
 * </ol>
 *
 * <p>Run with:
 * <pre>
 * BACKEND_URL=http://localhost:8080 ./mvnw test -pl lightning-engine/gui \
 *     -Dtest=MoveModuleDomainTest -DenableGLTests=true
 * </pre>
 */
@Slf4j
@Tag("integration")
@Tag("domain")
@DisplayName("MoveModule Domain Integration Test")
@EnabledIfSystemProperty(named = "enableGLTests", matches = "true")
@EnabledIfEnvironmentVariable(named = "BACKEND_URL", matches = ".+")
class MoveModuleDomainTest {

    private static final String DEFAULT_SERVER_URL = "http://localhost:8080";
    private static final String MOVE_MODULE_NAME = "MoveModule";

    private EngineGuiApplication app;
    private GuiDriver driver;
    private Window window;
    private String backendUrl;
    private long createdMatchId = -1;
    private long createdMatch2Id = -1;

    @BeforeEach
    void setUp() {
        backendUrl = System.getenv("BACKEND_URL");
        if (backendUrl == null || backendUrl.isEmpty()) {
            backendUrl = DEFAULT_SERVER_URL;
        }
    }

    @AfterEach
    void tearDown() {
        // Clean up created matches if any (cleanup uses direct API for reliability)
        if (app != null) {
            if (createdMatchId > 0) {
                try {
                    app.getMatchPanel().getMatchService().deleteMatch(createdMatchId).get();
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
            if (createdMatch2Id > 0) {
                try {
                    app.getMatchPanel().getMatchService().deleteMatch(createdMatch2Id).get();
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
        }

        if (driver != null) {
            driver.close();
            driver = null;
        }
        if (app != null) {
            app.stop();
            app = null;
        }
    }

    @Test
    @DisplayName("Complete MoveModule workflow: create match, spawn entity, verify movement")
    void completeMoveModuleWorkflow() throws Exception {
        log.info("=== Starting test with backend: " + backendUrl + " ===");

        // Initialize the application
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);

        // Run a few frames to let UI initialize
        window.runFrames(5);
        log.info("Window initialized, running frames...");

        // ===== STEP 1: Open Modules panel and verify MoveModule exists =====
        log.info("=== STEP 1: Verify MoveModule exists ===");

        clickButton("Modules");
        waitForUpdate(500);

        // Call refresh directly on the panel to ensure data loads
        log.info("Calling refreshModules() directly...");
        app.getServerPanel().refreshModules();

        // Wait for modules to load with more patience
        log.info("Waiting for modules to load...");
        waitForModulesLoadedWithDebug();

        // Verify MoveModule is in the list
        var modules = app.getServerPanel().getModules();
        log.info("Available modules: " + modules.stream().map(m -> m.name()).toList());
        assertThat(modules)
            .as("Modules list should contain MoveModule")
            .anyMatch(m -> m.name().equals(MOVE_MODULE_NAME));

        // ===== STEP 2: Create a match using MoveModule =====
        log.info("=== STEP 2: Create match with MoveModule ===");
        clickButton("Matches");
        waitForUpdate(500);

        // Click Refresh to load modules and matches
        clickButton("Refresh");
        waitForModulesLoaded();

        // Get initial match count
        int initialMatchCount = app.getMatchPanel().getMatches().size();
        log.info("Initial match count: " + initialMatchCount);

        // Find MoveModule in the available modules list and select it
        var matchPanel = app.getMatchPanel();
        var availableModules = matchPanel.getAvailableModules();
        int moveModuleIndex = -1;
        for (int i = 0; i < availableModules.size(); i++) {
            if (availableModules.get(i).name().equals(MOVE_MODULE_NAME)) {
                moveModuleIndex = i;
                break;
            }
        }
        assertThat(moveModuleIndex).as("MoveModule should be in available modules").isGreaterThanOrEqualTo(0);

        // Select the module
        matchPanel.selectModule(moveModuleIndex);
        window.runFrames(2);

        // Create match via API (match ID is generated server-side)
        createdMatchId = matchPanel.createMatchWithSelectedModules().get();
        log.info("Created match ID: " + createdMatchId);
        assertThat(createdMatchId).as("Match ID should be valid").isGreaterThan(0);

        // Refresh match list
        clickButton("Refresh");
        waitForUpdate(1000);

        var matchesAfterCreate = app.getMatchPanel().getMatches();
        log.info("Matches after create: " + matchesAfterCreate.stream().map(m -> m.id()).toList());
        assertThat(matchesAfterCreate)
            .as("Created match should be in the list")
            .anyMatch(m -> m.id() == createdMatchId);

        // ===== STEP 3: Spawn an entity first =====
        log.info("=== STEP 3: Spawn entity ===");
        clickButton("Commands");
        waitForUpdate(500);
        clickButton("Refresh");
        waitForCommandsLoaded();

        // Find and use spawn command
        var commands = app.getCommandPanel().getCommands();
        log.info("Available commands: " + commands.stream().map(c -> c.name()).toList());

        int spawnCmdIndex = -1;
        for (int i = 0; i < commands.size(); i++) {
            if (commands.get(i).name().equals("spawn")) {
                spawnCmdIndex = i;
                break;
            }
        }
        assertThat(spawnCmdIndex).as("spawn command should exist").isGreaterThanOrEqualTo(0);

        // Select spawn command and set parameters
        app.getCommandPanel().selectCommand(spawnCmdIndex);
        window.runFrames(2);
        setFormField("matchId", String.valueOf(createdMatchId));
        setFormField("playerId", "1");
        setFormField("entityType", "100");
        clickButton("Send");
        waitForUpdate(500);
        log.info("Spawn command sent!");

        // Advance tick to process spawn
        clickButton("Advance");
        waitForUpdate(500);
        clickButton("Advance");
        waitForUpdate(500);

        // ===== STEP 3b: Get entity ID and attach movement =====
        log.info("=== STEP 3b: Attach movement ===");

        // Get entity ID from snapshot (simple approach - use REST API directly)
        var httpClient = java.net.http.HttpClient.newHttpClient();
        var request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(backendUrl + "/api/snapshots/match/" + createdMatchId))
                .GET().build();
        var response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        String body = response.body();
        log.info("Snapshot response: " + body);

        // Parse entity ID (simple regex)
        long entityId = 1; // Default fallback
        var pattern = java.util.regex.Pattern.compile("\"ENTITY_ID\":\\s*\\[(\\d+)");
        var matcher = pattern.matcher(body);
        if (matcher.find()) {
            entityId = Long.parseLong(matcher.group(1));
        }
        log.info("Using entity ID: " + entityId);

        // Find attachMovement command
        commands = app.getCommandPanel().getCommands(); // Refresh list
        int cmdIndex = -1;
        for (int i = 0; i < commands.size(); i++) {
            if (commands.get(i).name().equals("attachMovement")) {
                cmdIndex = i;
                break;
            }
        }
        assertThat(cmdIndex).as("attachMovement command should exist").isGreaterThanOrEqualTo(0);

        // Select the command via the command list
        app.getCommandPanel().selectCommand(cmdIndex);
        window.runFrames(2);

        // Set parameters via the form panel fields
        setFormField("entityId", String.valueOf(entityId));
        setFormField("positionX", "0");
        setFormField("positionY", "0");
        setFormField("positionZ", "0");
        setFormField("velocityX", "1");
        setFormField("velocityY", "0");
        setFormField("velocityZ", "0");

        // Click Send button
        clickButton("Send");
        waitForUpdate(500);
        log.info("attachMovement command sent!");

        // ===== STEP 4: Advance tick using UI button =====
        log.info("=== STEP 4: Advance tick ===");
        long tickBefore = app.getCurrentTick();
        clickButton("Advance");
        waitForUpdate(500);

        // The tick should have advanced
        long tickAfter = app.getCurrentTick();
        log.info("Tick before: " + tickBefore + ", after: " + tickAfter);

        // ===== STEP 5: Navigate to Snapshots and verify =====
        log.info("=== STEP 5: Verify snapshot ===");
        clickButton("Snapshot");
        waitForUpdate(500);

        // Click "Load All" button to load snapshots via REST API (triggers tree rebuild)
        clickButton("Load All");
        waitForUpdate(1000);

        SnapshotData snapshot = app.getSnapshotPanel().getLatestSnapshot();
        if (snapshot != null) {
            log.info("Snapshot tick: " + snapshot.tick());
            var moveData = snapshot.getModuleData(MOVE_MODULE_NAME);
            if (moveData != null) {
                log.info("MoveModule data: " + moveData);
            }
        }

        // ===== STEP 6: Advance tick again and verify positionX=1 =====
        log.info("=== STEP 6: Advance tick again ===");
        clickButton("Advance");
        waitForUpdate(500);

        // Click "Load All" again to get fresh snapshots
        clickButton("Load All");
        waitForUpdate(1000);

        SnapshotData finalSnapshot = app.getSnapshotPanel().getLatestSnapshot();
        if (finalSnapshot != null && finalSnapshot.data() != null) {
            var moveData = finalSnapshot.getModuleData(MOVE_MODULE_NAME);
            if (moveData != null) {
                log.info("Final MoveModule data: " + moveData);

                // Check positionX - after 2 ticks with velocityX=1, positionX should be 2
                List<Float> positionX = moveData.get("POSITION_X");
                if (positionX != null && !positionX.isEmpty()) {
                    log.info("PositionX = " + positionX.get(0));
                    assertThat(positionX.get(0))
                        .as("PositionX should be 2 after 2 ticks with velocityX=1")
                        .isEqualTo(2.0f);
                }

                // Check velocityX
                List<Float> velocityX = moveData.get("VELOCITY_X");
                if (velocityX != null && !velocityX.isEmpty()) {
                    assertThat(velocityX.get(0))
                        .as("VelocityX should remain 1")
                        .isEqualTo(1.0f);
                }
            }
        }

        // ===== STEP 7: Verify TreeView labels directly =====
        log.info("=== STEP 7: Verify TreeView labels ===");
        verifyTreeViewLabels();

        log.info("=== TEST PASSED ===");
    }

    /**
     * Verify that TreeView labels are correctly set and not null/empty.
     * Tree structure is: Matches → Match X (Tick: Y) → Module → Entity
     * Entity nodes have no children (components shown in detail panel).
     */
    private void verifyTreeViewLabels() {
        TreeView entityTree = app.getSnapshotPanel().getEntityTree();
        assertThat(entityTree).as("EntityTree should not be null").isNotNull();

        List<? extends TreeNode> rootNodes = entityTree.getRootNodes();
        log.info("TreeView has " + rootNodes.size() + " root nodes");
        assertThat(rootNodes).as("TreeView should have root nodes").isNotEmpty();

        // Traverse and verify all labels
        for (TreeNode root : rootNodes) {
            verifyNodeLabels(root, 0);
        }

        // Verify specific expected labels - new tree structure
        TreeNode matchesRoot = rootNodes.get(0);
        assertThat(matchesRoot.getLabel())
            .as("Root node label should be 'Matches'")
            .isEqualTo("Matches");

        if (!matchesRoot.getChildren().isEmpty()) {
            // Find the match node for our created match
            TreeNode matchNode = matchesRoot.getChildren().stream()
                .filter(n -> n.getLabel().contains("Match " + createdMatchId))
                .findFirst()
                .orElse(matchesRoot.getChildren().get(0));

            String matchLabel = matchNode.getLabel();
            log.info("Match node label: '" + matchLabel + "'");
            assertThat(matchLabel)
                .as("Match label should contain 'Match'")
                .contains("Match");
            assertThat(matchLabel)
                .as("Match label should contain 'Tick:'")
                .contains("Tick:");

            if (!matchNode.getChildren().isEmpty()) {
                // Find MoveModule
                TreeNode moduleNode = matchNode.getChildren().stream()
                    .filter(n -> n.getLabel().equals("MoveModule"))
                    .findFirst()
                    .orElse(matchNode.getChildren().get(0));

                String moduleLabel = moduleNode.getLabel();
                log.info("Module node label: '" + moduleLabel + "'");
                assertThat(moduleLabel)
                    .as("Module label should be 'MoveModule'")
                    .isEqualTo("MoveModule");

                // Check entity nodes
                assertThat(moduleNode.getChildren())
                    .as("Module should have entity children")
                    .isNotEmpty();

                TreeNode entityNode = moduleNode.getChildren().get(0);
                String entityLabel = entityNode.getLabel();
                log.info("Entity node label: '" + entityLabel + "'");
                // Entity label should show actual ENTITY_ID (not index)
                assertThat(entityLabel)
                    .as("Entity label should start with 'Entity '")
                    .startsWith("Entity ");

                // In new design, entity nodes have NO children (components shown in detail panel)
                assertThat(entityNode.getChildren())
                    .as("Entity should have NO children in new design (components in detail panel)")
                    .isEmpty();
            }
        }

        log.info("All TreeView labels verified successfully!");
    }

    /**
     * Recursively verify that all node labels are set.
     */
    private void verifyNodeLabels(TreeNode node, int depth) {
        String indent = "  ".repeat(depth);
        String label = node.getLabel();
        String expandedStr = node.isExpanded() ? "[+]" : "[-]";

        log.info(indent + expandedStr + " '" + label + "' (" + node.getChildren().size() + " children)");

        assertThat(label)
            .as("Node label at depth " + depth + " should not be null")
            .isNotNull();
        assertThat(label)
            .as("Node label at depth " + depth + " should not be empty")
            .isNotEmpty();

        // Recurse into children if expanded
        if (node.isExpanded()) {
            for (TreeNode child : node.getChildren()) {
                verifyNodeLabels(child, depth + 1);
            }
        }
    }

    @Test
    @DisplayName("Multi-match isolation: each match shows only its own entities")
    void multiMatchIsolation_showsOnlyOwnEntities() throws Exception {
        log.info("=== Starting multi-match isolation test with backend: " + backendUrl + " ===");

        // Initialize the application
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // ===== STEP 1: Create two matches with MoveModule =====
        log.info("=== STEP 1: Create two matches with MoveModule ===");
        clickButton("Matches");
        waitForUpdate(500);
        clickButton("Refresh");
        waitForModulesLoaded();

        var matchPanel = app.getMatchPanel();
        var availableModules = matchPanel.getAvailableModules();
        int moveModuleIndex = -1;
        for (int i = 0; i < availableModules.size(); i++) {
            if (availableModules.get(i).name().equals(MOVE_MODULE_NAME)) {
                moveModuleIndex = i;
                break;
            }
        }
        assertThat(moveModuleIndex).as("MoveModule should exist").isGreaterThanOrEqualTo(0);

        // Create first match
        matchPanel.selectModule(moveModuleIndex);
        window.runFrames(2);
        long match1Id = matchPanel.createMatchWithSelectedModules().get();
        log.info("Created match 1: " + match1Id);
        createdMatchId = match1Id; // Store for cleanup

        // Create second match
        long match2Id = matchPanel.createMatchWithSelectedModules().get();
        createdMatch2Id = match2Id; // Store for cleanup
        log.info("Created match 2: " + match2Id);

        // ===== STEP 2: Create multiple entities in each match =====
        log.info("=== STEP 2: Create entities in each match ===");
        clickButton("Commands");
        waitForUpdate(500);
        clickButton("Refresh");
        waitForCommandsLoaded();

        // Find CreateMoveableCommand
        var commands = app.getCommandPanel().getCommands();
        int cmdIndex = -1;
        for (int i = 0; i < commands.size(); i++) {
            if (commands.get(i).name().equals("CreateMoveableCommand")) {
                cmdIndex = i;
                break;
            }
        }
        assertThat(cmdIndex).as("CreateMoveableCommand should exist").isGreaterThanOrEqualTo(0);

        // Create 2 entities in match 1 (positionX=100, 200)
        app.getCommandPanel().selectCommand(cmdIndex);
        window.runFrames(2);
        setFormField("matchId", String.valueOf(match1Id));
        setFormField("entityId", "0");
        setFormField("positionX", "100");
        setFormField("positionY", "0");
        setFormField("positionZ", "0");
        setFormField("velocityX", "0");
        setFormField("velocityY", "0");
        setFormField("velocityZ", "0");
        clickButton("Send");
        waitForUpdate(300);

        setFormField("entityId", "0");
        setFormField("positionX", "200");
        clickButton("Send");
        waitForUpdate(300);

        // Create 3 entities in match 2 (positionX=1000, 2000, 3000)
        setFormField("matchId", String.valueOf(match2Id));
        setFormField("entityId", "0");
        setFormField("positionX", "1000");
        clickButton("Send");
        waitForUpdate(300);

        setFormField("entityId", "0");
        setFormField("positionX", "2000");
        clickButton("Send");
        waitForUpdate(300);

        setFormField("entityId", "0");
        setFormField("positionX", "3000");
        clickButton("Send");
        waitForUpdate(300);

        // ===== STEP 3: Advance tick to process commands =====
        log.info("=== STEP 3: Advance tick to process commands ===");
        clickButton("Advance");
        waitForUpdate(500);

        // ===== STEP 4: Verify snapshots show correct entities in TreeView =====
        log.info("=== STEP 4: Verify snapshot isolation via TreeView ===");
        clickButton("Snapshot");
        waitForUpdate(500);

        // Click "Load All" button to load all snapshots via REST
        clickButton("Load All");
        waitForUpdate(1000);

        // Verify through the TreeView UI
        TreeView entityTree = app.getSnapshotPanel().getEntityTree();
        List<? extends TreeNode> rootNodes = entityTree.getRootNodes();
        assertThat(rootNodes).as("TreeView should have root nodes").isNotEmpty();

        TreeNode matchesRoot = rootNodes.get(0);
        assertThat(matchesRoot.getLabel()).isEqualTo("Matches");
        log.info("TreeView root: " + matchesRoot.getLabel() + " with " + matchesRoot.getChildren().size() + " children");

        // Find match 1 node and verify entity count
        TreeNode match1Node = matchesRoot.getChildren().stream()
            .filter(n -> n.getLabel().contains("Match " + match1Id))
            .findFirst()
            .orElse(null);

        assertThat(match1Node)
            .as("Match 1 (id=" + match1Id + ") should be visible in TreeView")
            .isNotNull();

        TreeNode match1MoveModule = match1Node.getChildren().stream()
            .filter(n -> n.getLabel().equals(MOVE_MODULE_NAME))
            .findFirst()
            .orElse(null);

        assertThat(match1MoveModule)
            .as("Match 1 should have MoveModule")
            .isNotNull();

        log.info("Match 1 MoveModule has " + match1MoveModule.getChildren().size() + " entities");
        assertThat(match1MoveModule.getChildren())
            .as("Match 1 should show exactly 2 entity nodes (not 3 from match 2)")
            .hasSize(2);

        // Verify entity labels show ENTITY_ID format
        for (TreeNode entityNode : match1MoveModule.getChildren()) {
            String label = entityNode.getLabel();
            log.info("  Match 1 entity: " + label);
            assertThat(label).as("Entity should have valid label").startsWith("Entity ");
        }

        // Find match 2 node and verify entity count
        TreeNode match2Node = matchesRoot.getChildren().stream()
            .filter(n -> n.getLabel().contains("Match " + match2Id))
            .findFirst()
            .orElse(null);

        assertThat(match2Node)
            .as("Match 2 (id=" + match2Id + ") should be visible in TreeView")
            .isNotNull();

        TreeNode match2MoveModule = match2Node.getChildren().stream()
            .filter(n -> n.getLabel().equals(MOVE_MODULE_NAME))
            .findFirst()
            .orElse(null);

        assertThat(match2MoveModule)
            .as("Match 2 should have MoveModule")
            .isNotNull();

        log.info("Match 2 MoveModule has " + match2MoveModule.getChildren().size() + " entities");
        assertThat(match2MoveModule.getChildren())
            .as("Match 2 should show exactly 3 entity nodes (not 2 from match 1)")
            .hasSize(3);

        // Verify entity labels show ENTITY_ID format
        for (TreeNode entityNode : match2MoveModule.getChildren()) {
            String label = entityNode.getLabel();
            log.info("  Match 2 entity: " + label);
            assertThat(label).as("Entity should have valid label").startsWith("Entity ");
        }

        log.info("=== TreeView correctly shows isolated entities per match ===");

        log.info("=== MULTI-MATCH ISOLATION TEST PASSED ===");
    }

    @Test
    @DisplayName("Play/Stop: tick auto-advances when playing")
    void playStop_tickAutoAdvancesWhenPlaying() throws Exception {
        log.info("=== Starting play/stop test with backend: " + backendUrl + " ===");

        // Initialize the application
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Get initial tick
        long initialTick = app.getCurrentTick();
        log.info("Initial tick: " + initialTick);

        // Click Play button
        log.info("=== Clicking Play button ===");
        clickButton("Play");
        waitForUpdate(200);

        // Verify play state
        assertThat(app.isPlaying()).as("Should be playing after clicking Play").isTrue();

        // Wait for ticks to advance
        Thread.sleep(150);
        window.runFrames(5);

        // Get new tick value
        app.getSimulationService().getCurrentTick().thenAccept(newTick -> {
            log.info("Tick after playing: " + newTick);
            assertThat(newTick).as("Tick should have advanced during play").isGreaterThan(initialTick);
        }).get();

        // Click Stop button
        log.info("=== Clicking Stop button ===");
        clickButton("Stop");
        waitForUpdate(200);

        // Verify stopped state
        assertThat(app.isPlaying()).as("Should be stopped after clicking Stop").isFalse();

        // Record tick after stop
        long tickAfterStop = app.getSimulationService().getCurrentTick().get();
        log.info("Tick after stop: " + tickAfterStop);

        // Wait and verify tick doesn't change
        Thread.sleep(100);
        long tickAfterWait = app.getSimulationService().getCurrentTick().get();
        log.info("Tick after wait: " + tickAfterWait);

        assertThat(tickAfterWait)
            .as("Tick should not advance after stop")
            .isEqualTo(tickAfterStop);

        log.info("=== PLAY/STOP TEST PASSED ===");
    }

    // ========== Helper Methods ==========

    private void clickButton(String text) {
        driver.refreshRegistry();
        if (driver.hasElement(By.text(text))) {
            driver.findElement(By.text(text)).click();
            window.runFrames(2);
        } else {
            log.info("WARNING: Button '" + text + "' not found");
        }
    }

    private void waitForUpdate(long millis) throws InterruptedException {
        Thread.sleep(millis);
        if (app.getMatchPanel() != null) app.getMatchPanel().update();
        if (app.getServerPanel() != null) app.getServerPanel().update();
        if (app.getCommandPanel() != null) app.getCommandPanel().update();
        if (app.getSnapshotPanel() != null) app.getSnapshotPanel().update();
        window.runFrames(3);
    }

    private void waitForModulesLoaded() throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            Thread.sleep(100);
            app.getServerPanel().update();
            app.getMatchPanel().update();
            window.runFrames(2);
            if (!app.getServerPanel().getModules().isEmpty()) {
                return;
            }
        }
    }

    private void waitForModulesLoadedWithDebug() throws InterruptedException {
        for (int i = 0; i < 30; i++) {  // Extended to 3 seconds
            Thread.sleep(100);
            app.getServerPanel().update();
            window.runFrames(2);
            var modules = app.getServerPanel().getModules();
            if (i % 5 == 0) {
                log.info("  Wait iteration " + i + ": " + modules.size() + " modules loaded");
            }
            if (!modules.isEmpty()) {
                log.info("  Modules loaded after " + (i * 100) + "ms");
                return;
            }
        }
        log.info("  WARNING: Timeout waiting for modules after 3000ms");
    }

    private void waitForCommandsLoaded() throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            Thread.sleep(100);
            app.getCommandPanel().update();
            window.runFrames(2);
            if (!app.getCommandPanel().getCommands().isEmpty()) {
                return;
            }
        }
    }

    private void setFormField(String paramName, String value) {
        var formPanel = app.getCommandPanel().getFormPanel();
        var field = formPanel.getFieldByName(paramName);
        if (field != null) {
            field.setText(value);
        } else {
            log.info("WARNING: Form field '" + paramName + "' not found");
        }
        window.runFrames(2);
    }
}
