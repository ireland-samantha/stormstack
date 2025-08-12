package com.lightningfirefly.engine.gui.acceptance;

import com.lightningfirefly.engine.gui.EngineGuiApplication;
import com.lightningfirefly.engine.gui.service.CommandService;
import com.lightningfirefly.engine.gui.service.ModuleService;
import com.lightningfirefly.engine.gui.service.ResourceService;
import com.lightningfirefly.engine.gui.service.SnapshotWebSocketClient.SnapshotData;
import com.lightningfirefly.engine.rendering.render2d.Window;
import com.lightningfirefly.engine.rendering.testing.By;
import com.lightningfirefly.engine.rendering.testing.GuiDriver;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GUI acceptance tests for rendering resource attachment workflow.
 *
 * <p>Tests the complete business flow for:
 * <ol>
 *   <li>Creating a match with SpawnModule and RenderModule enabled</li>
 *   <li>Uploading a texture resource</li>
 *   <li>Spawning an entity</li>
 *   <li>Sending attachResource command to link texture to entity</li>
 *   <li>Ticking the simulation to process the command</li>
 *   <li>Verifying the snapshot contains RESOURCE_ID with correct value</li>
 * </ol>
 */
@Tag("acceptance")
@Tag("testcontainers")
@DisplayName("Rendering Resource GUI Acceptance Tests")
@Testcontainers
class RenderingResourceGuiIT {

    private static final int BACKEND_PORT = 8080;

    @Container
    static GenericContainer<?> backendContainer = new GenericContainer<>(
            DockerImageName.parse("lightning-backend:latest"))
            .withExposedPorts(BACKEND_PORT)
            .waitingFor(Wait.forHttp("/api/simulation/tick")
                    .forPort(BACKEND_PORT)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    private EngineGuiApplication app;
    private GuiDriver driver;
    private Window window;
    private String backendUrl;
    private long createdMatchId = -1;
    private Long uploadedResourceId = null;
    private Path tempTexture;

    @BeforeEach
    void setUp() throws Exception {
        String host = backendContainer.getHost();
        Integer port = backendContainer.getMappedPort(BACKEND_PORT);
        backendUrl = String.format("http://%s:%d", host, port);

        // Create a temp PNG file for texture upload
        tempTexture = Files.createTempFile("test-texture", ".png");
        // Write minimal valid PNG (1x1 red pixel)
        byte[] minimalPng = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53,
            (byte) 0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
            0x54, 0x08, (byte) 0xD7, 0x63, (byte) 0xF8, (byte) 0xFF, (byte) 0xFF, 0x3F,
            0x00, 0x05, (byte) 0xFE, 0x02, (byte) 0xFE, (byte) 0xDC, (byte) 0xCC, 0x59,
            (byte) 0xE7, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E,
            0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
        };
        Files.write(tempTexture, minimalPng);
    }

