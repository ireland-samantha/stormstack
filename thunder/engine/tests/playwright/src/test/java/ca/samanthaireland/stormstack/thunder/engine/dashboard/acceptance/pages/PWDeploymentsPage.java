/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */
package ca.samanthaireland.stormstack.thunder.engine.dashboard.acceptance.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * Playwright Page Object for Deployments panel.
 */
public class PWDeploymentsPage {
    private final Page page;

    public PWDeploymentsPage(Page page) {
        this.page = page;
    }

    public boolean isDisplayed() {
        return page.getByText("Deployments").first().isVisible();
    }

    public boolean hasDeploymentsTable() {
        return page.locator("table").isVisible();
    }

    public boolean hasMatchIdColumn() {
        return page.getByText("Match ID").isVisible();
    }

    public boolean hasStatusColumn() {
        return page.getByText("Status").isVisible();
    }

    public boolean hasNewDeploymentButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("New Deployment")).isVisible();
    }

    public void clickNewDeployment() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("New Deployment")).click();
    }

    public boolean isNewDeploymentDialogOpen() {
        return page.locator(".MuiDialog-root").isVisible();
    }

    public int getDeploymentCount() {
        return page.locator("tbody tr").count();
    }
}
