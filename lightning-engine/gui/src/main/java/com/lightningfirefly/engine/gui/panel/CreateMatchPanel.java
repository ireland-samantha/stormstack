package com.lightningfirefly.engine.gui.panel;

import com.lightningfirefly.engine.gui.service.GameMasterService;
import com.lightningfirefly.engine.gui.service.GameMasterService.GameMasterInfo;
import com.lightningfirefly.engine.gui.service.MatchService;
import com.lightningfirefly.engine.gui.service.MatchService.CreateMatchRequest;
import com.lightningfirefly.engine.gui.service.ModuleService;
import com.lightningfirefly.engine.gui.service.ModuleService.ModuleInfo;
import com.lightningfirefly.engine.rendering.render2d.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Panel for creating a new match with module and game master selection.
 *
 * <p>Displays two multi-select lists (modules, game masters) and Create/Cancel buttons.
 * On successful creation, triggers a callback and hides itself.
 */
@Slf4j
public class CreateMatchPanel extends AbstractWindowComponent {

    /**
     * DTO for panel dimensions.
     */
    public record PanelBounds(int x, int y, int width, int height) {}

    private final Panel visualPanel;
    private final ComponentFactory factory;
    private final ComponentFactory.Colours colours;

    private final MatchService matchService;
    private final ModuleService moduleService;
    private final GameMasterService gameMasterService;

    private final ListView moduleList;
    private final ListView gameMasterList;
    private final Label statusLabel;
    private final Button createButton;
    private final Button cancelButton;

    private final List<ModuleInfo> availableModules = new CopyOnWriteArrayList<>();
    private final List<GameMasterInfo> availableGameMasters = new CopyOnWriteArrayList<>();
    private volatile boolean needsModuleRefresh = false;
    private volatile boolean needsGameMasterRefresh = false;

    private Consumer<Long> onMatchCreated;
    private Runnable onCancel;