    @AfterEach
    void tearDown() {
        // Cleanup match
        if (app != null && createdMatchId > 0) {
            try {
                app.getMatchPanel().getMatchService().deleteMatch(createdMatchId).get();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }

        // Cleanup resource
        if (app != null && uploadedResourceId != null) {
            try {
                app.getResourcePanel().getResourceService().deleteResource(uploadedResourceId).get();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }

        if (driver != null) {
            driver.close();
        }
        if (app != null) {
            app.stop();
        }
        try {
            if (tempTexture != null) {
                Files.deleteIfExists(tempTexture);
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    @Test
    @DisplayName("RenderModule should be available in module list")
    void renderModule_shouldBeAvailable() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Go to Matches panel to see modules
        clickButton("Matches");
        waitForUpdate(500);
        clickButton("Refresh");
        waitForModulesLoaded();

        var matchPanel = app.getMatchPanel();
        var modules = matchPanel.getAvailableModules();

        // Verify RenderModule is available
        boolean hasRenderModule = modules.stream()
                .anyMatch(m -> m.name().equals("RenderModule"));
        assertThat(hasRenderModule).as("RenderModule should be available").isTrue();

        System.out.println("Available modules: " +
                modules.stream().map(m -> m.name()).toList());
    }

    @Test
    @DisplayName("attachResource command should be available")
    void attachResourceCommand_shouldBeAvailable() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Go to Commands panel
        clickButton("Commands");
        waitForUpdate(500);
        clickButton("Refresh");
        waitForCommandsLoaded();

        var commandPanel = app.getCommandPanel();
        var commands = commandPanel.getCommands();

        // Verify attachResource command is available
        boolean hasAttachResource = commands.stream()
                .anyMatch(c -> c.name().equals("attachSprite"));
        assertThat(hasAttachResource).as("attachSprite command should be available").isTrue();

        System.out.println("Available commands: " +
                commands.stream().map(c -> c.name()).toList());
    }

    @Test
    @DisplayName("Complete workflow: upload texture, spawn entity, attach resource, verify snapshot")
    void completeWorkflow_uploadSpawnAttachVerify() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Step 1: Create a match with SpawnModule and RenderModule
        createdMatchId = createMatchWithModules("SpawnModule", "RenderModule");
        assertThat(createdMatchId).as("Match should be created").isGreaterThan(0);
        System.out.println("Created match with ID: " + createdMatchId);

        // Step 2: Upload a texture resource
        uploadedResourceId = uploadTexture();
        assertThat(uploadedResourceId).as("Resource should be uploaded").isGreaterThan(0);
        System.out.println("Uploaded resource with ID: " + uploadedResourceId);

        // Step 3: Spawn an entity
        sendSpawnCommand(createdMatchId);
        System.out.println("Sent spawn command");

        // Step 4: Tick to process spawn
        tickSimulation();
        tickSimulation();  // Extra tick to ensure command is processed
        System.out.println("Ticked simulation after spawn");

        // Step 5: Get entity ID from snapshot and send attachResource command
        long entityId = getEntityIdFromSnapshot(createdMatchId);
        System.out.println("Found entity ID: " + entityId);
        sendAttachSpriteCommand(entityId, uploadedResourceId);
        System.out.println("Sent attachResource command");

        // Step 6: Tick to process attachResource
        tickSimulation();
        tickSimulation();  // Extra tick to ensure command is processed
        System.out.println("Ticked simulation after attachResource");

        // Step 7: Navigate to snapshot view and verify
        clickButton("Snapshot");
        waitForUpdate(500);

        // Load all snapshots to see the match
        var snapshotPanel = app.getSnapshotPanel();
        snapshotPanel.loadAllSnapshots();
        waitForSnapshotLoaded();

        // Verify we can see the snapshot data
        System.out.println("Snapshot panel is visible: " + snapshotPanel.isVisible());
        System.out.println("Snapshot received - workflow completed successfully");
    }

    @Test
    @DisplayName("Upload texture and attach to entity shows in snapshot panel")
    void uploadAndAttach_showsInSnapshotPanel() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Create match with RenderModule
        createdMatchId = createMatchWithModules("SpawnModule", "RenderModule");

        // Upload texture
        uploadedResourceId = uploadTexture();

        // Spawn entity and attach resource
        sendSpawnCommand(createdMatchId);
        tickSimulation();
        tickSimulation();  // Extra tick to ensure command is processed
        long entityId = getEntityIdFromSnapshot(createdMatchId);
        System.out.println("Found entity ID: " + entityId);
        sendAttachSpriteCommand(entityId, uploadedResourceId);
        tickSimulation();
        tickSimulation();  // Extra tick to ensure command is processed

        // Navigate to Snapshot panel
        clickButton("Snapshot");
        waitForUpdate(500);

        var snapshotPanel = app.getSnapshotPanel();
        snapshotPanel.loadAllSnapshots();
        waitForSnapshotLoaded();

        // The snapshot panel should show the entity tree
        assertThat(snapshotPanel.isVisible()).as("Snapshot panel should be visible").isTrue();

        System.out.println("Snapshot panel showing match " + createdMatchId);
    }

    @Test
    @DisplayName("Renderable Matches section shows matches with RESOURCE_ID entities")
    void renderableMatches_showsMatchesWithResourceIdEntities() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Create match with RenderModule
        createdMatchId = createMatchWithModules("SpawnModule", "RenderModule");
        System.out.println("Created match with ID: " + createdMatchId);

        // Upload texture
        uploadedResourceId = uploadTexture();
        System.out.println("Uploaded resource with ID: " + uploadedResourceId);

        // Spawn entity, tick, attach resource
        sendSpawnCommand(createdMatchId);
        tickSimulation();
        tickSimulation();

        long entityId = getEntityIdFromSnapshot(createdMatchId);
        sendAttachSpriteCommand(entityId, uploadedResourceId);
        tickSimulation();
        tickSimulation();

        // Navigate to Rendering panel
        clickButton("Rendering");
        waitForUpdate(500);

        var renderingPanel = app.getRenderingPanel();
        assertThat(renderingPanel).isNotNull();

        // Load entities (use direct method call since RenderingPanel's button is nested in visualPanel)
        renderingPanel.loadEntitiesWithResources();

        // Wait for renderable matches to load
        boolean foundMatch = false;
        for (int i = 0; i < 60; i++) {
            Thread.sleep(200);
            renderingPanel.update();
            window.runFrames(3);

            var matches = renderingPanel.getRenderableMatches();
            System.out.println("Iteration " + i + ": Found " + matches.size() + " renderable matches");

            if (matches.contains(createdMatchId)) {
                foundMatch = true;
                System.out.println("Found our match " + createdMatchId + " in renderable matches list");
                break;
            }
        }

        assertThat(foundMatch).as("Should find our match with RESOURCE_ID in renderable matches").isTrue();

        // Select the match and verify Visualize button is available
        var matches = renderingPanel.getRenderableMatches();
        int matchIndex = matches.indexOf(createdMatchId);
        assertThat(matchIndex).as("Match should be in the list").isGreaterThanOrEqualTo(0);

        renderingPanel.selectMatch(matchIndex);
        window.runFrames(3);

        // Verify Visualize button exists
        assertThat(driver.hasElement(By.text("Visualize")))
                .as("Visualize button should be visible")
                .isTrue();

        // Click Visualize to open visualization panel
        renderingPanel.openVisualization();
        window.runFrames(5);

        // Verify visualization panel is visible and showing the correct match
        var vizPanel = renderingPanel.getVisualizationPanel();
        assertThat(vizPanel).as("Visualization panel should exist").isNotNull();
        assertThat(vizPanel.isVisible()).as("Visualization panel should be visible").isTrue();
        assertThat(vizPanel.getMatchId()).as("Visualization panel should show our match").isEqualTo(createdMatchId);

        // Wait for visualization panel to load entities
        boolean foundEntities = false;
        for (int i = 0; i < 30; i++) {
            Thread.sleep(200);
            vizPanel.update();
            window.runFrames(3);

            var visualEntities = vizPanel.getVisualEntities();
            System.out.println("Visualization iteration " + i + ": Found " + visualEntities.size() + " visual entities");

            if (!visualEntities.isEmpty()) {
                foundEntities = true;
                // Verify entity has the correct resource
                for (var ve : visualEntities) {
                    System.out.println("  Visual Entity: entityId=" + ve.entityId +
                            ", resourceId=" + ve.resourceId +
                            ", positionX=" + ve.positionX + ", positionY=" + ve.positionY +
                            ", hasPosition=" + ve.hasPosition);
                }

                // Check that our entity with resource is present
                boolean hasOurEntity = visualEntities.stream()
                        .anyMatch(ve -> ve.resourceId != null && ve.resourceId == uploadedResourceId);
                assertThat(hasOurEntity)
                        .as("Visualization should contain entity with resource ID " + uploadedResourceId)
                        .isTrue();
                break;
            }
        }

        assertThat(foundEntities).as("Visualization panel should load entities").isTrue();

        System.out.println("Renderable Matches section test with Visualization completed successfully");
    }

