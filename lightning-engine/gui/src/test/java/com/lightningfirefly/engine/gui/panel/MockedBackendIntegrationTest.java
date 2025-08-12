package com.lightningfirefly.engine.gui.panel;

import com.lightningfirefly.engine.gui.GuiConfig;
import com.lightningfirefly.engine.gui.service.CommandService;
import com.lightningfirefly.engine.gui.service.MatchService;
import com.lightningfirefly.engine.gui.service.ModuleService;
import com.lightningfirefly.engine.rendering.render2d.ComponentFactory;
import com.lightningfirefly.engine.rendering.render2d.Window;
import com.lightningfirefly.engine.rendering.render2d.WindowBuilder;
import com.lightningfirefly.engine.rendering.render2d.impl.opengl.GLComponentFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Integration tests using OpenGL with mocked backend services.
 *
 * <p>These tests verify GUI rendering and interactions without requiring a live backend.
 *
 * <p>Run with:
 * <pre>
 * ./mvnw test -pl lightning-engine/gui -Dtest=MockedBackendIntegrationTest -DenableGLTests=true
 * </pre>
 */
@Tag("integration")
@DisplayName("Mocked Backend GUI Integration Tests")
@EnabledIfSystemProperty(named = "enableGLTests", matches = "true")
class MockedBackendIntegrationTest {

    private Window window;
    private ComponentFactory factory;

    @BeforeEach
    void setUp() {
        factory = GLComponentFactory.getInstance();
    }

    @AfterEach
    void tearDown() {
        if (window != null) {
            try {
                window.stop();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
            window = null;
        }
    }

    @Test
    @DisplayName("ServerPanel renders with mocked modules")
    void serverPanel_rendersWithMockedModules() throws Exception {
        // Create mocked module service
        ModuleService mockModuleService = mock(ModuleService.class);
        when(mockModuleService.listModules()).thenReturn(CompletableFuture.completedFuture(List.of(
            new ModuleService.ModuleInfo("TestModule", "TestFlag", 2),
            new ModuleService.ModuleInfo("MoveModule", "move", 1),
            new ModuleService.ModuleInfo("SpawnModule", null, 0)
        )));

        // Create window
        window = WindowBuilder.create()
            .size(800, 600)
            .title("Mocked Backend Test")
            .build();

        // Create panel with mocked service
        ServerPanel panel = new ServerPanel(factory, 10, 10, 780, 580, mockModuleService);
        window.addComponent(panel);

        // Run a few frames to let UI render
        window.runFrames(5);

        // Trigger refresh
        panel.refreshModules();

        // Wait for async completion
        Thread.sleep(100);
        panel.update();
        window.runFrames(3);

        // Verify modules were loaded
        var modules = panel.getModules();
        assertThat(modules).hasSize(3);
        assertThat(modules).anyMatch(m -> m.name().equals("MoveModule"));

        System.out.println("ServerPanel test passed with mocked modules: " +
            modules.stream().map(ModuleService.ModuleInfo::name).toList());
    }

    @Test
    @DisplayName("MatchPanel renders with mocked services")
    void matchPanel_rendersWithMockedServices() throws Exception {
        // Create mocked services
        MatchService mockMatchService = mock(MatchService.class);
        ModuleService mockModuleService = mock(ModuleService.class);

        when(mockModuleService.listModules()).thenReturn(CompletableFuture.completedFuture(List.of(
            new ModuleService.ModuleInfo("MoveModule", "move", 0)
        )));

        when(mockMatchService.listMatches()).thenReturn(CompletableFuture.completedFuture(List.of(
            new MatchService.MatchInfo(1L, List.of("MoveModule"))
        )));

        // Create window
        window = WindowBuilder.create()
            .size(800, 600)
            .title("Mocked Match Panel Test")
            .build();

        // Create panel with mocked services
        MatchPanel panel = new MatchPanel(factory, 10, 10, 780, 580, mockMatchService, mockModuleService);
        window.addComponent(panel);

        // Run frames to render
        window.runFrames(5);

        // Trigger refresh
        panel.refreshMatches();
        panel.refreshModules();

        // Wait for async
        Thread.sleep(100);
        panel.update();
        window.runFrames(3);

        // Verify data loaded
        var matches = panel.getMatches();
        var modules = panel.getAvailableModules();

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).id()).isEqualTo(1L);
        assertThat(modules).hasSize(1);