    /**
     * Create a CreateMatchPanel.
     *
     * @param factory the component factory
     * @param bounds the panel bounds
     * @param matchService the match service for creating matches
     * @param moduleService the module service for listing available modules
     * @param gameMasterService the game master service for listing available game masters
     */
    public CreateMatchPanel(ComponentFactory factory, PanelBounds bounds,
                            MatchService matchService, ModuleService moduleService,
                            GameMasterService gameMasterService) {
        super(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        this.factory = factory;
        this.colours = factory.getColours();
        this.matchService = matchService;
        this.moduleService = moduleService;
        this.gameMasterService = gameMasterService;

        int x = bounds.x();
        int y = bounds.y();
        int width = bounds.width();
        int height = bounds.height();

        // Create visual panel container
        this.visualPanel = factory.createPanel(x, y, width, height);
        this.visualPanel.setTitle("Create Match");

        // Create status label
        statusLabel = factory.createLabel(x + 10, y + 35, "Select modules and game masters", 12.0f);
        statusLabel.setTextColor(colours.textSecondary());

        // Create buttons row at bottom
        int buttonY = y + height - 50;
        int buttonWidth = 100;
        int buttonSpacing = 10;

        createButton = factory.createButton(x + 10, buttonY, buttonWidth, 32, "Create Match");
        createButton.setBackgroundColor(colours.success());
        createButton.setOnClick(this::createMatch);

        cancelButton = factory.createButton(x + 10 + buttonWidth + buttonSpacing, buttonY, buttonWidth, 32, "Cancel");
        cancelButton.setOnClick(this::cancel);

        // Create side-by-side lists
        int listY = y + 60;
        int listHeight = height - 130;
        int halfWidth = (width - 30) / 2;

        // Module list (left side)
        Label moduleListLabel = factory.createLabel(x + 10, listY - 20, "Modules:", 12.0f);
        moduleListLabel.setTextColor(colours.textPrimary());
        moduleList = factory.createListView(x + 10, listY, halfWidth, listHeight);
        moduleList.setMultiSelectEnabled(true);

        // Game master list (right side)
        Label gameMasterListLabel = factory.createLabel(x + 20 + halfWidth, listY - 20, "Game Masters:", 12.0f);
        gameMasterListLabel.setTextColor(colours.textPrimary());
        gameMasterList = factory.createListView(x + 20 + halfWidth, listY, halfWidth, listHeight);
        gameMasterList.setMultiSelectEnabled(true);

        // Add components to visual panel
        visualPanel.addChild((WindowComponent) statusLabel);
        visualPanel.addChild((WindowComponent) createButton);
        visualPanel.addChild((WindowComponent) cancelButton);
        visualPanel.addChild((WindowComponent) moduleListLabel);
        visualPanel.addChild((WindowComponent) moduleList);
        visualPanel.addChild((WindowComponent) gameMasterListLabel);
        visualPanel.addChild((WindowComponent) gameMasterList);
    }

    /**
     * Set the callback for when a match is created.
     *
     * @param onMatchCreated callback receiving the new match ID
     */
    public void setOnMatchCreated(Consumer<Long> onMatchCreated) {
        this.onMatchCreated = onMatchCreated;
    }

    /**
     * Set the callback for when creation is cancelled.
     *
     * @param onCancel callback for cancel action
     */
    public void setOnCancel(Runnable onCancel) {
        this.onCancel = onCancel;
    }

    /**
     * Refresh the available modules and game masters.
     */
    public void refresh() {
        refreshModules();
        refreshGameMasters();
        clearSelections();
        setStatus("Select modules and game masters", colours.textSecondary());
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
     * Clear all selections.
     */
    public void clearSelections() {
        moduleList.clearSelection();
        gameMasterList.clearSelection();
    }

    /**
     * Update the panel. Call this from the render loop.
     */
    public void update() {
        if (needsModuleRefresh) {
            needsModuleRefresh = false;
            updateModuleList();
        }
        if (needsGameMasterRefresh) {
            needsGameMasterRefresh = false;
            updateGameMasterList();
        }
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

    private void createMatch() {
        List<String> selectedModules = moduleList.getSelectedItems();
        List<String> selectedGameMasters = gameMasterList.getSelectedItems();

        setStatus("Creating match...", colours.textSecondary());

        CreateMatchRequest request = new CreateMatchRequest(selectedModules, selectedGameMasters);
        matchService.createMatch(request)
            .thenAccept(matchId -> {
                if (matchId > 0) {
                    log.info("Created match {} with {} modules and {} game masters",
                            matchId, selectedModules.size(), selectedGameMasters.size());
                    setStatus("Created match " + matchId, colours.green());
                    if (onMatchCreated != null) {
                        onMatchCreated.accept(matchId);
                    }
                } else {
                    setStatus("Failed to create match", colours.red());
                }
            });
    }

    private void cancel() {
        if (onCancel != null) {
            onCancel.run();
        }
    }

    private void setStatus(String message, float[] color) {
        statusLabel.setText(message);
        statusLabel.setTextColor(color);
    }

    /**
     * Get the list of available modules.
     */
    public List<ModuleInfo> getAvailableModules() {
        return new ArrayList<>(availableModules);
    }

    /**
     * Get the list of available game masters.
     */
    public List<GameMasterInfo> getAvailableGameMasters() {
        return new ArrayList<>(availableGameMasters);
    }

    /**
     * Get the selected module names.
     */
    public List<String> getSelectedModules() {
        return moduleList.getSelectedItems();
    }

    /**
     * Get the selected game master names.
     */
    public List<String> getSelectedGameMasters() {
        return gameMasterList.getSelectedItems();
    }

    /**
     * Select a module by index (for testing).
     */
    public void selectModule(int index) {
        moduleList.selectIndex(index);
    }

    /**
     * Select a game master by index (for testing).
     */
    public void selectGameMaster(int index) {
        gameMasterList.selectIndex(index);
    }

    /**
     * Trigger the create action (for testing).
     */
    public void triggerCreate() {
        createMatch();
    }

    /**
     * Trigger the cancel action (for testing).
     */
    public void triggerCancel() {
        cancel();
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
