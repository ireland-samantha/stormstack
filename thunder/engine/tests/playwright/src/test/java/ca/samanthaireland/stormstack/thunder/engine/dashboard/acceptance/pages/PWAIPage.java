/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */
package ca.samanthaireland.stormstack.thunder.engine.dashboard.acceptance.pages;

import com.microsoft.playwright.Page;

/**
 * Playwright Page Object for the AI page.
 */
public class PWAIPage {
    private final Page page;

    public PWAIPage(Page page) {
        this.page = page;
    }

    public boolean isDisplayed() {
        page.waitForLoadState();
        page.waitForTimeout(500);
        return page.getByText("AI").first().isVisible() ||
               page.getByText("No Container Selected").isVisible();
    }

    public void waitForPageLoad() {
        page.waitForLoadState();
        page.waitForTimeout(500);
    }

    public boolean isNoContainerSelectedMessageDisplayed() {
        return page.getByText("No Container Selected").isVisible() ||
               page.getByText("Select a container").isVisible();
    }
}