        System.out.println("MatchPanel test passed");
    }

    @Test
    @DisplayName("CommandPanel renders with mocked commands")
    void commandPanel_rendersWithMockedCommands() throws Exception {
        // Create mocked service
        CommandService mockCommandService = mock(CommandService.class);

        when(mockCommandService.listCommands()).thenReturn(CompletableFuture.completedFuture(List.of(
            new CommandService.CommandInfo("attachMovement", List.of(
                new CommandService.ParameterInfo("entityId", "long"),
                new CommandService.ParameterInfo("positionX", "long"),
                new CommandService.ParameterInfo("positionY", "long"),
                new CommandService.ParameterInfo("velocityX", "long")
            )),
            new CommandService.CommandInfo("spawn", List.of())
        )));

        // Create window
        window = WindowBuilder.create()
            .size(800, 600)
            .title("Mocked Command Panel Test")
            .build();

        // Create panel with mocked service
        CommandPanel panel = new CommandPanel(factory, 10, 10, 780, 580, mockCommandService);
        window.addComponent(panel);

        // Run frames
        window.runFrames(5);

        // Trigger refresh
        panel.refreshCommands();

        // Wait for async
        Thread.sleep(100);
        panel.update();
        window.runFrames(3);

        // Verify
        var commands = panel.getCommands();
        assertThat(commands).hasSize(2);
        assertThat(commands).anyMatch(c -> c.name().equals("attachMovement"));

        System.out.println("CommandPanel test passed with commands: " +
            commands.stream().map(CommandService.CommandInfo::name).toList());
    }

    @Test
    @DisplayName("Panel switching renders correctly")
    void panelSwitching_rendersCorrectly() throws Exception {
        // Create mocked services
        ModuleService mockModuleService = mock(ModuleService.class);
        MatchService mockMatchService = mock(MatchService.class);
        CommandService mockCommandService = mock(CommandService.class);

        when(mockModuleService.listModules()).thenReturn(CompletableFuture.completedFuture(List.of(
            new ModuleService.ModuleInfo("MoveModule", "move", 0)
        )));
        when(mockMatchService.listMatches()).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(mockCommandService.listCommands()).thenReturn(CompletableFuture.completedFuture(List.of()));

        // Create window
        window = WindowBuilder.create()
            .size(1000, 700)
            .title("Panel Switching Test")
            .build();

        // Create multiple panels
        ServerPanel serverPanel = new ServerPanel(factory, 10, 100, 980, 590, mockModuleService);
        MatchPanel matchPanel = new MatchPanel(factory, 10, 100, 980, 590, mockMatchService, mockModuleService);
        CommandPanel commandPanel = new CommandPanel(factory, 10, 100, 980, 590, mockCommandService);

        // Initially only serverPanel visible
        serverPanel.setVisible(true);
        matchPanel.setVisible(false);
        commandPanel.setVisible(false);

        window.addComponent(serverPanel);
        window.addComponent(matchPanel);
        window.addComponent(commandPanel);

        // Render with serverPanel
        window.runFrames(3);
        System.out.println("Rendered serverPanel");

        // Switch to matchPanel
        serverPanel.setVisible(false);
        matchPanel.setVisible(true);
        window.runFrames(3);
        System.out.println("Rendered matchPanel");

        // Switch to commandPanel
        matchPanel.setVisible(false);
        commandPanel.setVisible(true);
        window.runFrames(3);
        System.out.println("Rendered commandPanel");

        // Switch back
        commandPanel.setVisible(false);
        serverPanel.setVisible(true);
        window.runFrames(3);
        System.out.println("Rendered serverPanel again");

        // If we get here without crashing, test passed
        System.out.println("Panel switching test passed");
    }

    @Test
    @DisplayName("Empty data renders without crash")
    void emptyData_rendersWithoutCrash() throws Exception {
        // Create mocked services that return empty data
        ModuleService mockModuleService = mock(ModuleService.class);
        MatchService mockMatchService = mock(MatchService.class);
        CommandService mockCommandService = mock(CommandService.class);

        when(mockModuleService.listModules()).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(mockMatchService.listMatches()).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(mockCommandService.listCommands()).thenReturn(CompletableFuture.completedFuture(List.of()));

        // Create window
        window = WindowBuilder.create()
            .size(800, 600)
            .title("Empty Data Test")
            .build();

        // Create panels
        ServerPanel serverPanel = new ServerPanel(factory, 10, 10, 780, 580, mockModuleService);
        MatchPanel matchPanel = new MatchPanel(factory, 10, 10, 780, 580, mockMatchService, mockModuleService);
        CommandPanel commandPanel = new CommandPanel(factory, 10, 10, 780, 580, mockCommandService);

        window.addComponent(serverPanel);
        window.addComponent(matchPanel);
        window.addComponent(commandPanel);

        // Make all visible
        serverPanel.setVisible(true);
        matchPanel.setVisible(true);
        commandPanel.setVisible(true);

        // Refresh all
        serverPanel.refreshModules();
        matchPanel.refreshMatches();
        matchPanel.refreshModules();
        commandPanel.refreshCommands();

        Thread.sleep(100);

        serverPanel.update();
        matchPanel.update();
        commandPanel.update();

        // Render many frames
        window.runFrames(10);

        // Verify empty lists
        assertThat(serverPanel.getModules()).isEmpty();
        assertThat(matchPanel.getMatches()).isEmpty();
        assertThat(commandPanel.getCommands()).isEmpty();

        System.out.println("Empty data test passed - no crashes with empty lists");
    }
}