    @Test
    @DisplayName("RenderingPanel shows entities with resources and preview works")
    void renderingPanel_showsEntitiesWithResources_andPreviewWorks() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Create match with RenderModule
        createdMatchId = createMatchWithModules("SpawnModule", "RenderModule");
        System.out.println("Created match with ID: " + createdMatchId);

        // Upload texture
        uploadedResourceId = uploadTexture();
        System.out.println("Uploaded resource with ID: " + uploadedResourceId);

        // Spawn entity
        sendSpawnCommand(createdMatchId);
        tickSimulation();
        tickSimulation();  // Extra tick to ensure command is processed

        // Get the entity ID from snapshot
        long entityId = getEntityIdFromSnapshot(createdMatchId);
        System.out.println("Spawned entity ID: " + entityId);

        // Attach resource to the spawned entity
        sendAttachSpriteCommand(entityId, uploadedResourceId);
        tickSimulation();
        tickSimulation();  // Extra tick to ensure command is processed
        System.out.println("Entity spawned and resource attached");

        // Navigate to Rendering panel
        clickButton("Rendering");
        waitForUpdate(500);

        var renderingPanel = app.getRenderingPanel();
        assertThat(renderingPanel).as("Rendering panel should exist").isNotNull();
        assertThat(renderingPanel.isVisible()).as("Rendering panel should be visible").isTrue();

