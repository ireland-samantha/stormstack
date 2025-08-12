package com.lightningfirefly.engine.gui.panel;

import com.lightningfirefly.engine.gui.service.GameMasterService;
import com.lightningfirefly.engine.gui.service.GameMasterService.GameMasterEvent;
import com.lightningfirefly.engine.gui.service.GameMasterService.GameMasterInfo;
import com.lightningfirefly.engine.rendering.render2d.*;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Panel for managing game masters.
 *
 * <p>Allows viewing installed game masters, uploading new JAR game masters,
 * and uninstalling existing game masters.
 */
@Slf4j
public class GameMasterPanel extends AbstractWindowComponent {

    private final Panel visualPanel;
    private final ComponentFactory factory;
    private final ComponentFactory.Colours colours;
    private final GameMasterService gameMasterService;
    private final ListView gameMasterList;
    private final Label statusLabel;
    private final Button refreshButton;
    private final Button uploadButton;
    private final Button uninstallButton;
    private final Button reloadButton;
    private final Button browseButton;
    private final TextField jarPathField;

    private final List<GameMasterInfo> gameMasters = new CopyOnWriteArrayList<>();
    private volatile boolean needsRefresh = false;
    private volatile String statusMessage = "";

    public GameMasterPanel(ComponentFactory factory, int x, int y, int width, int height, String serverUrl) {
        this(factory, x, y, width, height, new GameMasterService(serverUrl));
        refreshGameMasters();
    }

    public GameMasterPanel(ComponentFactory factory, int x, int y, int width, int height, GameMasterService gameMasterService) {
        super(x, y, width, height);
        this.factory = factory;
        this.colours = factory.getColours();

        // Create visual panel container
        this.visualPanel = factory.createPanel(x, y, width, height);
        this.visualPanel.setTitle("Game Masters");

        this.gameMasterService = gameMasterService;

        // Create status label
        statusLabel = factory.createLabel(x + 10, y + 35, "Ready", 12.0f);
        statusLabel.setTextColor(colours.textSecondary());

        // Create buttons
        int buttonY = y + 55;
        int buttonWidth = 80;
        int buttonSpacing = 8;

        refreshButton = factory.createButton(x + 10, buttonY, buttonWidth, 28, "Refresh");
        refreshButton.setOnClick(this::refreshGameMasters);

        uploadButton = factory.createButton(x + 10 + buttonWidth + buttonSpacing, buttonY, buttonWidth, 28, "Upload");
        uploadButton.setOnClick(this::uploadGameMaster);

        uninstallButton = factory.createButton(x + 10 + (buttonWidth + buttonSpacing) * 2, buttonY, buttonWidth, 28, "Uninstall");
        uninstallButton.setOnClick(this::uninstallGameMaster);

        reloadButton = factory.createButton(x + 10 + (buttonWidth + buttonSpacing) * 3, buttonY, buttonWidth, 28, "Reload All");
        reloadButton.setOnClick(this::reloadGameMasters);

        browseButton = factory.createButton(x + 10 + (buttonWidth + buttonSpacing) * 4, buttonY, 70, 28, "Browse");
        browseButton.setOnClick(this::browseForJar);

        // Create JAR path input
        jarPathField = factory.createTextField(x + 10, buttonY + 38, width - 20, 28);
        jarPathField.setPlaceholder("Enter JAR path or click Browse...");

        // Create game master list
        gameMasterList = factory.createListView(x + 10, buttonY + 76, width - 20, height - buttonY - 86 + y);

        // Add components to visual panel
        visualPanel.addChild((WindowComponent) statusLabel);
        visualPanel.addChild((WindowComponent) refreshButton);
        visualPanel.addChild((WindowComponent) uploadButton);
        visualPanel.addChild((WindowComponent) uninstallButton);
        visualPanel.addChild((WindowComponent) reloadButton);
        visualPanel.addChild((WindowComponent) browseButton);
        visualPanel.addChild((WindowComponent) jarPathField);
        visualPanel.addChild((WindowComponent) gameMasterList);

        // Setup game master service listener
        gameMasterService.addListener(this::onGameMasterEvent);
    }

    /**
     * Refresh the game master list.
     */
    public void refreshGameMasters() {
        setStatus("Loading game masters...", colours.textSecondary());
        gameMasterService.listGameMasters().thenAccept(gameMasterInfos -> {
            gameMasters.clear();
            gameMasters.addAll(gameMasterInfos);
            needsRefresh = true;
            setStatus("Loaded " + gameMasterInfos.size() + " game masters", colours.green());
        });
    }

    /**
     * Browse for a JAR file.
     */
    private void browseForJar() {
        Optional<Path> selectedFile = factory.openFileDialog(
            "Select Game Master JAR",
            System.getProperty("user.home"),
            "*.jar",
            "JAR files (*.jar)"
        );

        selectedFile.ifPresent(path -> {
            jarPathField.setText(path.toAbsolutePath().toString());
            setStatus("Selected: " + path.getFileName(), colours.textPrimary());
        });
    }

