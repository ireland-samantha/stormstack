package com.lightningfirefly.engine.gui.acceptance;

import com.lightningfirefly.engine.gui.EngineGuiApplication;
import com.lightningfirefly.engine.rendering.render2d.TreeNode;
import com.lightningfirefly.engine.rendering.render2d.TreeView;
import com.lightningfirefly.engine.rendering.render2d.Window;
import com.lightningfirefly.engine.rendering.testing.By;
import com.lightningfirefly.engine.rendering.testing.GuiDriver;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.lightningfirefly.engine.gui.service.CommandService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GUI acceptance test for multi-match isolation business use case.
 *
 * <p>This test verifies the business requirement: entities created in one match
 * should not appear in another match's snapshot, as viewed through the GUI.
 *
 * <p>Uses Testcontainers for the backend and requires OpenGL display.
 *
 * <p>Prerequisites:
 * <ul>
 *   <li>Docker must be running</li>
 *   <li>The backend image must be built: {@code docker build -t lightning-backend .}</li>
 *   <li>OpenGL display must be available</li>
 * </ul>
 *
 * <p>Run with:
 * <pre>
 * ./mvnw verify -pl lightning-engine/gui-acceptance-test -Pacceptance-tests
 * </pre>
 */
@Tag("acceptance")
@Tag("testcontainers")
@DisplayName("Multi-Match Isolation GUI Acceptance Test")
@Testcontainers
class MultiMatchIsolationGuiIT {

    private static final String SPAWN_MODULE_NAME = "SpawnModule";
    private static final String MOVE_MODULE_NAME = "MoveModule";
    private static final int BACKEND_PORT = 8080;

