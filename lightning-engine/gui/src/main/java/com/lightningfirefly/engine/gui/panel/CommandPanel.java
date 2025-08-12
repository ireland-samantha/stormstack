package com.lightningfirefly.engine.gui.panel;

import com.lightningfirefly.engine.gui.service.CommandService;
import com.lightningfirefly.engine.gui.service.CommandService.CommandEvent;
import com.lightningfirefly.engine.gui.service.CommandService.CommandInfo;
import com.lightningfirefly.engine.gui.service.CommandService.ParameterInfo;
import com.lightningfirefly.engine.rendering.render2d.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Panel for sending commands to the game engine.
 *
 * <p>Allows viewing available commands and submitting them with parameters.
 * Features a split layout with command list on the left and a dynamic
 * parameter form on the right.
 */
@Slf4j
public class CommandPanel extends AbstractWindowComponent {

    private static final int FORM_PANEL_WIDTH = 350;

    private final Panel visualPanel;
    private final ComponentFactory factory;
    private final ComponentFactory.Colours colours;
    private final CommandService commandService;

    private final ListView commandList;
    private final Label statusLabel;
    private final Button refreshButton;

    // Dynamic parameter form panel on the right
    private final CommandFormPanel formPanel;

    private final List<CommandInfo> commands = new CopyOnWriteArrayList<>();
    private volatile boolean needsRefresh = false;
    private CommandInfo selectedCommand = null;

    public CommandPanel(ComponentFactory factory, int x, int y, int width, int height, String serverUrl) {
        this(factory, x, y, width, height, new CommandService(serverUrl));
        refreshCommands();
    }

    public CommandPanel(ComponentFactory factory, int x, int y, int width, int height, CommandService commandService) {
        super(x, y, width, height);
        this.factory = factory;
        this.colours = factory.getColours();

        // Calculate layout - left side for list, right side for form
        int leftPanelWidth = width - FORM_PANEL_WIDTH - 20;

        // Create visual panel container (for the left side)
        this.visualPanel = factory.createPanel(x, y, leftPanelWidth, height);
        this.visualPanel.setTitle("Command Console");

        this.commandService = commandService;

        // Create status label
        statusLabel = factory.createLabel(x + 10, y + 35, "Ready", 12.0f);
        statusLabel.setTextColor(colours.textSecondary());

        // Create buttons row
        int buttonY = y + 55;
        int buttonWidth = 80;
        int buttonSpacing = 8;

        refreshButton = factory.createButton(x + 10, buttonY, buttonWidth, 28, "Refresh");
        refreshButton.setOnClick(this::refreshCommands);

        // Create command list (takes up remaining height)
        int listY = buttonY + 38;
        int listHeight = height - listY - 20 + y;
        commandList = factory.createListView(x + 10, listY, leftPanelWidth - 20, listHeight);
        commandList.setOnSelectionChanged(this::onCommandSelected);

        // Add components to visual panel
        visualPanel.addChild((WindowComponent) statusLabel);
        visualPanel.addChild((WindowComponent) refreshButton);
        visualPanel.addChild((WindowComponent) commandList);

        // Create form panel on the right side (Send button is inside the form panel)
        int formX = x + leftPanelWidth + 10;
        int formHeight = height;
        formPanel = new CommandFormPanel(factory, formX, y, FORM_PANEL_WIDTH, formHeight, this::sendCommand);

        // Setup service listener
        commandService.addListener(this::onCommandEvent);
    }

    /**
     * Refresh the command list.
     */
    public void refreshCommands() {
        setStatus("Loading commands...", colours.textSecondary());
        commandService.listCommands().thenAccept(commandInfos -> {
            commands.clear();
            commands.addAll(commandInfos);
            needsRefresh = true;
            setStatus("Loaded " + commandInfos.size() + " commands", colours.green());
        });
    }

