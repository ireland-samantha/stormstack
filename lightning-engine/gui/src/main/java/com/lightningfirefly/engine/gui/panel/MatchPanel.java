package com.lightningfirefly.engine.gui.panel;

import com.lightningfirefly.engine.gui.service.GameMasterService;
import com.lightningfirefly.engine.gui.service.GameMasterService.GameMasterInfo;
import com.lightningfirefly.engine.gui.service.MatchService;
import com.lightningfirefly.engine.gui.service.MatchService.MatchEvent;
import com.lightningfirefly.engine.gui.service.MatchService.MatchInfo;
import com.lightningfirefly.engine.gui.service.ModuleService;
import com.lightningfirefly.engine.gui.service.ModuleService.ModuleInfo;
import com.lightningfirefly.engine.rendering.render2d.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Panel for managing matches.
 *
 * <p>Allows viewing existing matches, creating new matches with selected modules
 * and game masters, and deleting matches. Supports selecting a match to view its snapshot.
 */
@Slf4j
public class MatchPanel extends AbstractWindowComponent {

    private final Panel visualPanel;
    private final ComponentFactory factory;
    private final ComponentFactory.Colours colours;
    private final MatchService matchService;
    private final ModuleService moduleService;
    private final GameMasterService gameMasterService;

    private final ListView matchList;
    private final ListView moduleList;
    private final ListView gameMasterList;
    private final Label statusLabel;
    private final Button refreshButton;
    private final Button createButton;
    private final Button deleteButton;
    private final Button viewSnapshotButton;

    private final List<MatchInfo> matches = new CopyOnWriteArrayList<>();
    private final List<ModuleInfo> availableModules = new CopyOnWriteArrayList<>();
    private final List<GameMasterInfo> availableGameMasters = new CopyOnWriteArrayList<>();
    private final List<String> selectedModules = new ArrayList<>();
    private final List<String> selectedGameMasters = new ArrayList<>();
    private volatile boolean needsRefresh = false;
    private volatile boolean needsModuleRefresh = false;
    private volatile boolean needsGameMasterRefresh = false;

    private Consumer<Long> onViewSnapshot;

    public MatchPanel(ComponentFactory factory, int x, int y, int width, int height, String serverUrl) {
        this(factory, x, y, width, height,
                new MatchService(serverUrl),
                new ModuleService(serverUrl),
                new GameMasterService(serverUrl));
        refreshMatches();
        refreshModules();
        refreshGameMasters();
    }

    public MatchPanel(ComponentFactory factory, int x, int y, int width, int height,
                      MatchService matchService, ModuleService moduleService) {
        this(factory, x, y, width, height, matchService, moduleService, null);
    }

    public MatchPanel(ComponentFactory factory, int x, int y, int width, int height,
                      MatchService matchService, ModuleService moduleService, GameMasterService gameMasterService) {
        super(x, y, width, height);
        this.factory = factory;
        this.colours = factory.getColours();

        // Create visual panel container
        this.visualPanel = factory.createPanel(x, y, width, height);
        this.visualPanel.setTitle("Match Manager");

        this.matchService = matchService;
        this.moduleService = moduleService;
        this.gameMasterService = gameMasterService;

        // Create status label
        statusLabel = factory.createLabel(x + 10, y + 35, "Ready", 12.0f);
        statusLabel.setTextColor(colours.textSecondary());

        // Create buttons row
        int buttonY = y + 55;
        int buttonWidth = 80;
        int buttonSpacing = 8;

        refreshButton = factory.createButton(x + 10, buttonY, buttonWidth, 28, "Refresh");
        refreshButton.setOnClick(() -> {
            refreshMatches();
            refreshModules();
            refreshGameMasters();
        });

        createButton = factory.createButton(x + 10 + buttonWidth + buttonSpacing, buttonY, buttonWidth, 28, "Create");
        createButton.setOnClick(this::createMatch);

        deleteButton = factory.createButton(x + 10 + (buttonWidth + buttonSpacing) * 2, buttonY, buttonWidth, 28, "Delete");
        deleteButton.setOnClick(this::deleteMatch);

        viewSnapshotButton = factory.createButton(x + 10 + (buttonWidth + buttonSpacing) * 3, buttonY, 100, 28, "View Snapshot");
        viewSnapshotButton.setOnClick(this::viewSelectedSnapshot);

        // Create three side-by-side lists
        int listY = buttonY + 38;
        int listHeight = height - listY - 20 + y;
        int thirdWidth = (width - 40) / 3;

        // Match list (left)
        Label matchListLabel = factory.createLabel(x + 10, listY - 20, "Matches:", 12.0f);
        matchListLabel.setTextColor(colours.textPrimary());
        matchList = factory.createListView(x + 10, listY, thirdWidth, listHeight);

        // Module list (center) - for selecting modules when creating matches
        Label moduleListLabel = factory.createLabel(x + 20 + thirdWidth, listY - 20, "Modules (select):", 12.0f);
        moduleListLabel.setTextColor(colours.textPrimary());
        moduleList = factory.createListView(x + 20 + thirdWidth, listY, thirdWidth, listHeight);
        moduleList.setMultiSelectEnabled(true);

        // Game master list (right) - for selecting game masters when creating matches
        Label gameMasterListLabel = factory.createLabel(x + 30 + thirdWidth * 2, listY - 20, "Game Masters (select):", 12.0f);
        gameMasterListLabel.setTextColor(colours.textPrimary());
        gameMasterList = factory.createListView(x + 30 + thirdWidth * 2, listY, thirdWidth, listHeight);
        gameMasterList.setMultiSelectEnabled(true);

        // Add components to visual panel
        visualPanel.addChild((WindowComponent) statusLabel);
        visualPanel.addChild((WindowComponent) refreshButton);
        visualPanel.addChild((WindowComponent) createButton);
        visualPanel.addChild((WindowComponent) deleteButton);
        visualPanel.addChild((WindowComponent) viewSnapshotButton);
        visualPanel.addChild((WindowComponent) matchListLabel);
        visualPanel.addChild((WindowComponent) matchList);
        visualPanel.addChild((WindowComponent) moduleListLabel);
        visualPanel.addChild((WindowComponent) moduleList);
        visualPanel.addChild((WindowComponent) gameMasterListLabel);
        visualPanel.addChild((WindowComponent) gameMasterList);

        // Setup service listeners
        matchService.addListener(this::onMatchEvent);
    }