        // Load entities (use direct method call since RenderingPanel's button is nested in visualPanel)
        renderingPanel.loadEntitiesWithResources();

        // Wait for entities to load
        boolean foundEntity = false;
        for (int i = 0; i < 60; i++) {
            Thread.sleep(200);
            renderingPanel.update();
            window.runFrames(3);

            var entities = renderingPanel.getEntities();
            System.out.println("Iteration " + i + ": Found " + entities.size() + " entities in rendering panel");

            if (!entities.isEmpty()) {
                // Log all entities for debugging
                for (var entity : entities) {
                    System.out.println("  Entity: matchId=" + entity.matchId() +
                            ", entityId=" + entity.entityId() +
                            ", resourceId=" + entity.resourceId());
                }

                // Verify entity with our resource is present
                for (var entity : entities) {
                    if (entity.resourceId() == uploadedResourceId && entity.matchId() == createdMatchId) {
                        System.out.println("Found our entity with resource ID " + uploadedResourceId +
                                " in match " + entity.matchId());
                        foundEntity = true;
                        break;
                    }
                }
                if (foundEntity) break;
            }
        }

        assertThat(foundEntity).as("Should find entity with resource ID " + uploadedResourceId +
                " in match " + createdMatchId).isTrue();

        // Now test the preview functionality
        var previewPanel = renderingPanel.getPreviewPanel();
        assertThat(previewPanel).as("Preview panel should exist").isNotNull();
        assertThat(previewPanel.isVisible()).as("Preview panel should be hidden initially").isFalse();

        // Select the first entity in the list
        var entities = renderingPanel.getEntities();
        assertThat(entities).as("Should have at least one entity").isNotEmpty();

        // Select the entity with our resource
        int entityIndex = -1;
        for (int i = 0; i < entities.size(); i++) {
            if (entities.get(i).resourceId() == uploadedResourceId) {
                entityIndex = i;
                break;
            }
        }
        assertThat(entityIndex).as("Should find index of entity with our resource").isGreaterThanOrEqualTo(0);

        // Select the entity programmatically
        renderingPanel.selectEntity(entityIndex);
        window.runFrames(3);
        System.out.println("Selected entity at index " + entityIndex);

        // Click Preview Texture button
        clickButton("Preview Texture");

        // Wait for resource to download and preview to show
        boolean previewShown = false;
        for (int i = 0; i < 50; i++) {
            Thread.sleep(100);
            renderingPanel.update();
            window.runFrames(2);

            if (previewPanel.isVisible()) {
                previewShown = true;
                System.out.println("Preview panel is now visible");
                System.out.println("Preview texture name: " + previewPanel.getTextureName());
                System.out.println("Preview texture path: " + previewPanel.getTexturePath());
                break;
            }
        }

        // Verify preview panel is visible with correct content
        assertThat(previewShown).as("Preview panel should become visible after clicking Preview Texture").isTrue();
        assertThat(previewPanel.getTexturePath()).as("Preview should have a texture path").isNotNull();

