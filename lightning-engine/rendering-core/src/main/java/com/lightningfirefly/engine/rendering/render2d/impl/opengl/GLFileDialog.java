package com.lightningfirefly.engine.rendering.render2d.impl.opengl;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Utility class for native file dialogs using TinyFileDialogs.
 */
public final class GLFileDialog {

    private GLFileDialog() {
        // Utility class
    }

    /**
     * Show an open file dialog.
     *
     * @param title the dialog title
     * @param defaultPath the default path to start in (can be null)
     * @param filterPatterns file filter patterns (e.g., "*.png", "*.jpg")
     * @param filterDescription description of the filter (e.g., "Image files")
     * @return the selected file path, or empty if cancelled
     */
    public static Optional<Path> openFile(String title, String defaultPath,
                                          String[] filterPatterns, String filterDescription) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = null;
            if (filterPatterns != null && filterPatterns.length > 0) {
                filters = stack.mallocPointer(filterPatterns.length);
                for (String pattern : filterPatterns) {
                    filters.put(stack.UTF8(pattern));
                }
                filters.flip();
            }

            String result = TinyFileDialogs.tinyfd_openFileDialog(
                title,
                defaultPath,
                filters,
                filterDescription,
                false // single selection
            );

            if (result != null && !result.isEmpty()) {
                return Optional.of(Path.of(result));
            }
            return Optional.empty();
        }
    }

    /**
     * Show an open file dialog for multiple files.
     *
     * @param title the dialog title
     * @param defaultPath the default path to start in (can be null)
     * @param filterPatterns file filter patterns (e.g., "*.png", "*.jpg")
     * @param filterDescription description of the filter (e.g., "Image files")
     * @return array of selected file paths, or empty array if cancelled
     */
    public static Path[] openFiles(String title, String defaultPath,
                                   String[] filterPatterns, String filterDescription) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = null;
            if (filterPatterns != null && filterPatterns.length > 0) {
                filters = stack.mallocPointer(filterPatterns.length);
                for (String pattern : filterPatterns) {
                    filters.put(stack.UTF8(pattern));
                }
                filters.flip();
            }

            String result = TinyFileDialogs.tinyfd_openFileDialog(
                title,
                defaultPath,
                filters,
                filterDescription,
                true // multiple selection
            );

            if (result != null && !result.isEmpty()) {
                // Multiple files are separated by |
                String[] paths = result.split("\\|");
                Path[] filePaths = new Path[paths.length];
                for (int i = 0; i < paths.length; i++) {
                    filePaths[i] = Path.of(paths[i]);
                }
                return filePaths;
            }
            return new Path[0];
        }
    }

    /**
     * Show a save file dialog.
     *
     * @param title the dialog title
     * @param defaultPath the default path/filename (can be null)
     * @param filterPatterns file filter patterns (e.g., "*.png", "*.jpg")
     * @param filterDescription description of the filter (e.g., "Image files")
     * @return the selected file path, or empty if cancelled
     */
    public static Optional<Path> saveFile(String title, String defaultPath,
                                          String[] filterPatterns, String filterDescription) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = null;
            if (filterPatterns != null && filterPatterns.length > 0) {
                filters = stack.mallocPointer(filterPatterns.length);
                for (String pattern : filterPatterns) {
                    filters.put(stack.UTF8(pattern));
                }
                filters.flip();
            }

            String result = TinyFileDialogs.tinyfd_saveFileDialog(
                title,
                defaultPath,
                filters,
                filterDescription
            );

            if (result != null && !result.isEmpty()) {
                return Optional.of(Path.of(result));
            }
            return Optional.empty();
        }
    }

    /**
     * Show a folder selection dialog.
     *
     * @param title the dialog title
     * @param defaultPath the default path to start in (can be null)
     * @return the selected folder path, or empty if cancelled
     */
    public static Optional<Path> selectFolder(String title, String defaultPath) {
        String result = TinyFileDialogs.tinyfd_selectFolderDialog(title, defaultPath);
        if (result != null && !result.isEmpty()) {
            return Optional.of(Path.of(result));
        }
        return Optional.empty();
    }

    /**
     * Show a message box.
     *
     * @param title the dialog title
     * @param message the message to display
     * @param dialogType "ok", "okcancel", "yesno", or "yesnocancel"
     * @param iconType "info", "warning", "error", or "question"
     * @param defaultOkYes true for ok/yes as default, false for cancel/no
     * @return true if ok/yes was clicked, false otherwise
     */
    public static boolean messageBox(String title, String message,
                                     String dialogType, String iconType, boolean defaultOkYes) {
        return TinyFileDialogs.tinyfd_messageBox(title, message, dialogType, iconType, defaultOkYes);
    }

    /**
     * Show an info message box with OK button.
     */
    public static void showInfo(String title, String message) {
        TinyFileDialogs.tinyfd_messageBox(title, message, "ok", "info", true);
    }

    /**
     * Show a warning message box with OK button.
     */
    public static void showWarning(String title, String message) {
        TinyFileDialogs.tinyfd_messageBox(title, message, "ok", "warning", true);
    }

    /**
     * Show an error message box with OK button.
     */
    public static void showError(String title, String message) {
        TinyFileDialogs.tinyfd_messageBox(title, message, "ok", "error", true);
    }

    /**
     * Show a confirmation dialog with Yes/No buttons.
     *
     * @return true if Yes was clicked
     */
    public static boolean confirm(String title, String message) {
        return TinyFileDialogs.tinyfd_messageBox(title, message, "yesno", "question", true);
    }

    /**
     * Common file filter patterns.
     */
    public static class Filters {
        public static final String[] ALL = {"*"};
        public static final String[] IMAGES = {"*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.tga"};
        public static final String[] TEXTURES = {"*.png", "*.jpg", "*.jpeg", "*.tga", "*.dds"};
        public static final String[] AUDIO = {"*.wav", "*.mp3", "*.ogg", "*.flac"};
        public static final String[] JSON = {"*.json"};
        public static final String[] XML = {"*.xml"};
        public static final String[] TEXT = {"*.txt", "*.md", "*.log"};
    }
}
