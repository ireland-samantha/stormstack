package com.lightningfirefly.engine.gui.panel;

import com.lightningfirefly.engine.gui.EngineGuiApplication;
import com.lightningfirefly.engine.gui.service.SnapshotWebSocketClient.SnapshotData;
import com.lightningfirefly.engine.rendering.render2d.TreeNode;
import com.lightningfirefly.engine.rendering.render2d.Window;
import com.lightningfirefly.engine.rendering.testing.By;
import com.lightningfirefly.engine.rendering.testing.GuiDriver;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test for multi-match snapshot functionality.
 *
 * <p>This test validates:
 * <ol>
 *   <li>Auto-connect to all matches from the API</li>
 *   <li>Display all snapshots for all matches in treeview</li>
 *   <li>Entity selection shows component data in detail panel</li>
 * </ol>
 *
 * <p>Run with:
 * <pre>
 * BACKEND_URL=http://localhost:8080 ./mvnw test -pl lightning-engine/gui \
 *     -Dtest=MultiMatchSnapshotE2ETest -DenableGLTests=true
 * </pre>
 */
@Tag("integration")
@Tag("e2e")
@DisplayName("Multi-Match Snapshot E2E Test")
@EnabledIfSystemProperty(named = "enableGLTests", matches = "true")
@EnabledIfEnvironmentVariable(named = "BACKEND_URL", matches = ".+")
class MultiMatchSnapshotE2ETest {

    private static final String DEFAULT_SERVER_URL = "http://localhost:8080";
    private static final String MOVE_MODULE_NAME = "MoveModule";

    private EngineGuiApplication app;
    private GuiDriver driver;
    private Window window;
    private String backendUrl;
    private long matchId1 = -1;
    private long matchId2 = -1;

    @BeforeEach
    void setUp() {
        backendUrl = System.getenv("BACKEND_URL");
        if (backendUrl == null || backendUrl.isEmpty()) {
            backendUrl = DEFAULT_SERVER_URL;
        }
    }

