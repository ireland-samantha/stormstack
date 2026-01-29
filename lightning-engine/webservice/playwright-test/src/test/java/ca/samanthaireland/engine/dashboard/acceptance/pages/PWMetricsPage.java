/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */
package ca.samanthaireland.engine.dashboard.acceptance.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Playwright Page Object for Metrics panel.
 */
public class PWMetricsPage {
    private final Page page;

    public PWMetricsPage(Page page) {
        this.page = page;
    }

    public boolean isDisplayed() {
        return page.getByText("Metrics").isVisible();
    }

    public boolean hasBenchmarksSection() {
        return page.getByText("Module Benchmarks").count() > 0;
    }

    public int getBenchmarkCount() {
        if (!hasBenchmarksSection()) {
            return 0;
        }
        // Count rows in the benchmarks table (excluding header)
        return page.locator("table tbody tr").count();
    }

    public boolean hasStandardMetricsFields() {
        return page.getByText("Current Tick").isVisible() &&
               page.getByText("Total Entities").isVisible() &&
               page.getByText("Total Component Types").isVisible();
    }
}
