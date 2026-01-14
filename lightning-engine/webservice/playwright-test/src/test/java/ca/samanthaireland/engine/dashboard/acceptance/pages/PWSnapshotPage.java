/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */
package ca.samanthaireland.engine.dashboard.acceptance.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import java.util.List;

/**
 * Playwright Page Object for the Snapshot page.
 */
public class PWSnapshotPage {
    private final Page page;

    public PWSnapshotPage(Page page) {
        this.page = page;
    }

    public boolean isDisplayed() {
        page.waitForLoadState();
        page.waitForTimeout(500);
        return page.getByText("Snapshot").first().isVisible() ||
               page.getByText("No Container Selected").isVisible();
    }

    public void waitForPageLoad() {
        page.waitForLoadState();
        page.waitForTimeout(500);
    }

    public void clickLoadSnapshot() {
        // Button has tooltip "Refresh"
        page.locator("button[title='Refresh']").click();
    }

    public void toggleAutoRefresh() {
        page.getByLabel("Auto-refresh").click();
    }

    public boolean isAutoRefreshEnabled() {
        return page.getByLabel("Auto-refresh").isChecked();
    }

    public long getCurrentTick() {
        String tickText = page.locator("//*[contains(text(), 'Tick:')]").textContent();
        return Long.parseLong(tickText.replaceAll("[^0-9]", ""));
    }

    public List<String> getModuleNames() {
        return page.locator(".MuiTreeItem-label").allTextContents();
    }

    public void expandModule(String moduleName) {
        page.locator(".MuiTreeItem-root").filter(new Locator.FilterOptions().setHasText(moduleName))
                .locator(".MuiTreeItem-iconContainer")
                .click();
    }

    public int getEntityCount() {
        return page.locator(".MuiTreeItem-root").filter(new Locator.FilterOptions()
                .setHasText(java.util.regex.Pattern.compile("Entity \\d+"))).count();
    }

    public void selectEntity(long entityId) {
        page.locator(".MuiTreeItem-label").filter(new Locator.FilterOptions()
                .setHasText("Entity " + entityId)).click();
    }

    public boolean isNoSnapshotMessageDisplayed() {
        return page.getByText("No snapshot").isVisible() || page.getByText("Select a match").isVisible();
    }

    public boolean isNoContainerSelectedMessageDisplayed() {
        return page.getByText("No Container Selected").isVisible() ||
               page.getByText("Select a container").isVisible();
    }

    /**
     * Select a match from the match dropdown.
     * @param matchIndex 0-based index of the match to select
     */
    public void selectMatch(int matchIndex) {
        page.getByLabel("Match").click();
        page.getByRole(AriaRole.OPTION).nth(matchIndex).click();
        page.waitForTimeout(500);
    }

    /**
     * Check if the snapshot contains a specific module name.
     */
    public boolean hasModule(String moduleName) {
        return page.getByText(moduleName).isVisible();
    }

    /**
     * Check if the snapshot contains a specific component name.
     */
    public boolean hasComponent(String componentName) {
        return page.getByText(componentName).isVisible();
    }

    /**
     * Get the text content of the snapshot display (for debugging/verification).
     */
    public String getSnapshotText() {
        return page.locator(".MuiPaper-root").first().textContent();
    }

    /**
     * Wait for snapshot data to load (wait for module names to appear).
     */
    public void waitForSnapshotData() {
        // Wait for either module data or "no snapshot" message
        page.waitForCondition(() ->
            page.getByText("EntityModule").isVisible() ||
            page.getByText("No snapshot").isVisible() ||
            page.getByText("Select a match").isVisible()
        );
        page.waitForTimeout(500);
    }
}
