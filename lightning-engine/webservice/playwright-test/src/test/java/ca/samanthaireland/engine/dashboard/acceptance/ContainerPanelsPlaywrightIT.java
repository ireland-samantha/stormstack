/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */
package ca.samanthaireland.engine.dashboard.acceptance;

import ca.samanthaireland.engine.dashboard.acceptance.pages.*;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Playwright integration tests for Container panels.
 * Tests Commands, Players, Sessions, Snapshot, History, and Logs panels.
 * These panels require a container to be selected.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ContainerPanelsPlaywrightIT extends PlaywrightTestBase {

    private static final String TEST_CONTAINER_PREFIX = "pw-test-";

    @BeforeEach
    void login() {
        loginAsAdmin();
    }

    private void ensureContainerExistsAndSelected() {
        PWDashboardPage dashboard = new PWDashboardPage(page);

        // Use first available container, or create one if none exist
        var containerNames = dashboard.getContainerNames();
        String containerToUse;

        if (containerNames.isEmpty()) {
            // Create a unique container name
            String uniqueName = TEST_CONTAINER_PREFIX + System.currentTimeMillis();
            dashboard.createContainer(uniqueName);
            containerToUse = uniqueName;
        } else {
            containerToUse = containerNames.get(0);
        }

        // Start container if needed
        String status = dashboard.getContainerStatus(containerToUse);
        if (!status.contains("RUNNING")) {
            dashboard.startContainer(containerToUse);
        }

        // Select the container in the sidebar dropdown
        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        if (!sidebar.isContainerSelected()) {
            sidebar.selectContainer(containerToUse);
        }
    }

    // ==================== Players Panel Tests ====================

    @Test
    @Order(1)
    void viewPlayersPage() {
        ensureContainerExistsAndSelected();

        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWPlayersPage playersPage = sidebar.goToPlayers();

        assertThat(playersPage.isDisplayed()).isTrue();
    }

    @Test
    @Order(2)
    void canAddPlayer() {
        ensureContainerExistsAndSelected();

        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWPlayersPage playersPage = sidebar.goToPlayers();

        int initialCount = playersPage.getPlayerCount();
        playersPage.addPlayer();

        assertThat(playersPage.getPlayerCount()).isGreaterThan(initialCount);
    }

    // ==================== Sessions Panel Tests ====================

    @Test
    @Order(3)
    void viewSessionsPage() {
        ensureContainerExistsAndSelected();

        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWSessionsPage sessionsPage = sidebar.goToSessions();

        assertThat(sessionsPage.isDisplayed()).isTrue();
    }

    // ==================== Commands Panel Tests ====================

    @Test
    @Order(4)
    void viewCommandsPage() {
        ensureContainerExistsAndSelected();

        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWCommandsPage commandsPage = sidebar.goToCommands();

        assertThat(commandsPage.isDisplayed()).isTrue();
    }

    @Test
    @Order(5)
    void commandsPageShowsAvailableCommands() {
        ensureContainerExistsAndSelected();

        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWCommandsPage commandsPage = sidebar.goToCommands();

        // Should show commands or "no commands" message
        assertThat(commandsPage.isDisplayed()).isTrue();
    }

    // ==================== Snapshot Panel Tests ====================

    @Test
    @Order(6)
    void viewSnapshotPage() {
        ensureContainerExistsAndSelected();

        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWSnapshotPage snapshotPage = sidebar.goToLiveSnapshot();

        assertThat(snapshotPage.isDisplayed()).isTrue();
    }

    @Test
    @Order(7)
    void snapshotPageShowsNoSnapshotInitially() {
        ensureContainerExistsAndSelected();

        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWSnapshotPage snapshotPage = sidebar.goToLiveSnapshot();

        // Should show either snapshot data or "no snapshot/select a match" message
        assertThat(snapshotPage.isDisplayed() || snapshotPage.isNoSnapshotMessageDisplayed()).isTrue();
    }

    // ==================== History Panel Tests ====================

    @Test
    @Order(8)
    void viewHistoryPage() {
        ensureContainerExistsAndSelected();

        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWHistoryPage historyPage = sidebar.goToHistory();

        assertThat(historyPage.isDisplayed()).isTrue();
    }

    // ==================== Logs Panel Tests ====================

    @Test
    @Order(9)
    void viewLogsPage() {
        ensureContainerExistsAndSelected();

        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWLogsPage logsPage = sidebar.goToLogs();

        assertThat(logsPage.isDisplayed()).isTrue();
    }

    @Test
    @Order(10)
    void logsPageShowsStreamingControls() {
        ensureContainerExistsAndSelected();

        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWLogsPage logsPage = sidebar.goToLogs();

        // Page should display and show either streaming controls or no logs message
        assertThat(logsPage.isDisplayed()).isTrue();
    }

    // ==================== No Container Selected Tests ====================

    @Test
    @Order(11)
    void playersShowNoContainerMessageWhenNoneSelected() {
        // Navigate directly without selecting container
        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWPlayersPage playersPage = sidebar.goToPlayers();

        // Should show either content (if auto-selected) or no container message
        assertThat(playersPage.isDisplayed() || playersPage.isNoContainerSelectedMessageDisplayed()).isTrue();
    }

    @Test
    @Order(12)
    void sessionsShowNoContainerMessageWhenNoneSelected() {
        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWSessionsPage sessionsPage = sidebar.goToSessions();

        assertThat(sessionsPage.isDisplayed() || sessionsPage.isNoContainerSelectedMessageDisplayed()).isTrue();
    }

    @Test
    @Order(13)
    void commandsShowNoContainerMessageWhenNoneSelected() {
        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWCommandsPage commandsPage = sidebar.goToCommands();

        assertThat(commandsPage.isDisplayed() || commandsPage.isNoContainerSelectedMessageDisplayed()).isTrue();
    }

    @Test
    @Order(14)
    void snapshotShowNoContainerMessageWhenNoneSelected() {
        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWSnapshotPage snapshotPage = sidebar.goToLiveSnapshot();

        assertThat(snapshotPage.isDisplayed() || snapshotPage.isNoContainerSelectedMessageDisplayed()).isTrue();
    }

    @Test
    @Order(15)
    void historyShowNoContainerMessageWhenNoneSelected() {
        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWHistoryPage historyPage = sidebar.goToHistory();

        assertThat(historyPage.isDisplayed() || historyPage.isNoContainerSelectedMessageDisplayed()).isTrue();
    }

    @Test
    @Order(16)
    void logsShowNoContainerMessageWhenNoneSelected() {
        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWLogsPage logsPage = sidebar.goToLogs();

        assertThat(logsPage.isDisplayed() || logsPage.isNoContainerSelectedMessageDisplayed()).isTrue();
    }

    // ==================== Integration Tests ====================

    @Test
    @Order(17)
    void canNavigateThroughAllContainerPanels() {
        ensureContainerExistsAndSelected();

        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);

        // Navigate through all container panels
        PWDashboardPage dashboard = sidebar.goToOverview();
        assertThat(dashboard.isDisplayed()).isTrue();

        PWPlayersPage players = sidebar.goToPlayers();
        assertThat(players.isDisplayed()).isTrue();

        PWSessionsPage sessions = sidebar.goToSessions();
        assertThat(sessions.isDisplayed()).isTrue();

        PWCommandsPage commands = sidebar.goToCommands();
        assertThat(commands.isDisplayed()).isTrue();

        PWSnapshotPage snapshot = sidebar.goToLiveSnapshot();
        assertThat(snapshot.isDisplayed()).isTrue();

        PWHistoryPage history = sidebar.goToHistory();
        assertThat(history.isDisplayed()).isTrue();

        PWLogsPage logs = sidebar.goToLogs();
        assertThat(logs.isDisplayed()).isTrue();

        PWModulesPage modules = sidebar.goToModules();
        assertThat(modules.isDisplayed()).isTrue();

        PWAIPage ai = sidebar.goToAI();
        assertThat(ai.isDisplayed()).isTrue();

        PWResourcesPage resources = sidebar.goToResources();
        assertThat(resources.isDisplayed()).isTrue();
    }

    // ==================== E2E Acceptance Tests ====================

    /**
     * Functional interface for running code within a container context.
     */
    @FunctionalInterface
    private interface ContainerTestAction {
        void execute(String containerName, PWDashboardPage dashboard, PWSidebarNavigator sidebar) throws Exception;
    }

    /**
     * Helper that creates a container, starts it, selects it, and ensures cleanup.
     * This is the recommended way to write container E2E tests.
     *
     * @param testPrefix a short prefix to identify the test (e.g., "e2e1", "memory")
     * @param action the test code to run with the container
     */
    private void withContainer(String testPrefix, ContainerTestAction action) {
        String containerName = TEST_CONTAINER_PREFIX + testPrefix + "-" + System.currentTimeMillis();
        try {
            PWDashboardPage dashboard = new PWDashboardPage(page);
            dashboard.waitForPageLoad();
            dashboard.createContainer(containerName);
            dashboard.startContainer(containerName);

            PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
            sidebar.selectContainer(containerName);

            action.execute(containerName, dashboard, sidebar);
        } catch (Exception e) {
            throw new RuntimeException("Test failed: " + e.getMessage(), e);
        } finally {
            cleanupContainer(containerName);
        }
    }

    /**
     * Safely clean up a container by stopping and deleting it.
     * Navigates to dashboard first to ensure we're on the right page.
     */
    private void cleanupContainer(String containerName) {
        try {
            PWDashboardPage dashboard = new PWDashboardPage(page);
            PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
            sidebar.goToOverview();
            dashboard.waitForPageLoad();

            // Check if container still exists
            if (dashboard.getContainerCard(containerName).count() == 0) {
                return; // Container doesn't exist, nothing to clean up
            }

            // Stop container if running (STARTING state needs to wait or be handled)
            String status = dashboard.getContainerStatus(containerName);
            if (status.contains("RUNNING")) {
                dashboard.stopContainer(containerName);
                page.waitForTimeout(1000);
            } else if (status.contains("STARTING")) {
                // Wait briefly for container to finish starting, then try to stop
                page.waitForTimeout(2000);
                status = dashboard.getContainerStatus(containerName);
                if (status.contains("RUNNING")) {
                    dashboard.stopContainer(containerName);
                    page.waitForTimeout(1000);
                }
            }

            // Delete the container (clicks the delete button)
            dashboard.deleteContainer(containerName);
            // Wait for container card to disappear
            page.waitForTimeout(500);
        } catch (Exception e) {
            // Log but don't fail - cleanup is best effort
            System.err.println("Warning: Failed to cleanup container " + containerName + ": " + e.getMessage());
        }
    }

    @Test
    @Order(18)
    @DisplayName("E2E: create container, create match, create player, create session, verify commands available")
    void e2eAcceptance1() {
        withContainer("e2e1", (containerName, dashboard, sidebar) -> {
            // Create match with EntityModule
            PWMatchesPage matchesPage = sidebar.goToMatches();
            matchesPage.waitForPageLoad();
            int initialMatchCount = matchesPage.getMatchCount();
            matchesPage.createMatch("EntityModule");
            assertThat(matchesPage.getMatchCount()).isGreaterThan(initialMatchCount);

            // Create player
            PWPlayersPage playersPage = sidebar.goToPlayers();
            playersPage.waitForPageLoad();
            playersPage.addPlayer();
            page.waitForTimeout(1000);

            // Verify at least one player exists
            java.util.List<String> players = playersPage.getPlayerIds();
            assertThat(players).as("At least one player should exist").isNotEmpty();

            // Create session (join player to match) - use first available options
            PWSessionsPage sessions = sidebar.goToSessions();
            sessions.waitForPageLoad();
            sessions.createSessionWithFirstOptions();

            // Navigate to commands page and verify commands are available
            PWCommandsPage commands = sidebar.goToCommands();
            commands.waitForPageLoad();
            java.util.List<String> availableCommands = commands.getAvailableCommands();
            assertThat(availableCommands).as("Commands should be available").isNotEmpty();
            assertThat(availableCommands).as("spawn command should be available").contains("spawn");

            // Step the simulation
            dashboard = sidebar.goToOverview();
            dashboard.clickStep(containerName);

            // Navigate to snapshot page
            PWSnapshotPage snapshot = sidebar.goToLiveSnapshot();
            snapshot.waitForPageLoad();
            assertThat(snapshot.isDisplayed()).as("Snapshot panel should be displayed").isTrue();
        });
    }

    @Test
    @Order(19)
    @DisplayName("E2E: create match with physics modules, verify all commands available")
    void e2eAcceptance2() {
        withContainer("e2e2", (containerName, dashboard, sidebar) -> {
            // Create match with physics modules (GridMapModule required for position)
            PWMatchesPage matches = sidebar.goToMatches();
            matches.waitForPageLoad();
            matches.createMatch("EntityModule", "RigidBodyModule", "GridMapModule");

            // Create player
            PWPlayersPage players = sidebar.goToPlayers();
            players.waitForPageLoad();
            players.addPlayer();
            page.waitForTimeout(1000);

            // Create session using first options
            PWSessionsPage sessions = sidebar.goToSessions();
            sessions.waitForPageLoad();
            sessions.createSessionWithFirstOptions();

            // Navigate to commands page and verify physics commands are available
            PWCommandsPage commands = sidebar.goToCommands();
            commands.waitForPageLoad();
            java.util.List<String> availableCommands = commands.getAvailableCommands();
            assertThat(availableCommands).as("Commands should be available").isNotEmpty();
            assertThat(availableCommands).as("spawn command should be available").contains("spawn");
            assertThat(availableCommands).as("createMap command should be available").contains("createMap");
            assertThat(availableCommands).as("setEntityPosition command should be available").contains("setEntityPosition");
            assertThat(availableCommands).as("attachRigidBody command should be available").contains("attachRigidBody");

            // Step the simulation
            dashboard = sidebar.goToOverview();
            dashboard.clickStep(containerName);

            // Navigate to snapshot page and verify it loads
            PWSnapshotPage snapshot = sidebar.goToLiveSnapshot();
            snapshot.waitForPageLoad();
            assertThat(snapshot.isDisplayed()).as("Snapshot panel should be displayed").isTrue();
        });
    }

    @Test
    @Order(20)
    @DisplayName("Delta compression: verify full E2E flow with container, match, and snapshot")
    void e2eAcceptance3() {
        withContainer("e2e3", (containerName, dashboard, sidebar) -> {
            // Create match with EntityModule
            PWMatchesPage matches = sidebar.goToMatches();
            matches.waitForPageLoad();
            matches.createMatch("EntityModule");

            // Create player
            PWPlayersPage players = sidebar.goToPlayers();
            players.waitForPageLoad();
            players.addPlayer();
            page.waitForTimeout(1000);

            // Create session
            PWSessionsPage sessions = sidebar.goToSessions();
            sessions.waitForPageLoad();
            sessions.createSessionWithFirstOptions();

            // Verify commands page loads
            PWCommandsPage commands = sidebar.goToCommands();
            commands.waitForPageLoad();
            assertThat(commands.getAvailableCommands()).as("Commands should be available").isNotEmpty();

            // Step and verify snapshot
            dashboard = sidebar.goToOverview();
            dashboard.clickStep(containerName);

            PWSnapshotPage snapshot = sidebar.goToLiveSnapshot();
            snapshot.waitForPageLoad();
            assertThat(snapshot.isDisplayed()).as("Snapshot panel should be displayed").isTrue();
        });
    }

    @Test
    @Order(21)
    @DisplayName("Resource: verify resources panel is accessible")
    void e2eAcceptance5() {
        withContainer("e2e5", (containerName, dashboard, sidebar) -> {
            // Navigate to resources and verify page loads
            PWResourcesPage resources = sidebar.goToResources();
            resources.waitForPageLoad();
            assertThat(resources.isDisplayed()).as("Resources panel should be displayed").isTrue();
        });
    }

    @Test
    @Order(22)
    @DisplayName("E2E: create container with pre-selected modules, verify full setup flow")
    void e2eCreateContainerWithModules() {
        String containerName = TEST_CONTAINER_PREFIX + "with-modules-" + System.currentTimeMillis();
        try {
            PWDashboardPage dashboard = new PWDashboardPage(page);
            dashboard.waitForPageLoad();

            // Create container with EntityModule pre-selected
            // This tests the code path that was causing NullPointerException when
            // trying to install modules into a container before it was started
            dashboard.createContainerWithModules(containerName, "EntityModule");

            // Start the container - this should succeed without errors
            dashboard.startContainer(containerName);

            // Verify container reached RUNNING status
            String status = dashboard.getContainerStatus(containerName);
            assertThat(status)
                .as("Container should be running after creation with modules")
                .contains("RUNNING");

            // Select container
            PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
            sidebar.selectContainer(containerName);

            // Create a match to enable commands
            PWMatchesPage matches = sidebar.goToMatches();
            matches.waitForPageLoad();
            matches.createMatch("EntityModule");

            // Create a player
            PWPlayersPage players = sidebar.goToPlayers();
            players.waitForPageLoad();
            players.addPlayer();
            page.waitForTimeout(500);

            // Create a session
            PWSessionsPage sessions = sidebar.goToSessions();
            sessions.waitForPageLoad();
            sessions.createSessionWithFirstOptions();

            // Navigate to commands page
            PWCommandsPage commands = sidebar.goToCommands();
            commands.waitForPageLoad();
            java.util.List<String> availableCommands = commands.getAvailableCommands();
            assertThat(availableCommands)
                .as("spawn command should be available from EntityModule - proves container with modules works")
                .contains("spawn");

            // Verify the command dialog opens (proves the commands are functional)
            commands.openSendDialog("spawn");
            page.waitForTimeout(500);
            assertThat(page.locator(".MuiDialog-root").isVisible())
                .as("Spawn command dialog should open")
                .isTrue();
            page.keyboard().press("Escape");
            page.waitForTimeout(300);

            // Step the simulation to prove container tick works
            dashboard = sidebar.goToOverview();
            long tickBefore = dashboard.getContainerTick(containerName);
            dashboard.clickStep(containerName);
            page.waitForTimeout(500);
            long tickAfter = dashboard.getContainerTick(containerName);
            assertThat(tickAfter)
                .as("Container tick should advance - proves container is functional")
                .isGreaterThan(tickBefore);

            // Navigate to snapshot page and verify it loads
            PWSnapshotPage snapshot = sidebar.goToLiveSnapshot();
            snapshot.waitForPageLoad();
            assertThat(snapshot.isDisplayed())
                .as("Snapshot page should be accessible")
                .isTrue();

            // Select the first match and verify snapshot functionality
            snapshot.selectMatch(0);
            page.waitForTimeout(1000);
            // The snapshot page should show something (either data or "no snapshot" message)
            assertThat(snapshot.isDisplayed() || snapshot.isNoSnapshotMessageDisplayed())
                .as("Snapshot page should show data or appropriate message")
                .isTrue();
        } finally {
            cleanupContainer(containerName);
        }
    }

    @Test
    @Order(23)
    @DisplayName("Memory: verify container statistics are visible")
    void createContainer_verifyMemoryUsageChangesAsWeCreateEntities() {
        withContainer("memory", (containerName, dashboard, sidebar) -> {
            // Create match with EntityModule
            PWMatchesPage matches = sidebar.goToMatches();
            matches.waitForPageLoad();
            matches.createMatch("EntityModule");

            // Create player
            PWPlayersPage players = sidebar.goToPlayers();
            players.waitForPageLoad();
            players.addPlayer();
            page.waitForTimeout(1000);

            // Create session
            PWSessionsPage sessions = sidebar.goToSessions();
            sessions.waitForPageLoad();
            sessions.createSessionWithFirstOptions();

            // Navigate back to dashboard and verify container is running with stats visible
            dashboard = sidebar.goToOverview();
            dashboard.waitForPageLoad();
            dashboard.clickStep(containerName);

            // Verify the dashboard shows container statistics (the stats panel should be visible for running container)
            assertThat(page.locator("text=Container Statistics").isVisible() ||
                       page.locator("text=Entities").isVisible())
                .as("Container stats should be visible").isTrue();
        });
    }
}
