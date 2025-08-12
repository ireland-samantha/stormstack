package com.lightningfirefly.engine.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GuiConfig}.
 */
@DisplayName("GuiConfig")
class GuiConfigTest {

    @Nested
    @DisplayName("load()")
    class Load {

        @Test
        @DisplayName("should use default values when no config is provided")
        void shouldUseDefaultValues() {
            GuiConfig config = GuiConfig.load();

            assertThat(config.getWindowWidth()).isEqualTo(1200);
            assertThat(config.getWindowHeight()).isEqualTo(800);
            assertThat(config.getWindowTitle()).isEqualTo("Lightning Engine GUI");
            assertThat(config.getServerUrl()).isEqualTo("http://localhost:8080");
            assertThat(config.getMatchId()).isEqualTo(1L);
            assertThat(config.getHttpTimeoutSeconds()).isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("load(serverUrl, matchId)")
    class LoadWithOverrides {

        @Test
        @DisplayName("should use provided server URL")
        void shouldUseProvidedServerUrl() {
            GuiConfig config = GuiConfig.load("http://example.com:9090", 0);

            assertThat(config.getServerUrl()).isEqualTo("http://example.com:9090");
        }

        @Test
        @DisplayName("should use provided match ID")
        void shouldUseProvidedMatchId() {
            GuiConfig config = GuiConfig.load(null, 42);

            assertThat(config.getMatchId()).isEqualTo(42L);
        }

        @Test
        @DisplayName("should use defaults when null/zero provided")
        void shouldUseDefaultsWhenNullOrZeroProvided() {
            GuiConfig config = GuiConfig.load(null, 0);

            assertThat(config.getServerUrl()).isEqualTo("http://localhost:8080");
            assertThat(config.getMatchId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should retain window defaults when only server/match overridden")
        void shouldRetainWindowDefaults() {
            GuiConfig config = GuiConfig.load("http://test:8000", 99);

            assertThat(config.getWindowWidth()).isEqualTo(1200);
            assertThat(config.getWindowHeight()).isEqualTo(800);
            assertThat(config.getWindowTitle()).isEqualTo("Lightning Engine GUI");
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToString {

        @Test
        @DisplayName("should include all config values")
        void shouldIncludeAllConfigValues() {
            GuiConfig config = GuiConfig.load("http://test:1234", 55);

            String str = config.toString();

            assertThat(str).contains("windowWidth=1200");
            assertThat(str).contains("windowHeight=800");
            assertThat(str).contains("serverUrl='http://test:1234'");
            assertThat(str).contains("matchId=55");
        }
    }
}
