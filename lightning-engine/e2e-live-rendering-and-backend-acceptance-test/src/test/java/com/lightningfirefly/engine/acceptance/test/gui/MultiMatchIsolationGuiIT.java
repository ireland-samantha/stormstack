package com.lightningfirefly.engine.acceptance.test.gui;
import com.lightningfirefly.engine.gui.EngineGuiApplication;
import com.lightningfirefly.engine.gui.service.CommandService;
import com.lightningfirefly.engine.rendering.render2d.TreeNode;
import com.lightningfirefly.engine.rendering.render2d.TreeView;
import com.lightningfirefly.engine.rendering.render2d.Window;
import com.lightningfirefly.engine.rendering.testing.By;
import com.lightningfirefly.engine.rendering.testing.GuiDriver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

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
 * <p>Verifies: entities created in one match should not appear in another match's
 * snapshot, as viewed through the GUI TreeView.
 */
@Slf4j
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
        log.info("Backend URL from testcontainers: {}", backendUrl);
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
    @DisplayName("Given two matches with entities, when viewing TreeView, then entities are isolated per match")
    void givenTwoMatchesWithEntities_whenViewingTreeView_thenEntitiesAreIsolatedPerMatch() throws Exception {
        log.info("=== GUI ACCEPTANCE TEST: Multi-match isolation ===");

        // Given: GUI initialized
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // And: Two matches created with SpawnModule and MoveModule
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

        matchPanel.selectModule(spawnModuleIndex);
        matchPanel.selectModule(moveModuleIndex);
        window.runFrames(2);

        long match1Id = matchPanel.createMatchWithSelectedModules().get();
        createdMatchId = match1Id;
        log.info("Created match 1: {}", match1Id);

        matchPanel.clearSelectedModules();
        matchPanel.selectModule(spawnModuleIndex);
        matchPanel.selectModule(moveModuleIndex);

        long match2Id = matchPanel.createMatchWithSelectedModules().get();
        createdMatch2Id = match2Id;
        log.info("Created match 2: {}", match2Id);

        // And: Entities spawned in each match
        var commandService = app.getCommandPanel().getCommandService();

        spawnEntity(commandService, match1Id);
        spawnEntity(commandService, match1Id);
        log.info("Spawned 2 entities in match 1");

        spawnEntity(commandService, match2Id);
        spawnEntity(commandService, match2Id);
        spawnEntity(commandService, match2Id);
        log.info("Spawned 3 entities in match 2");

        // When: Ticking to process spawn commands
        clickButton("Advance");
        waitForUpdate(500);
        clickButton("Advance");
        waitForUpdate(500);

        // And: Attaching movement to entities
        var httpClient = HttpClient.newHttpClient();
        List<Long> match1EntityIds = getEntityIdsFromSnapshot(httpClient, match1Id);
        List<Long> match2EntityIds = getEntityIdsFromSnapshot(httpClient, match2Id);
        log.info("Match 1 entity IDs: {}", match1EntityIds);
        log.info("Match 2 entity IDs: {}", match2EntityIds);

        int posX = 100;
        for (Long entityId : match1EntityIds) {
            attachMovement(commandService, entityId, posX);
            posX += 100;
        }

        posX = 1000;
        for (Long entityId : match2EntityIds) {
            attachMovement(commandService, entityId, posX);
            posX += 1000;
        }

        clickButton("Advance");
        waitForUpdate(500);

        // And: Loading snapshot view
        clickButton("Snapshot");
        waitForUpdate(500);
        clickButton("Load All");
        waitForUpdate(1000);

        // Then: TreeView should show isolated entities per match
        TreeView entityTree = app.getSnapshotPanel().getEntityTree();
        List<? extends TreeNode> rootNodes = entityTree.getRootNodes();
        assertThat(rootNodes).as("TreeView should have root nodes").isNotEmpty();

        TreeNode matchesRoot = rootNodes.get(0);
        assertThat(matchesRoot.getLabel()).isEqualTo("Matches");
        log.info("TreeView root: {} with {} children", matchesRoot.getLabel(), matchesRoot.getChildren().size());

        // Verify Match 1 has exactly 2 entities
        TreeNode match1MoveModule = findMoveModuleNode(matchesRoot, match1Id);
        assertThat(match1MoveModule).as("Match 1 should have MoveModule").isNotNull();
        log.info("Match 1 MoveModule has {} entities", match1MoveModule.getChildren().size());
        assertThat(match1MoveModule.getChildren())
                .as("Match 1 should show exactly 2 entity nodes")
                .hasSize(2);

        for (TreeNode entityNode : match1MoveModule.getChildren()) {
            log.info("  Match 1 entity: {}", entityNode.getLabel());
            assertThat(entityNode.getLabel()).as("Entity should have valid label").startsWith("Entity ");
        }

        // Verify Match 2 has exactly 3 entities
        TreeNode match2MoveModule = findMoveModuleNode(matchesRoot, match2Id);
        assertThat(match2MoveModule).as("Match 2 should have MoveModule").isNotNull();
        log.info("Match 2 MoveModule has {} entities", match2MoveModule.getChildren().size());
        assertThat(match2MoveModule.getChildren())
                .as("Match 2 should show exactly 3 entity nodes")
                .hasSize(3);

        for (TreeNode entityNode : match2MoveModule.getChildren()) {
            log.info("  Match 2 entity: {}", entityNode.getLabel());
            assertThat(entityNode.getLabel()).as("Entity should have valid label").startsWith("Entity ");
        }

        log.info("=== GUI ACCEPTANCE TEST PASSED: Multi-match isolation verified ===");
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
                // Ignore
            }
        }
        return -1;
    }

    private TreeNode findMoveModuleNode(TreeNode matchesRoot, long matchId) {
        TreeNode matchNode = matchesRoot.getChildren().stream()
                .filter(n -> n.getLabel().contains("Match " + matchId))
                .findFirst()
                .orElse(null);

        if (matchNode == null) return null;

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
            log.warn("Failed to spawn entity: {}", e.getMessage());
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
            log.warn("Failed to attach movement: {}", e.getMessage());
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

            Pattern pattern = Pattern.compile("\"ENTITY_ID\":\\s*\\[([^\\]]+)\\]");
            Matcher matcher = pattern.matcher(body);
            if (matcher.find()) {
                String[] ids = matcher.group(1).split(",");
                for (String id : ids) {
                    entityIds.add(Long.parseLong(id.trim()));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get entity IDs: {}", e.getMessage());
        }
        return entityIds;
    }

    private void clickButton(String text) {
        driver.refreshRegistry();
        if (driver.hasElement(By.text(text))) {
            driver.findElement(By.text(text)).click();
            window.runFrames(2);
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
}
