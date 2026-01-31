/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */
package ca.samanthaireland.lightning.engine.dashboard.acceptance.pages;

import com.microsoft.playwright.Page;

/**
 * Playwright Page Object for Cluster Modules panel.
 */
public class PWClusterModulesPage {
    private final Page page;

    public PWClusterModulesPage(Page page) {
        this.page = page;
    }

    public boolean isDisplayed() {
        return page.getByText("Cluster Modules").isVisible();
    }

    public boolean hasModulesTable() {
        return page.locator("table").isVisible();
    }

    public boolean hasModuleColumn() {
        return page.getByText("Module").isVisible();
    }

    public boolean hasVersionColumn() {
        return page.getByText("Current Version").isVisible();
    }

    public int getModuleCount() {
        return page.locator("tbody tr").count();
    }
}
