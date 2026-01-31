/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */
package ca.samanthaireland.lightning.engine.dashboard.acceptance;

import ca.samanthaireland.lightning.engine.dashboard.acceptance.pages.*;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Playwright integration tests for Control Plane panels.
 * Tests the cluster overview, nodes, modules, deployments, and autoscaler panels.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ControlPlanePlaywrightIT extends PlaywrightTestBase {

    @BeforeEach
    void login() {
        loginAsAdmin();
    }

    // ==================== Cluster Overview Tests ====================

    @Test
    @Order(1)
    void viewClusterOverviewPage() {
        // When I navigate to cluster overview
        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWClusterOverviewPage overviewPage = sidebar.goToClusterOverview();

        // Then the page should load and display cluster status
        assertThat(overviewPage.isDisplayed()).isTrue();
    }

    @Test
    @Order(2)
    void clusterOverviewShowsStatusSections() {
        // When I navigate to cluster overview
        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWClusterOverviewPage overviewPage = sidebar.goToClusterOverview();

        // Then I should see nodes and matches summaries
        assertThat(overviewPage.hasNodesSummary() || overviewPage.hasMatchesSummary()).isTrue();
    }

    // ==================== Cluster Nodes Tests ====================

    @Test
    @Order(3)
    void viewClusterNodesPage() {
        // When I navigate to cluster nodes
        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWClusterNodesPage nodesPage = sidebar.goToClusterNodes();

        // Then the page should load
        assertThat(nodesPage.isDisplayed()).isTrue();
    }

    @Test
    @Order(4)
    void clusterNodesShowsTable() {
        // When I navigate to cluster nodes
        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWClusterNodesPage nodesPage = sidebar.goToClusterNodes();

        // Then I should see a table with node information
        assertThat(nodesPage.hasNodesTable()).isTrue();
    }

    // ==================== Cluster Modules Tests ====================

    @Test
    @Order(5)
    void viewClusterModulesPage() {
        // When I navigate to cluster modules
        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWClusterModulesPage modulesPage = sidebar.goToClusterModules();

        // Then the page should load
        assertThat(modulesPage.isDisplayed()).isTrue();
    }

    @Test
    @Order(6)
    void clusterModulesShowsTable() {
        // When I navigate to cluster modules
        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWClusterModulesPage modulesPage = sidebar.goToClusterModules();

        // Then I should see a table with module information
        assertThat(modulesPage.hasModulesTable()).isTrue();
    }

    // ==================== Deployments Tests ====================

    @Test
    @Order(7)
    void viewDeploymentsPage() {
        // When I navigate to deployments
        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWDeploymentsPage deploymentsPage = sidebar.goToDeployments();

        // Then the page should load
        assertThat(deploymentsPage.isDisplayed()).isTrue();
    }

    @Test
    @Order(8)
    void deploymentsShowsTable() {
        // When I navigate to deployments
        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWDeploymentsPage deploymentsPage = sidebar.goToDeployments();

        // Then I should see a table with deployments
        assertThat(deploymentsPage.hasDeploymentsTable()).isTrue();
    }

    @Test
    @Order(9)
    void deploymentsHasNewDeploymentButton() {
        // When I navigate to deployments
        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWDeploymentsPage deploymentsPage = sidebar.goToDeployments();

        // Then I should see the new deployment button
        assertThat(deploymentsPage.hasNewDeploymentButton()).isTrue();
    }

    @Test
    @Order(10)
    void newDeploymentDialogOpens() {
        // When I navigate to deployments and click new deployment
        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWDeploymentsPage deploymentsPage = sidebar.goToDeployments();
        deploymentsPage.clickNewDeployment();

        // Then the dialog should open
        assertThat(deploymentsPage.isNewDeploymentDialogOpen()).isTrue();
    }

    // ==================== Autoscaler Tests ====================

    @Test
    @Order(11)
    void viewAutoscalerPage() {
        // When I navigate to autoscaler
        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWAutoscalerPage autoscalerPage = sidebar.goToAutoscaler();

        // Then the page should load
        assertThat(autoscalerPage.isDisplayed()).isTrue();
    }

    @Test
    @Order(12)
    void autoscalerShowsStatusSection() {
        // When I navigate to autoscaler
        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWAutoscalerPage autoscalerPage = sidebar.goToAutoscaler();

        // Then I should see the status section
        assertThat(autoscalerPage.hasStatusSection()).isTrue();
    }

    @Test
    @Order(13)
    void autoscalerShowsRecommendationSection() {
        // When I navigate to autoscaler
        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWAutoscalerPage autoscalerPage = sidebar.goToAutoscaler();

        // Then I should see the recommendation section
        assertThat(autoscalerPage.hasRecommendationSection()).isTrue();
    }

    @Test
    @Order(14)
    void autoscalerShowsNodeCounts() {
        // When I navigate to autoscaler
        PWSidebarNavigator sidebar = new PWSidebarNavigator(page);
        PWAutoscalerPage autoscalerPage = sidebar.goToAutoscaler();

        // Then I should see current and recommended node counts
        assertThat(autoscalerPage.hasCurrentNodesDisplay()).isTrue();
        assertThat(autoscalerPage.hasRecommendedNodesDisplay()).isTrue();
    }
}
