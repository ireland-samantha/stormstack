package com.lightningfirefly.engine.gui.panel;

import com.lightningfirefly.engine.gui.service.ModuleService;
import com.lightningfirefly.engine.gui.service.ModuleService.ModuleEvent;
import com.lightningfirefly.engine.gui.service.ModuleService.ModuleInfo;
import com.lightningfirefly.engine.rendering.render2d.*;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Panel for managing server modules.
 *
 * <p>Allows viewing installed modules, uploading new JAR modules,
 * and uninstalling existing modules.
 */
@Slf4j
public class ServerPanel extends AbstractWindowComponent {

    private final Panel visualPanel;
    private final ComponentFactory factory;
    private final ComponentFactory.Colours colours;
    private final ModuleService moduleService;
    private final ListView moduleList;
    private final Label statusLabel;
    private final Button refreshButton;
    private final Button uploadButton;
    private final Button uninstallButton;
    private final Button reloadButton;
    private final Button browseButton;
    private final TextField jarPathField;

    private final List<ModuleInfo> modules = new CopyOnWriteArrayList<>();
    private volatile boolean needsRefresh = false;

    public ServerPanel(ComponentFactory factory, int x, int y, int width, int height, String serverUrl) {
        this(factory, x, y, width, height, new ModuleService(serverUrl));
        refreshModules();
    }

    public ServerPanel(ComponentFactory factory, int x, int y, int width, int height, ModuleService moduleService) {
        super(x, y, width, height);
        this.factory = factory;
        this.colours = factory.getColours();

        // Create visual panel container
        this.visualPanel = factory.createPanel(x, y, width, height);
        this.visualPanel.setTitle("Server - Modules");

        this.moduleService = moduleService;

        // Create status label
        statusLabel = factory.createLabel(x + 10, y + 35, "Ready", 12.0f);
        statusLabel.setTextColor(colours.textSecondary());

        // Create buttons
        int buttonY = y + 55;
        int buttonWidth = 80;
        int buttonSpacing = 8;

        refreshButton = factory.createButton(x + 10, buttonY, buttonWidth, 28, "Refresh");
        refreshButton.setOnClick(this::refreshModules);

        uploadButton = factory.createButton(x + 10 + buttonWidth + buttonSpacing, buttonY, buttonWidth, 28, "Upload");
        uploadButton.setOnClick(this::uploadModule);

        uninstallButton = factory.createButton(x + 10 + (buttonWidth + buttonSpacing) * 2, buttonY, buttonWidth, 28, "Uninstall");
        uninstallButton.setOnClick(this::uninstallModule);

        reloadButton = factory.createButton(x + 10 + (buttonWidth + buttonSpacing) * 3, buttonY, buttonWidth, 28, "Reload All");
        reloadButton.setOnClick(this::reloadModules);

        browseButton = factory.createButton(x + 10 + (buttonWidth + buttonSpacing) * 4, buttonY, 70, 28, "Browse");
        browseButton.setOnClick(this::browseForJar);

        // Create JAR path input
        jarPathField = factory.createTextField(x + 10, buttonY + 38, width - 20, 28);
        jarPathField.setPlaceholder("Enter JAR path or click Browse...");

        // Create module list
        moduleList = factory.createListView(x + 10, buttonY + 76, width - 20, height - buttonY - 86 + y);

        // Add components to visual panel
        visualPanel.addChild((WindowComponent) statusLabel);
        visualPanel.addChild((WindowComponent) refreshButton);
        visualPanel.addChild((WindowComponent) uploadButton);
        visualPanel.addChild((WindowComponent) uninstallButton);
        visualPanel.addChild((WindowComponent) reloadButton);
        visualPanel.addChild((WindowComponent) browseButton);
        visualPanel.addChild((WindowComponent) jarPathField);
        visualPanel.addChild((WindowComponent) moduleList);

        // Setup module service listener
        moduleService.addListener(this::onModuleEvent);
    }

    /**
     * Refresh the module list.
     */
    public void refreshModules() {
        setStatus("Loading modules...", colours.textSecondary());
        moduleService.listModules().thenAccept(moduleInfos -> {
            modules.clear();
            modules.addAll(moduleInfos);
            needsRefresh = true;
            setStatus("Loaded " + moduleInfos.size() + " modules", colours.green());
        });
    }

    /**
     * Browse for a JAR file.
     */
    private void browseForJar() {
        Optional<Path> selectedFile = factory.openFileDialog(
            "Select Module JAR",
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
     * Upload a module from the JAR path field.
     */
    private void uploadModule() {
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

        setStatus("Uploading module...", colours.textSecondary());
        moduleService.uploadModule(path)
            .thenAccept(success -> {
                if (success) {
                    setStatus("Module uploaded successfully", colours.green());
                    refreshModules();
                } else {
                    setStatus("Upload failed", colours.red());
                }
            });
    }

    /**
     * Uninstall the selected module.
     */
    private void uninstallModule() {
        int selectedIndex = moduleList.getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= modules.size()) {
            setStatus("Select a module to uninstall", colours.yellow());
            return;
        }

        ModuleInfo module = modules.get(selectedIndex);
        setStatus("Uninstalling " + module.name() + "...", colours.textSecondary());
        moduleService.uninstallModule(module.name())
            .thenAccept(success -> {
                if (success) {
                    setStatus("Uninstalled " + module.name(), colours.green());
                    refreshModules();
                } else {
                    setStatus("Uninstall failed", colours.red());
                }
            });
    }

    /**
     * Reload all modules on the server.
     */
    private void reloadModules() {
        setStatus("Reloading modules...", colours.textSecondary());
        moduleService.reloadModules()
            .thenAccept(success -> {
                if (success) {
                    setStatus("Modules reloaded", colours.green());
                    refreshModules();
                } else {
                    setStatus("Reload failed", colours.red());
                }
            });
    }

    private void onModuleEvent(ModuleEvent event) {
        switch (event.type()) {
            case UPLOADED -> setStatus("Uploaded: " + event.message(), colours.green());
            case UNINSTALLED -> setStatus("Uninstalled: " + event.moduleName(), colours.green());
            case RELOADED -> setStatus("Modules reloaded", colours.green());
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
            updateModuleList();
        }
    }

    private void updateModuleList() {
        List<String> items = new ArrayList<>();
        for (ModuleInfo module : modules) {
            String item = module.name();
            if (module.flagComponent() != null) {
                item += " [" + module.flagComponent() + "]";
            }
            if (module.enabledMatches() > 0) {
                item += " (" + module.enabledMatches() + " matches)";
            }
            items.add(item);
        }
        moduleList.setItems(items);
    }

    /**
     * Cleanup resources.
     */
    public void dispose() {
        moduleService.shutdown();
    }

    /**
     * Get the list of loaded modules.
     */
    public List<ModuleInfo> getModules() {
        return new ArrayList<>(modules);
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
