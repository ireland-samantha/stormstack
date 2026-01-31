/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */
package ca.samanthaireland.stormstack.thunder.engine.dashboard.acceptance.pages;

import com.microsoft.playwright.Page;

/**
 * Playwright Page Object for Cluster Overview panel.
 */
public class PWClusterOverviewPage {
    private final Page page;

    public PWClusterOverviewPage(Page page) {
        this.page = page;
    }

    public boolean isDisplayed() {
        return page.getByText("Cluster Overview").isVisible();
    }

    public boolean hasClusterStatus() {
        return page.getByText("Cluster Status").isVisible();
    }

    public boolean hasNodesSummary() {
        return page.getByText("Nodes").count() > 0;
    }

    public boolean hasMatchesSummary() {
        return page.getByText("Matches").count() > 0 || page.getByText("Active Matches").count() > 0;
    }
}