    /**
     * Set the callback for viewing a match's snapshot.
     */
    public void setOnViewSnapshot(Consumer<Long> onViewSnapshot) {
        this.onViewSnapshot = onViewSnapshot;
    }

    /**
     * Refresh the match list.
     */
    public void refreshMatches() {
        setStatus("Loading matches...", colours.textSecondary());
        matchService.listMatches().thenAccept(matchInfos -> {
            matches.clear();
            matches.addAll(matchInfos);
            needsRefresh = true;
            setStatus("Loaded " + matchInfos.size() + " matches", colours.green());
        });
    }

    /**
     * Refresh the available modules list.
     */
    public void refreshModules() {
        moduleService.listModules().thenAccept(moduleInfos -> {
            availableModules.clear();
            availableModules.addAll(moduleInfos);
            needsModuleRefresh = true;
        });
    }

    /**
     * Refresh the available game masters list.
     */
    public void refreshGameMasters() {
        if (gameMasterService != null) {
            gameMasterService.listGameMasters().thenAccept(gameMasterInfos -> {
                availableGameMasters.clear();
                availableGameMasters.addAll(gameMasterInfos);
                needsGameMasterRefresh = true;
            });
        }
    }

    /**
     * Create a new match. Match ID is generated server-side.
     */
    private void createMatch() {
        // Get selected modules from module list (supports multi-selection)
        List<String> moduleNames = moduleList.getSelectedItems();

        setStatus("Creating match with " + moduleNames.size() + " module(s)...", colours.textSecondary());
        matchService.createMatch(moduleNames)
            .thenAccept(id -> {
                if (id > 0) {
                    setStatus("Created match " + id + " with " + moduleNames.size() + " module(s)", colours.green());
                    refreshMatches();
                } else {
                    setStatus("Failed to create match", colours.red());
                }
            });
    }

    /**
     * Delete the selected match.
     */
    private void deleteMatch() {
        int selectedIndex = matchList.getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= matches.size()) {
            setStatus("Select a match to delete", colours.yellow());
            return;
        }

