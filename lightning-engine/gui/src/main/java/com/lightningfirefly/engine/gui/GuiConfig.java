package com.lightningfirefly.engine.gui;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Configuration for the Lightning Engine GUI application.
 *
 * <p>Configuration sources (in order of precedence):
 * <ol>
 *   <li>System properties (-D flags)</li>
 *   <li>Environment variables</li>
 *   <li>gui.properties file in current directory</li>
 *   <li>gui.properties on classpath</li>
 *   <li>Default values</li>
 * </ol>
 */
@Slf4j
@Getter
public class GuiConfig {

    private static final String CONFIG_FILE_NAME = "gui.properties";

    // Default values
    private static final int DEFAULT_WINDOW_WIDTH = 1200;
    private static final int DEFAULT_WINDOW_HEIGHT = 800;
    private static final String DEFAULT_WINDOW_TITLE = "Lightning Engine GUI";
    private static final String DEFAULT_SERVER_URL = "http://localhost:8080";
    private static final long DEFAULT_MATCH_ID = 1;
    private static final int DEFAULT_HTTP_TIMEOUT_SECONDS = 30;

    private final int windowWidth;
    private final int windowHeight;
    private final String windowTitle;
    private final String serverUrl;
    private final long matchId;
    private final int httpTimeoutSeconds;

    private GuiConfig(int windowWidth, int windowHeight, String windowTitle,
                      String serverUrl, long matchId, int httpTimeoutSeconds) {
        this.windowWidth = windowWidth;
        this.windowHeight = windowHeight;
        this.windowTitle = windowTitle;
        this.serverUrl = serverUrl;
        this.matchId = matchId;
        this.httpTimeoutSeconds = httpTimeoutSeconds;
    }

    /**
     * Load configuration with default values.
     */
    public static GuiConfig load() {
        Properties props = loadProperties();
        return new GuiConfig(
                getInt(props, "gui.window.width", DEFAULT_WINDOW_WIDTH),
                getInt(props, "gui.window.height", DEFAULT_WINDOW_HEIGHT),
                getString(props, "gui.window.title", DEFAULT_WINDOW_TITLE),
                getString(props, "gui.server.url", DEFAULT_SERVER_URL),
                getLong(props, "gui.server.match-id", DEFAULT_MATCH_ID),
                getInt(props, "gui.http.timeout-seconds", DEFAULT_HTTP_TIMEOUT_SECONDS)
        );
    }

    /**
     * Create configuration with specific server URL and match ID (for CLI override).
     */
    public static GuiConfig load(String serverUrl, long matchId) {
        Properties props = loadProperties();
        return new GuiConfig(
                getInt(props, "gui.window.width", DEFAULT_WINDOW_WIDTH),
                getInt(props, "gui.window.height", DEFAULT_WINDOW_HEIGHT),
                getString(props, "gui.window.title", DEFAULT_WINDOW_TITLE),
                serverUrl != null ? serverUrl : getString(props, "gui.server.url", DEFAULT_SERVER_URL),
                matchId > 0 ? matchId : getLong(props, "gui.server.match-id", DEFAULT_MATCH_ID),
                getInt(props, "gui.http.timeout-seconds", DEFAULT_HTTP_TIMEOUT_SECONDS)
        );
    }

    private static Properties loadProperties() {
        Properties props = new Properties();

        // Try to load from file in current directory
        Path configPath = Path.of(CONFIG_FILE_NAME);
        if (Files.exists(configPath)) {
            try (InputStream is = Files.newInputStream(configPath)) {
                props.load(is);
                log.debug("Loaded configuration from {}", configPath.toAbsolutePath());
            } catch (IOException e) {
                log.warn("Failed to load config from {}: {}", configPath, e.getMessage());
            }
        } else {
            // Try to load from classpath
            try (InputStream is = GuiConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE_NAME)) {
                if (is != null) {
                    props.load(is);
                    log.debug("Loaded configuration from classpath");
                }
            } catch (IOException e) {
                log.warn("Failed to load config from classpath: {}", e.getMessage());
            }
        }

        return props;
    }

    private static String getString(Properties props, String key, String defaultValue) {
        // System property takes precedence
        String sysProp = System.getProperty(key);
        if (sysProp != null) {
            return sysProp;
        }

        // Environment variable (convert key to ENV_VAR format)
        String envKey = key.toUpperCase().replace('.', '_').replace('-', '_');
        String envValue = System.getenv(envKey);
        if (envValue != null) {
            return envValue;
        }

        // Properties file
        return props.getProperty(key, defaultValue);
    }

    private static int getInt(Properties props, String key, int defaultValue) {
        String value = getString(props, key, null);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                log.warn("Invalid integer value for {}: {}", key, value);
            }
        }
        return defaultValue;
    }

    private static long getLong(Properties props, String key, long defaultValue) {
        String value = getString(props, key, null);
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                log.warn("Invalid long value for {}: {}", key, value);
            }
        }
        return defaultValue;
    }

    @Override
    public String toString() {
        return "GuiConfig{" +
                "windowWidth=" + windowWidth +
                ", windowHeight=" + windowHeight +
                ", windowTitle='" + windowTitle + '\'' +
                ", serverUrl='" + serverUrl + '\'' +
                ", matchId=" + matchId +
                ", httpTimeoutSeconds=" + httpTimeoutSeconds +
                '}';
    }
}
