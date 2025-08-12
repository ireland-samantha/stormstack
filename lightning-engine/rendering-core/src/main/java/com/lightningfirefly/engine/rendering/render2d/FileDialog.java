package com.lightningfirefly.engine.rendering.render2d;

import java.io.File;
import java.util.Optional;

/**
 * Interface for file selection dialogs.
 */
public interface FileDialog {

    /**
     * Show an open file dialog.
     *
     * @param title the dialog title
     * @param defaultPath the default path to open
     * @param filters file filters (e.g., "*.txt", "*.png")
     * @return the selected file, or empty if cancelled
     */
    Optional<File> showOpenDialog(String title, String defaultPath, String... filters);

    /**
     * Show a save file dialog.
     *
     * @param title the dialog title
     * @param defaultPath the default path to save to
     * @param filters file filters (e.g., "*.txt", "*.png")
     * @return the selected file, or empty if cancelled
     */
    Optional<File> showSaveDialog(String title, String defaultPath, String... filters);

    /**
     * Show a folder selection dialog.
     *
     * @param title the dialog title
     * @param defaultPath the default path to open
     * @return the selected folder, or empty if cancelled
     */
    Optional<File> showFolderDialog(String title, String defaultPath);
}