        MatchInfo match = matches.get(selectedIndex);
        setStatus("Deleting match " + match.id() + "...", colours.textSecondary());
        matchService.deleteMatch(match.id())
            .thenAccept(success -> {
                if (success) {
                    setStatus("Deleted match " + match.id(), colours.green());
                    refreshMatches();
                } else {
                    setStatus("Delete failed", colours.red());
                }
            });
    }

    /**
     * View the snapshot for the selected match.
     */
    private void viewSelectedSnapshot() {
        int selectedIndex = matchList.getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= matches.size()) {
            setStatus("Select a match to view", colours.yellow());
            return;
        }

        MatchInfo match = matches.get(selectedIndex);
        if (onViewSnapshot != null) {
            onViewSnapshot.accept(match.id());
            setStatus("Viewing match " + match.id(), colours.green());
        } else {
            setStatus("Snapshot viewer not configured", colours.yellow());
        }
    }

    private void onMatchEvent(MatchEvent event) {
        switch (event.type()) {
            case CREATED -> setStatus("Created: " + event.message(), colours.green());
            case DELETED -> setStatus("Deleted match " + event.matchId(), colours.green());
            case ERROR -> setStatus("Error: " + event.message(), colours.red());
        }
    }

    private void setStatus(String message, float[] color) {
        statusLabel.setText(message);
        statusLabel.setTextColor(color);
    }

    /**
     * Update the panel. Call this from the render loop.
     */
    public void update() {
        if (needsRefresh) {
            needsRefresh = false;
            updateMatchList();
        }
        if (needsModuleRefresh) {
            needsModuleRefresh = false;
            updateModuleList();
        }
        if (needsGameMasterRefresh) {
            needsGameMasterRefresh = false;
            updateGameMasterList();
        }
    }

    private void updateMatchList() {
        List<String> items = new ArrayList<>();
        for (MatchInfo match : matches) {
            items.add("Match " + match.id());
        }
        matchList.setItems(items);
    }

    private void updateModuleList() {
        List<String> items = new ArrayList<>();
        for (ModuleInfo module : availableModules) {
            items.add(module.name());
        }
        moduleList.setItems(items);
    }

    private void updateGameMasterList() {
        List<String> items = new ArrayList<>();
        for (GameMasterInfo gameMaster : availableGameMasters) {
            items.add(gameMaster.name());
        }
        gameMasterList.setItems(items);
    }

    /**
     * Cleanup resources.
     */
    public void dispose() {
        matchService.shutdown();
        moduleService.shutdown();
        if (gameMasterService != null) {
            gameMasterService.shutdown();
        }
    }

    /**
     * Get the list of loaded matches.
     */
    public List<MatchInfo> getMatches() {
        return new ArrayList<>(matches);
    }

    /**
     * Get the selected match, if any.
     */
    public MatchInfo getSelectedMatch() {
        int index = matchList.getSelectedIndex();
        if (index >= 0 && index < matches.size()) {
            return matches.get(index);
        }
        return null;
    }

    /**
     * Get the match service.
     */
    public MatchService getMatchService() {
        return matchService;
    }

    /**
     * Get the module service.
     */
    public ModuleService getModuleService() {
        return moduleService;
    }

    /**
     * Get the list of available modules for creating matches.
     */
    public List<ModuleInfo> getAvailableModules() {
        return new ArrayList<>(availableModules);
    }

    /**
     * Select a module by index (for UI testing).
     */
    public void selectModule(int index) {
        if (index >= 0 && index < availableModules.size()) {
            moduleList.setSelectedIndex(index);
            String moduleName = availableModules.get(index).name();
            if (!selectedModules.contains(moduleName)) {
                selectedModules.add(moduleName);
            }
        }
    }

    /**
     * Create a match with the selected modules (for UI testing).
     * Match ID is generated server-side.
     *
     * @return CompletableFuture with the generated match ID
     */
    public java.util.concurrent.CompletableFuture<Long> createMatchWithSelectedModules() {
        // Use selectedModules list which is populated by selectModule()
        // moduleList.getSelectedItems() may not reflect programmatic selection
        return matchService.createMatch(new ArrayList<>(selectedModules));
    }

    /**
     * Clear selected modules (for testing).
     */
    public void clearSelectedModules() {
        selectedModules.clear();
    }

    /**
     * Get the list of selected module names (for testing).
     */
    public List<String> getSelectedModuleNames() {
        return new ArrayList<>(selectedModules);
    }

    // Delegate rendering and input to the visual panel

    @Override
    public void render(long nvg) {
        if (visible) {
            visualPanel.render(nvg);
        }
    }

    @Override
    public boolean onMouseClick(int mx, int my, int button, int action) {
        return visible && contains(mx, my) && visualPanel.onMouseClick(mx, my, button, action);
    }

    @Override
    public boolean onMouseMove(int mx, int my) {
        return visible && visualPanel.onMouseMove(mx, my);
    }

    @Override
    public boolean onMouseScroll(int mx, int my, double scrollX, double scrollY) {
        return visible && contains(mx, my) && visualPanel.onMouseScroll(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean onKeyPress(int key, int action) {
        return onKeyPress(key, action, 0);
    }

    @Override
    public boolean onKeyPress(int key, int action, int mods) {
        return visible && visualPanel.onKeyPress(key, action, mods);
    }

    @Override
    public boolean onCharInput(int codepoint) {
        return visible && visualPanel.onCharInput(codepoint);
    }
}
