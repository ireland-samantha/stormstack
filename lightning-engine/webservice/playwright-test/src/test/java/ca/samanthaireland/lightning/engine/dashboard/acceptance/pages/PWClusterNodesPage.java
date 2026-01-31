/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */
package ca.samanthaireland.lightning.engine.dashboard.acceptance.pages;

import com.microsoft.playwright.Page;

/**
 * Playwright Page Object for Cluster Nodes panel.
 */
public class PWClusterNodesPage {
    private final Page page;

    public PWClusterNodesPage(Page page) {
        this.page = page;
    }

    public boolean isDisplayed() {
        return page.getByText("Cluster Nodes").isVisible();
    }

    public boolean hasNodesTable() {
        return page.locator("table").isVisible();
    }

    public boolean hasNodeIdColumn() {
        return page.getByText("Node ID").isVisible();
    }

    public boolean hasStatusColumn() {
        return page.getByText("Status").isVisible();
    }

    public int getNodeCount() {
        return page.locator("tbody tr").count();
    }
}