    /**
     * Upload a game master from the JAR path field.
     */
    private void uploadGameMaster() {
        String jarPath = jarPathField.getText().trim();
        if (jarPath.isEmpty()) {
            setStatus("Enter a JAR path to upload", colours.yellow());
            return;
        }

        Path path = Path.of(jarPath);
        if (!java.nio.file.Files.exists(path)) {
            setStatus("File not found: " + jarPath, colours.red());
            return;
        }

        if (!jarPath.endsWith(".jar")) {
            setStatus("File must be a JAR file", colours.yellow());
            return;
        }

        setStatus("Uploading game master...", colours.textSecondary());
        gameMasterService.uploadGameMaster(path)
            .thenAccept(success -> {
                if (success) {
                    setStatus("Game master uploaded successfully", colours.green());
                    refreshGameMasters();
                } else {
                    setStatus("Upload failed", colours.red());
                }
            });
    }

    /**
     * Uninstall the selected game master.
     */
    private void uninstallGameMaster() {
        int selectedIndex = gameMasterList.getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= gameMasters.size()) {
            setStatus("Select a game master to uninstall", colours.yellow());
            return;
        }

        GameMasterInfo gameMaster = gameMasters.get(selectedIndex);
        setStatus("Uninstalling " + gameMaster.name() + "...", colours.textSecondary());
        gameMasterService.uninstallGameMaster(gameMaster.name())
            .thenAccept(success -> {
                if (success) {
                    setStatus("Uninstalled " + gameMaster.name(), colours.green());
                    refreshGameMasters();
                } else {
                    setStatus("Uninstall failed", colours.red());
                }
            });
    }

    /**
     * Reload all game masters on the server.
     */
    private void reloadGameMasters() {
        setStatus("Reloading game masters...", colours.textSecondary());
        gameMasterService.reloadGameMasters()
            .thenAccept(success -> {
                if (success) {
                    setStatus("Game masters reloaded", colours.green());
                    refreshGameMasters();
                } else {
                    setStatus("Reload failed", colours.red());
                }
            });
    }

    private void onGameMasterEvent(GameMasterEvent event) {
        switch (event.type()) {
            case UPLOADED -> setStatus("Uploaded: " + event.message(), colours.green());
            case UNINSTALLED -> setStatus("Uninstalled: " + event.gameMasterName(), colours.green());
            case RELOADED -> setStatus("Game masters reloaded", colours.green());
            case ERROR -> setStatus("Error: " + event.message(), colours.red());
        }
    }

    private void setStatus(String message, float[] color) {
        statusMessage = message;
        statusLabel.setText(message);
        statusLabel.setTextColor(color);
    }

    /**
     * Update the panel. Call this from the render loop.
     */
    public void update() {
        if (needsRefresh) {
            needsRefresh = false;
            updateGameMasterList();
        }
    }

    private void updateGameMasterList() {
        List<String> items = new ArrayList<>();
        for (GameMasterInfo gameMaster : gameMasters) {
            String item = gameMaster.name();
            if (gameMaster.enabledMatches() > 0) {
                item += " (" + gameMaster.enabledMatches() + " matches)";
            }
            items.add(item);
        }
        gameMasterList.setItems(items);
    }

    /**
     * Cleanup resources.
     */
    public void dispose() {
        gameMasterService.shutdown();
    }

    /**
     * Get the list of loaded game masters.
     */
    public List<GameMasterInfo> getGameMasters() {
        return new ArrayList<>(gameMasters);
    }

    /**
     * Get the game master service.
     */
    public GameMasterService getGameMasterService() {
        return gameMasterService;
    }

    /**
     * Get the status message (for testing).
     */
    public String getStatusMessage() {
        return statusMessage;
    }

    // ========== Test Helper Methods ==========

    /**
     * Set the JAR path field text (for testing).
     */
    public void setJarPath(String path) {
        jarPathField.setText(path);
    }

    /**
     * Get the JAR path field text (for testing).
     */
    public String getJarPath() {
        return jarPathField.getText();
    }

    /**
     * Trigger upload of the JAR in the path field (for testing).
     */
    public void uploadSelectedJar() {
        uploadGameMaster();
    }

    /**
     * Select a game master by index (for testing).
     */
    public void selectGameMaster(int index) {
        gameMasterList.setSelectedIndex(index);
    }

    /**
     * Get the selected game master index (for testing).
     */
    public int getSelectedGameMasterIndex() {
        return gameMasterList.getSelectedIndex();
    }

    /**
     * Trigger uninstall of the selected game master (for testing).
     */
    public void uninstallSelectedGameMaster() {
        uninstallGameMaster();
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