    @Container
    static GenericContainer<?> backendContainer = new GenericContainer<>(
            DockerImageName.parse("lightning-backend:latest"))
            .withExposedPorts(BACKEND_PORT)
            .waitingFor(Wait.forHttp("/api/simulation/tick")
                    .forPort(BACKEND_PORT)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofSeconds(30)));

    private EngineGuiApplication app;
    private GuiDriver driver;
    private Window window;
    private String backendUrl;
    private long createdMatchId = -1;
    private long createdMatch2Id = -1;

    @BeforeEach
    void setUp() {
        String host = backendContainer.getHost();
        Integer port = backendContainer.getMappedPort(BACKEND_PORT);
        backendUrl = String.format("http://%s:%d", host, port);
        System.out.println("Backend URL from testcontainers: " + backendUrl);
    }

    @AfterEach
    void tearDown() {
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
    @DisplayName("Business Use Case: Entities in separate matches are isolated in GUI TreeView")
    void entitiesInSeparateMatchesAreIsolatedInGuiTreeView() throws Exception {
        System.out.println("=== GUI ACCEPTANCE TEST: Multi-match isolation ===");

        // Initialize the GUI application
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // ===== STEP 1: Create two matches with SpawnModule and MoveModule =====
        System.out.println("=== STEP 1: Create two matches with SpawnModule and MoveModule ===");
        clickButton("Matches");
        waitForUpdate(500);
        clickButton("Refresh");
        waitForModulesLoaded();

        var matchPanel = app.getMatchPanel();
        var availableModules = matchPanel.getAvailableModules();
        int spawnModuleIndex = findModuleIndex(availableModules, SPAWN_MODULE_NAME);
        int moveModuleIndex = findModuleIndex(availableModules, MOVE_MODULE_NAME);
        assertThat(spawnModuleIndex).as("SpawnModule should exist").isGreaterThanOrEqualTo(0);
        assertThat(moveModuleIndex).as("MoveModule should exist").isGreaterThanOrEqualTo(0);

        // Create first match with both modules
        matchPanel.selectModule(spawnModuleIndex);
        matchPanel.selectModule(moveModuleIndex);
        window.runFrames(2);
        long match1Id = matchPanel.createMatchWithSelectedModules().get();
        System.out.println("Created match 1: " + match1Id);
        createdMatchId = match1Id;

        // Clear selection for second match
        matchPanel.clearSelectedModules();
        matchPanel.selectModule(spawnModuleIndex);
        matchPanel.selectModule(moveModuleIndex);

        // Create second match
        long match2Id = matchPanel.createMatchWithSelectedModules().get();
        createdMatch2Id = match2Id;
        System.out.println("Created match 2: " + match2Id);

        // ===== STEP 2: Spawn entities in each match =====
        System.out.println("=== STEP 2: Spawn entities in each match ===");
        var commandService = app.getCommandPanel().getCommandService();

        // Spawn 2 entities in match 1
        spawnEntity(commandService, match1Id);
        spawnEntity(commandService, match1Id);
        System.out.println("Spawned 2 entities in match 1");

        // Spawn 3 entities in match 2
        spawnEntity(commandService, match2Id);
        spawnEntity(commandService, match2Id);
        spawnEntity(commandService, match2Id);
        System.out.println("Spawned 3 entities in match 2");

        // ===== STEP 3: Advance tick to process spawn commands =====
        System.out.println("=== STEP 3: Advance tick to process spawn commands ===");
        clickButton("Advance");
        waitForUpdate(500);
        clickButton("Advance");
        waitForUpdate(500);

        // ===== STEP 4: Get entity IDs and attach movement =====
        System.out.println("=== STEP 4: Get entity IDs and attach movement ===");

        // Get entity IDs from snapshots via REST API
        var httpClient = java.net.http.HttpClient.newHttpClient();
        List<Long> match1EntityIds = getEntityIdsFromSnapshot(httpClient, match1Id);
        List<Long> match2EntityIds = getEntityIdsFromSnapshot(httpClient, match2Id);
        System.out.println("Match 1 entity IDs: " + match1EntityIds);
        System.out.println("Match 2 entity IDs: " + match2EntityIds);

        // Attach movement to match 1 entities
        int posX = 100;
        for (Long entityId : match1EntityIds) {
            attachMovement(commandService, entityId, posX);
            posX += 100;
        }

        // Attach movement to match 2 entities
        posX = 1000;
        for (Long entityId : match2EntityIds) {
            attachMovement(commandService, entityId, posX);
            posX += 1000;
        }
        System.out.println("Attached movement to entities");

        // ===== STEP 5: Advance tick to process attachMovement commands =====
        System.out.println("=== STEP 5: Advance tick to process attachMovement commands ===");
        clickButton("Advance");
        waitForUpdate(500);

        // ===== STEP 6: Verify snapshot isolation via GUI TreeView =====
        System.out.println("=== STEP 6: Verify snapshot isolation via TreeView ===");
        clickButton("Snapshot");
        waitForUpdate(500);
        clickButton("Load All");
        waitForUpdate(1000);

        TreeView entityTree = app.getSnapshotPanel().getEntityTree();
        List<? extends TreeNode> rootNodes = entityTree.getRootNodes();
        assertThat(rootNodes).as("TreeView should have root nodes").isNotEmpty();

        TreeNode matchesRoot = rootNodes.get(0);
        assertThat(matchesRoot.getLabel()).isEqualTo("Matches");
        System.out.println("TreeView root: " + matchesRoot.getLabel() + " with " + matchesRoot.getChildren().size() + " children");

        // Verify Match 1: should have exactly 2 entities
        TreeNode match1MoveModule = findMoveModuleNode(matchesRoot, match1Id);
        assertThat(match1MoveModule).as("Match 1 should have MoveModule").isNotNull();
        System.out.println("Match 1 MoveModule has " + match1MoveModule.getChildren().size() + " entities");
        assertThat(match1MoveModule.getChildren())
                .as("Match 1 should show exactly 2 entity nodes")
                .hasSize(2);

        for (TreeNode entityNode : match1MoveModule.getChildren()) {
            System.out.println("  Match 1 entity: " + entityNode.getLabel());
            assertThat(entityNode.getLabel()).as("Entity should have valid label").startsWith("Entity ");
        }

        // Verify Match 2: should have exactly 3 entities
        TreeNode match2MoveModule = findMoveModuleNode(matchesRoot, match2Id);
        assertThat(match2MoveModule).as("Match 2 should have MoveModule").isNotNull();
        System.out.println("Match 2 MoveModule has " + match2MoveModule.getChildren().size() + " entities");
        assertThat(match2MoveModule.getChildren())
                .as("Match 2 should show exactly 3 entity nodes")
                .hasSize(3);

        for (TreeNode entityNode : match2MoveModule.getChildren()) {
            System.out.println("  Match 2 entity: " + entityNode.getLabel());
            assertThat(entityNode.getLabel()).as("Entity should have valid label").startsWith("Entity ");
        }

        System.out.println("=== GUI ACCEPTANCE TEST PASSED: Multi-match isolation verified ===");
    }

    // ========== Helper Methods ==========

    private int findModuleIndex(List<?> modules, String moduleName) {
        for (int i = 0; i < modules.size(); i++) {
            Object module = modules.get(i);
            try {
                var nameMethod = module.getClass().getMethod("name");
                if (moduleName.equals(nameMethod.invoke(module))) {
                    return i;
                }
            } catch (Exception e) {
                // Ignore reflection errors
            }
        }
        return -1;
    }

    private int findCommandIndex(List<?> commands, String commandName) {
        for (int i = 0; i < commands.size(); i++) {
            Object command = commands.get(i);
            try {
                var nameMethod = command.getClass().getMethod("name");
                if (commandName.equals(nameMethod.invoke(command))) {
                    return i;
                }
            } catch (Exception e) {
                // Ignore reflection errors
            }
        }
        return -1;
    }

    private TreeNode findMoveModuleNode(TreeNode matchesRoot, long matchId) {
        TreeNode matchNode = matchesRoot.getChildren().stream()
                .filter(n -> n.getLabel().contains("Match " + matchId))
                .findFirst()
                .orElse(null);

        if (matchNode == null) {
            return null;
        }

        return matchNode.getChildren().stream()
                .filter(n -> n.getLabel().equals(MOVE_MODULE_NAME))
                .findFirst()
                .orElse(null);
    }

    private void spawnEntity(CommandService commandService, long matchId) throws InterruptedException {
        var params = Map.<String, Object>of(
                "matchId", matchId,
                "playerId", 1L,
                "entityType", 100L
        );
        try {
            commandService.submitCommand("spawn", params).get();
        } catch (Exception e) {
            System.out.println("WARNING: Failed to spawn entity: " + e.getMessage());
        }
        waitForUpdate(100);
    }

    private void attachMovement(CommandService commandService, long entityId, int positionX) throws InterruptedException {
        var params = Map.<String, Object>of(
                "entityId", entityId,
                "positionX", (long) positionX,
                "positionY", 0L,
                "positionZ", 0L,
                "velocityX", 0L,
                "velocityY", 0L,
                "velocityZ", 0L
        );
        try {
            commandService.submitCommand("attachMovement", params).get();
        } catch (Exception e) {
            System.out.println("WARNING: Failed to attach movement: " + e.getMessage());
        }
        waitForUpdate(100);
    }

    private List<Long> getEntityIdsFromSnapshot(HttpClient httpClient, long matchId) {
        List<Long> entityIds = new ArrayList<>();
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(backendUrl + "/api/snapshots/match/" + matchId))
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            // Parse ENTITY_ID array from response
            Pattern pattern = Pattern.compile("\"ENTITY_ID\":\\s*\\[([^\\]]+)\\]");
            Matcher matcher = pattern.matcher(body);
            if (matcher.find()) {
                String[] ids = matcher.group(1).split(",");
                for (String id : ids) {
                    entityIds.add(Long.parseLong(id.trim()));
                }
            }
        } catch (Exception e) {
            System.out.println("WARNING: Failed to get entity IDs: " + e.getMessage());
        }
        return entityIds;
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
        for (int i = 0; i < 30; i++) {
            Thread.sleep(100);
            app.getServerPanel().update();
            app.getMatchPanel().update();
            window.runFrames(2);
            if (!app.getServerPanel().getModules().isEmpty()) {
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

    private void setFormField(String paramName, String value) {
        var formPanel = app.getCommandPanel().getFormPanel();
        var field = formPanel.getFieldByName(paramName);
        if (field != null) {
            field.setText(value);
        } else {
            System.out.println("WARNING: Form field '" + paramName + "' not found");
        }
        window.runFrames(2);
    }
}