    /**
     * Send the selected command.
     */
    private void sendCommand() {
        if (selectedCommand == null) {
            setStatus("Select a command first", colours.yellow());
            return;
        }

        // Get parameters from the form panel (matchId and entityId are now in the form)
        Map<String, Object> params = formPanel.getParameterValues();

        // Extract matchId from params if present, default to 1
        long matchId = 1;
        if (params.containsKey("matchId")) {
            Object matchIdObj = params.get("matchId");
            if (matchIdObj instanceof Number) {
                matchId = ((Number) matchIdObj).longValue();
            } else if (matchIdObj instanceof String str && !str.isEmpty()) {
                try {
                    matchId = Long.parseLong(str);
                } catch (NumberFormatException e) {
                    setStatus("Invalid matchId in parameters", colours.red());
                    return;
                }
            }
        }

        // Extract entityId from params if present, default to 0
        long entityId = 0;
        if (params.containsKey("entityId")) {
            Object entityIdObj = params.get("entityId");
            if (entityIdObj instanceof Number) {
                entityId = ((Number) entityIdObj).longValue();
            } else if (entityIdObj instanceof String str && !str.isEmpty()) {
                try {
                    entityId = Long.parseLong(str);
                } catch (NumberFormatException e) {
                    setStatus("Invalid entityId in parameters", colours.red());
                    return;
                }
            }
        }

        log.info("Sending command {} with matchId={}, entityId={}, params: {}",
                selectedCommand.name(), matchId, entityId, params);

        setStatus("Sending " + selectedCommand.name() + "...", colours.textSecondary());
        commandService.submitCommand(matchId, selectedCommand.name(), entityId, params)
                .thenAccept(success -> {
                    if (success) {
                        setStatus("Command sent: " + selectedCommand.name(), colours.green());
                    } else {
                        setStatus("Command failed", colours.red());
                    }
                });
    }

    private void onCommandSelected(int index) {
        if (index >= 0 && index < commands.size()) {
            selectedCommand = commands.get(index);
            // Update the form panel with the selected command
            formPanel.setCommand(selectedCommand);
            log.debug("Selected command: {} with {} parameters",
                    selectedCommand.name(), selectedCommand.parameters().size());
        } else {
            selectedCommand = null;
            formPanel.setCommand(null);
        }
    }

    private void onCommandEvent(CommandEvent event) {
        switch (event.type()) {
            case SUBMITTED -> setStatus("Sent: " + event.commandName(), colours.green());
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
            updateCommandList();
        }
    }

    private void updateCommandList() {
        List<String> items = new ArrayList<>();
        for (CommandInfo command : commands) {
            items.add(command.getSignature());
        }
        commandList.setItems(items);
    }

    /**
     * Cleanup resources.
     */
    public void dispose() {
        commandService.shutdown();
    }

    /**
     * Get the list of loaded commands.
     */
    public List<CommandInfo> getCommands() {
        return new ArrayList<>(commands);
    }

    /**
     * Get the selected command, if any.
     */
    public CommandInfo getSelectedCommand() {
        return selectedCommand;
    }

    /**
     * Get the command service.
     */
    public CommandService getCommandService() {
        return commandService;
    }

    /**
     * Select a command by index (for UI testing).
     */
    public void selectCommand(int index) {
        commandList.setSelectedIndex(index);
        onCommandSelected(index);
    }

    /**
     * Get the form panel for testing.
     */
    public CommandFormPanel getFormPanel() {
        return formPanel;
    }

    // Delegate rendering and input to both panels

    @Override
    public void render(long nvg) {
        if (visible) {
            visualPanel.render(nvg);
            formPanel.render(nvg);
        }
    }

    @Override
    public boolean onMouseClick(int mx, int my, int button, int action) {
        if (!visible) return false;

        // Check form panel first (it's on top visually on the right)
        if (formPanel.contains(mx, my)) {
            return formPanel.onMouseClick(mx, my, button, action);
        }

        return contains(mx, my) && visualPanel.onMouseClick(mx, my, button, action);
    }

    @Override
    public boolean onMouseMove(int mx, int my) {
        if (!visible) return false;
        formPanel.onMouseMove(mx, my);
        return visualPanel.onMouseMove(mx, my);
    }

    @Override
    public boolean onMouseScroll(int mx, int my, double scrollX, double scrollY) {
        if (!visible) return false;

        if (formPanel.contains(mx, my)) {
            return formPanel.onMouseScroll(mx, my, scrollX, scrollY);
        }

        return contains(mx, my) && visualPanel.onMouseScroll(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean onKeyPress(int key, int action) {
        return onKeyPress(key, action, 0);
    }

    @Override
    public boolean onKeyPress(int key, int action, int mods) {
        if (!visible) return false;
        // Try form panel first, then visual panel
        if (formPanel.onKeyPress(key, action, mods)) {
            return true;
        }
        return visualPanel.onKeyPress(key, action, mods);
    }

    @Override
    public boolean onCharInput(int codepoint) {
        if (!visible) return false;
        // Try form panel first, then visual panel
        if (formPanel.onCharInput(codepoint)) {
            return true;
        }
        return visualPanel.onCharInput(codepoint);
    }
}
