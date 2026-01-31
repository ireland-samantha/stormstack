/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */
package ca.samanthaireland.stormstack.thunder.engine.dashboard.acceptance.pages;

import com.microsoft.playwright.Page;

/**
 * Playwright Page Object for Autoscaler panel.
 */
public class PWAutoscalerPage {
    private final Page page;

    public PWAutoscalerPage(Page page) {
        this.page = page;
    }

    public boolean isDisplayed() {
        return page.getByText("Autoscaler").first().isVisible();
    }

    public boolean hasStatusSection() {
        return page.getByText("Status").isVisible();
    }

    public boolean hasRecommendationSection() {
        return page.getByText("Current Recommendation").isVisible();
    }

    public boolean hasCurrentNodesDisplay() {
        return page.getByText("Current Nodes").isVisible();
    }

    public boolean hasRecommendedNodesDisplay() {
        return page.getByText("Recommended Nodes").isVisible();
    }

    public boolean isActiveStatus() {
        return page.getByText("Active").isVisible();
    }

    public boolean isInCooldown() {
        return page.getByText("In Cooldown").isVisible();
    }
}