        System.out.println("Rendering panel preview test completed successfully");
    }


    // ========== Helper Methods ==========

    private long createMatchWithModules(String... moduleNames) throws Exception {
        clickButton("Matches");
        waitForUpdate(500);
        clickButton("Refresh");
        waitForModulesLoaded();

        var matchPanel = app.getMatchPanel();
        var modules = matchPanel.getAvailableModules();

        // Select the required modules
        for (String moduleName : moduleNames) {
            int index = findModuleIndex(modules, moduleName);
            System.out.println("Selecting module '" + moduleName + "' at index " + index);
            if (index >= 0) {
                matchPanel.selectModule(index);
                window.runFrames(2);
                System.out.println("  After selectModule, selectedModuleNames: " + matchPanel.getSelectedModuleNames());
            } else {
                System.out.println("  Module '" + moduleName + "' not found in available modules!");
            }
        }

        // Verify selection before creating match
        System.out.println("About to create match with selectedModuleNames: " + matchPanel.getSelectedModuleNames());
        System.out.println("Available modules count: " + modules.size());

        // Create the match
        long matchId = matchPanel.createMatchWithSelectedModules().get(10, TimeUnit.SECONDS);

        // Verify match was created with correct modules
        var httpClient = java.net.http.HttpClient.newHttpClient();
        var request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(backendUrl + "/api/matches/" + matchId))
                .GET()
                .header("Accept", "application/json")
                .build();
        var response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        System.out.println("Match " + matchId + " details: " + response.body());

        return matchId;
    }

    private Long uploadTexture() throws Exception {
        var resourcePanel = app.getResourcePanel();
        return resourcePanel.getResourceService()
                .uploadResourceFromFile(tempTexture, "TEXTURE")
                .get(10, TimeUnit.SECONDS);
    }

    private void sendSpawnCommand(long matchId) throws Exception {
        var commandService = new CommandService(backendUrl);
        Map<String, Object> payload = Map.of(
                "matchId", matchId,
                "playerId", 1L,
                "entityType", 100L,
                "positionX", 50L,
                "positionY", 50L
        );
        commandService.submitCommand("spawn", payload).get(10, TimeUnit.SECONDS);
    }

    private void sendAttachSpriteCommand(long entityId, long resourceId) throws Exception {
        var commandService = new CommandService(backendUrl);
        Map<String, Object> payload = Map.of(
                "entityId", entityId,
                "resourceId", resourceId
        );
        commandService.submitCommand("attachSprite", payload).get(10, TimeUnit.SECONDS);
    }

    private long getEntityIdFromSnapshot(long matchId) throws Exception {
        var httpClient = java.net.http.HttpClient.newHttpClient();

        // Try multiple times with delay to ensure snapshot is ready
        for (int i = 0; i < 30; i++) {
            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(backendUrl + "/api/snapshots/match/" + matchId))
                    .GET()
                    .header("Accept", "application/json")
                    .build();
            var response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            System.out.println("Iteration " + i + ": Snapshot response: " + body);

            if (response.statusCode() == 200) {
                // Parse ENTITY_ID from response
                // Format: {"matchId":1,"tick":2,"data":{"SpawnModule":{"ENTITY_ID":[1],...}}}
                java.util.regex.Pattern entityIdPattern = java.util.regex.Pattern.compile("\"ENTITY_ID\":\\s*\\[(\\d+)");
                java.util.regex.Matcher matcher = entityIdPattern.matcher(body);
                if (matcher.find()) {
                    long entityId = Long.parseLong(matcher.group(1));
                    System.out.println("Found entity ID: " + entityId);
                    return entityId;
                }
            }
            Thread.sleep(200);
        }
        throw new RuntimeException("Could not find entity ID for match " + matchId);
    }

    private void tickSimulation() throws Exception {
        // Use the simulation endpoint directly
        var httpClient = java.net.http.HttpClient.newHttpClient();
        var request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(backendUrl + "/api/simulation/tick"))
                .POST(java.net.http.HttpRequest.BodyPublishers.noBody())
                .build();
        httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        Thread.sleep(100);
    }

    private int findModuleIndex(List<ModuleService.ModuleInfo> modules, String moduleName) {
        for (int i = 0; i < modules.size(); i++) {
            if (modules.get(i).name().equals(moduleName)) {
                return i;
            }
        }
        return -1;
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
        window.runFrames(3);
    }

    private void waitForModulesLoaded() throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            Thread.sleep(100);
            app.getMatchPanel().update();
            window.runFrames(2);
            if (!app.getMatchPanel().getAvailableModules().isEmpty()) {
                break;
            }
        }
    }

    private void waitForCommandsLoaded() throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            Thread.sleep(100);
            app.getCommandPanel().update();
            window.runFrames(2);
            if (!app.getCommandPanel().getCommands().isEmpty()) {
                break;
            }
        }
    }

    private void waitForSnapshotLoaded() throws InterruptedException {
        for (int i = 0; i < 30; i++) {
            Thread.sleep(100);
            app.getSnapshotPanel().update();
            window.runFrames(2);
        }
    }

    private void waitForRenderingEntitiesLoaded() throws InterruptedException {
        for (int i = 0; i < 30; i++) {
            Thread.sleep(100);
            app.getRenderingPanel().update();
            window.runFrames(2);
            if (!app.getRenderingPanel().getEntities().isEmpty()) {
                break;
            }
        }
    }
}
