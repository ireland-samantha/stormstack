/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */
package ca.samanthaireland.engine.dashboard.acceptance.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Playwright Page Object for the Players page.
 */
public class PWPlayersPage {
    private final Page page;

    public PWPlayersPage(Page page) {
        this.page = page;
    }

    public boolean isDisplayed() {
        // Wait for page to load and check for any Players content
        page.waitForLoadState();
        page.waitForTimeout(500); // Give time for React to render
        return page.getByText("Players").first().isVisible() ||
               page.getByText("No Container Selected").isVisible() ||
               page.getByText("Add Player").isVisible();
    }

    public void waitForPageLoad() {
        page.waitForLoadState();
        // Wait for the Add Player button to appear (indicates data is loaded)
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Add Player"))
            .waitFor(new Locator.WaitForOptions().setTimeout(10000));
        page.waitForTimeout(500);
    }

    public void clickAddPlayer() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Add Player")).click();
    }

    public void addPlayer() {
        clickAddPlayer();
        waitForSuccessAlert();
    }

    public int getPlayerCount() {
        return page.locator("tbody tr").count();
    }

    public List<String> getPlayerIds() {
        return page.locator("tbody tr td:first-child .MuiChip-label").allTextContents();
    }

    public void deletePlayer(int playerId) {
        page.locator("tbody tr").filter(new Locator.FilterOptions().setHasText("Player " + playerId))
                .locator("button[title='Delete player']")
                .click();
        // Confirm
        page.locator(".MuiDialog-root")
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(Pattern.compile("Delete|Confirm")))
                .click();
    }

    public void refresh() {
        page.locator("button[title='Refresh']").click();
    }

    public void waitForSuccessAlert() {
        page.locator(".MuiAlert-standardSuccess, .MuiAlert-filledSuccess").waitFor();
    }

    public boolean isNoPlayersMessageDisplayed() {
        return page.getByText("No players").isVisible();
    }

    public boolean isNoContainerSelectedMessageDisplayed() {
        return page.getByText("No Container Selected").isVisible();
    }
}