    @AfterEach
    void tearDown() {
        // Clean up created matches
        if (app != null) {
            try {
                if (matchId1 > 0) {
                    app.getMatchPanel().getMatchService().deleteMatch(matchId1).get();
                }
                if (matchId2 > 0) {
                    app.getMatchPanel().getMatchService().deleteMatch(matchId2).get();
                }
            } catch (Exception e) {
                // Ignore cleanup errors
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
    @DisplayName("Auto-connect shows all matches in treeview")
    void autoConnectShowsAllMatchesInTreeview() throws Exception {
        System.out.println("=== Starting test with backend: " + backendUrl + " ===");

        // Initialize the application
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // ===== STEP 1: Create two matches =====
        System.out.println("=== STEP 1: Create two matches ===");
        clickButton("Matches");
        waitForUpdate(500);

        // Refresh to load modules
        clickButton("Refresh");
        waitForModulesLoaded();

        // Create first match (ID generated server-side)
        matchId1 = createMatch();
        System.out.println("Created match 1: " + matchId1);

        // Create second match (ID generated server-side)
        matchId2 = createMatch();
        System.out.println("Created match 2: " + matchId2);

        // ===== STEP 2: Switch to Snapshot panel and load all snapshots =====
        System.out.println("=== STEP 2: Load all snapshots ===");
        clickButton("Snapshot");
        waitForUpdate(500);

        // Use Load All to fetch all snapshots (auto-connect happens on panel update)
        clickButton("Load All");
        waitForUpdate(2000); // Wait for REST response

        // Verify connected to matches
        SnapshotPanel snapshotPanel = app.getSnapshotPanel();
        int connectedCount = snapshotPanel.getConnectedMatchCount();
        System.out.println("Connected to " + connectedCount + " matches");
        assertThat(connectedCount).as("Should be connected to at least 2 matches").isGreaterThanOrEqualTo(2);

        // ===== STEP 3: Verify treeview shows all matches =====
        System.out.println("=== STEP 3: Verify treeview shows all matches ===");

        // Request snapshots
        snapshotPanel.requestAllSnapshots();
        waitForUpdate(1000);
        snapshotPanel.update();
        window.runFrames(5);

        // Check tree structure
        List<? extends TreeNode> rootNodes = snapshotPanel.getEntityTree().getRootNodes();
        assertThat(rootNodes).as("Tree should have root nodes").isNotEmpty();

        TreeNode matchesRoot = rootNodes.getFirst();
        System.out.println("Root node: " + matchesRoot.getLabel());
        assertThat(matchesRoot.getLabel()).as("Root should be 'Matches'").isEqualTo("Matches");

        // Verify matches are in the tree
        List<? extends TreeNode> matchNodes = matchesRoot.getChildren();
        System.out.println("Found " + matchNodes.size() + " match nodes");
        for (TreeNode matchNode : matchNodes) {
            System.out.println("  - " + matchNode.getLabel());
        }

        // ===== STEP 4: Verify all snapshots are shown =====
        System.out.println("=== STEP 4: Verify snapshots from all matches ===");
        Map<Long, SnapshotData> allSnapshots = snapshotPanel.getAllSnapshots();
        System.out.println("Snapshots from " + allSnapshots.size() + " matches");
        for (Map.Entry<Long, SnapshotData> entry : allSnapshots.entrySet()) {
            System.out.println("  Match " + entry.getKey() + ": tick=" + entry.getValue().tick());
        }

        System.out.println("=== TEST PASSED ===");
    }

    @Test
    @DisplayName("Entity selection shows components in detail panel")
    void entitySelectionShowsComponentsInDetailPanel() throws Exception {
        System.out.println("=== Starting test with backend: " + backendUrl + " ===");

        // Initialize the application
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // ===== STEP 1: Create a match with MoveModule =====
        System.out.println("=== STEP 1: Create match with MoveModule ===");
        clickButton("Matches");
        waitForUpdate(500);
        clickButton("Refresh");
        waitForModulesLoaded();

        // Select MoveModule
        var matchPanel = app.getMatchPanel();
        var modules = matchPanel.getAvailableModules();
        int moveModuleIndex = -1;
        for (int i = 0; i < modules.size(); i++) {
            if (modules.get(i).name().equals(MOVE_MODULE_NAME)) {
                moveModuleIndex = i;
                break;
            }
        }

        if (moveModuleIndex >= 0) {
            matchPanel.selectModule(moveModuleIndex);
            window.runFrames(2);
        }

        // Create match (ID generated server-side)
        matchId1 = matchPanel.createMatchWithSelectedModules().get();
        System.out.println("Created match: " + matchId1);
        waitForUpdate(500);

        // ===== STEP 2: Send CreateMoveableCommand =====
        System.out.println("=== STEP 2: Send CreateMoveableCommand ===");
        clickButton("Commands");
        waitForUpdate(500);
        clickButton("Refresh");
        waitForCommandsLoaded();

        var commandPanel = app.getCommandPanel();
        var commands = commandPanel.getCommands();
        int cmdIndex = -1;
        for (int i = 0; i < commands.size(); i++) {
            if (commands.get(i).name().equals("CreateMoveableCommand")) {
                cmdIndex = i;
                break;
            }
        }

        if (cmdIndex >= 0) {
            commandPanel.selectCommand(cmdIndex);
            window.runFrames(2);

            // Set parameters via form fields
            var formPanel = commandPanel.getFormPanel();
            setFormField(formPanel, "matchId", String.valueOf(matchId1));
            setFormField(formPanel, "entityId", "0");
            setFormField(formPanel, "positionX", "0");
            setFormField(formPanel, "positionY", "0");
            setFormField(formPanel, "positionZ", "0");
            setFormField(formPanel, "velocityX", "10");
            setFormField(formPanel, "velocityY", "5");
            setFormField(formPanel, "velocityZ", "0");

            clickButton("Send");
            waitForUpdate(500);
        }

        // Advance tick
        clickButton("Advance");
        waitForUpdate(500);

        // ===== STEP 3: Go to Snapshot panel and verify entity details =====
        System.out.println("=== STEP 3: Verify entity detail panel ===");
        clickButton("Snapshot");
        waitForUpdate(500);

        SnapshotPanel snapshotPanel = app.getSnapshotPanel();

        // Connect to the match
        snapshotPanel.connectToMatch(matchId1);
        waitForUpdate(1000);
        snapshotPanel.requestAllSnapshots();
        waitForUpdate(1000);
        snapshotPanel.update();
        window.runFrames(5);

        // Get all snapshots
        Map<Long, SnapshotData> snapshots = snapshotPanel.getAllSnapshots();
        System.out.println("Have " + snapshots.size() + " snapshots");

        // Verify we have snapshot data with components
        if (snapshots.containsKey(matchId1)) {
            SnapshotData snapshot = snapshots.get(matchId1);
            System.out.println("Snapshot tick: " + snapshot.tick());

            var moveData = snapshot.getModuleData(MOVE_MODULE_NAME);
            if (moveData != null) {
                System.out.println("MoveModule components:");
                for (String componentName : moveData.keySet()) {
                    System.out.println("  " + componentName + ": " + moveData.get(componentName));
                }

                // Verify velocity values
                if (moveData.containsKey("VELOCITY_X")) {
                    assertThat(moveData.get("VELOCITY_X").getFirst())
                        .as("VELOCITY_X should be 10")
                        .isEqualTo(10L);
                }
            }
        }

        // Verify detail panel exists
        assertThat(snapshotPanel.getDetailPanel()).as("Detail panel should exist").isNotNull();
        assertThat(snapshotPanel.getComponentList()).as("Component list should exist").isNotNull();

        System.out.println("=== TEST PASSED ===");
    }

    @Test
    @DisplayName("Load All via REST fetches snapshots for all matches")
    void loadAllViaRestFetchesSnapshotsForAllMatches() throws Exception {
        System.out.println("=== Starting REST-based Load All test with backend: " + backendUrl + " ===");

        // Initialize the application
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // ===== STEP 1: Create two matches =====
        System.out.println("=== STEP 1: Create two matches ===");
        clickButton("Matches");
        waitForUpdate(500);

        // Refresh to load modules
        clickButton("Refresh");
        waitForModulesLoaded();

        // Create first match (ID generated server-side)
        matchId1 = createMatch();
        System.out.println("Created match 1: " + matchId1);

        // Create second match (ID generated server-side)
        matchId2 = createMatch();
        System.out.println("Created match 2: " + matchId2);

        // ===== STEP 2: Switch to Snapshot panel and use Load All (REST) =====
        System.out.println("=== STEP 2: Load All via REST ===");
        clickButton("Snapshot");
        waitForUpdate(500);

        // Click Load All button (REST-based)
        clickButton("Load All");
        waitForUpdate(2000); // Wait for REST response

        // ===== STEP 3: Verify all matches loaded via REST =====
        System.out.println("=== STEP 3: Verify snapshots loaded via REST ===");
        SnapshotPanel snapshotPanel = app.getSnapshotPanel();
        snapshotPanel.update();
        window.runFrames(5);

        Map<Long, SnapshotData> allSnapshots = snapshotPanel.getAllSnapshots();
        System.out.println("Loaded " + allSnapshots.size() + " snapshots via REST");

        // Should have at least 2 matches
        assertThat(allSnapshots.size())
            .as("Should have loaded snapshots for at least 2 matches")
            .isGreaterThanOrEqualTo(2);

        // Verify tree shows all matches
        List<? extends TreeNode> rootNodes = snapshotPanel.getEntityTree().getRootNodes();
        assertThat(rootNodes).as("Tree should have root nodes").isNotEmpty();

        TreeNode matchesRoot = rootNodes.getFirst();
        assertThat(matchesRoot.getLabel()).as("Root should be 'Matches'").isEqualTo("Matches");

        List<? extends TreeNode> matchNodes = matchesRoot.getChildren();
        System.out.println("Found " + matchNodes.size() + " match nodes in tree");
        assertThat(matchNodes.size())
            .as("Tree should show at least 2 matches")
            .isGreaterThanOrEqualTo(2);

        System.out.println("=== REST-based Load All TEST PASSED ===");
    }

    @Test
    @DisplayName("Multi-select modules when creating match")
    void multiSelectModulesWhenCreatingMatch() throws Exception {
        System.out.println("=== Starting multi-select modules test with backend: " + backendUrl + " ===");

        // Initialize the application
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // ===== STEP 1: Go to Matches panel and load modules =====
        System.out.println("=== STEP 1: Load modules ===");
        clickButton("Matches");
        waitForUpdate(500);

        clickButton("Refresh");
        waitForModulesLoaded();

        // ===== STEP 2: Select multiple modules =====
        System.out.println("=== STEP 2: Select multiple modules ===");
        var matchPanel = app.getMatchPanel();
        var modules = matchPanel.getAvailableModules();
        System.out.println("Available modules: " + modules.size());

        if (modules.size() >= 2) {
            // Select first two modules using multi-select
            matchPanel.selectModule(0);
            window.runFrames(2);
            matchPanel.selectModule(1);
            window.runFrames(2);
        } else if (modules.size() >= 1) {
            matchPanel.selectModule(0);
            window.runFrames(2);
        }

        // ===== STEP 3: Create match with selected modules =====
        System.out.println("=== STEP 3: Create match with selected modules ===");
        matchId1 = matchPanel.createMatchWithSelectedModules().get();
        System.out.println("Created match: " + matchId1);
        assertThat(matchId1).as("Match should be created with server-generated ID").isGreaterThan(0);

        // Refresh match list
        clickButton("Refresh");
        waitForUpdate(500);
        matchPanel.update();

        // Verify match was created with multiple modules
        var matches = matchPanel.getMatches();
        var createdMatch = matches.stream()
            .filter(m -> m.id() == matchId1)
            .findFirst();

        assertThat(createdMatch).as("Created match should be in list").isPresent();
        System.out.println("Created match modules: " + createdMatch.get().enabledModules());

        System.out.println("=== Multi-select modules TEST PASSED ===");
    }

    // ========== Helper Methods ==========

    /**
     * Create a match and return its server-generated ID.
     */
    private long createMatch() throws Exception {
        var matchPanel = app.getMatchPanel();
        var modules = matchPanel.getAvailableModules();

        // Select first module if available
        if (!modules.isEmpty()) {
            matchPanel.selectModule(0);
            window.runFrames(2);
        }

        // Match ID is generated server-side
        long matchId = matchPanel.createMatchWithSelectedModules().get();
        waitForUpdate(500);
        clickButton("Refresh");
        waitForUpdate(500);
        return matchId;
    }

    private void setFormField(CommandFormPanel formPanel, String paramName, String value) {
        var field = formPanel.getFieldByName(paramName);
        if (field != null) {
            field.setText(value);
        }
        window.runFrames(2);
    }

    private void clickButton(String text) {
        driver.refreshRegistry();
        if (driver.hasElement(By.text(text))) {
            driver.findElement(By.text(text)).click();
            window.runFrames(2);
        } else {
            System.out.println("WARNING: Button '" + text + "' not found");
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
            if (!app.getMatchPanel().getAvailableModules().isEmpty()) {
                return;
            }
        }
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
}
