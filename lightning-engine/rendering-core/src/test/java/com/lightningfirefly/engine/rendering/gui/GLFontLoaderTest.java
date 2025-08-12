package com.lightningfirefly.engine.rendering.gui;

import com.lightningfirefly.engine.rendering.render2d.impl.opengl.GLFontLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GLFontLoader}.
 *
 * <p>These tests verify the font loading logic without requiring
 * an actual NanoVG context (which requires OpenGL).
 */
@DisplayName("FontLoader")
class GLFontLoaderTest {

    // Use 0 as a mock context since we're testing non-NanoVG functionality
    private static final long MOCK_NVG_CONTEXT = 0;

    private GLFontLoader fontLoader;

    @BeforeEach
    void setUp() {
        fontLoader = new GLFontLoader(MOCK_NVG_CONTEXT);
    }

    @Nested
    @DisplayName("getSystemFontPaths")
    class GetSystemFontPaths {

        @Test
        @DisplayName("should return non-empty array of font paths")
        void shouldReturnNonEmptyArray() {
            String[] paths = fontLoader.getSystemFontPaths();

            assertThat(paths).isNotEmpty();
        }

        @Test
        @DisplayName("should return paths with ttf extension")
        void shouldReturnTtfPaths() {
            String[] paths = fontLoader.getSystemFontPaths();

            for (String path : paths) {
                assertThat(path).endsWith(".ttf");
            }
        }

        @Test
        @DisplayName("should return OS-specific paths")
        void shouldReturnOsSpecificPaths() {
            String[] paths = fontLoader.getSystemFontPaths();
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("mac")) {
                assertThat(paths[0]).contains("/System/Library/Fonts");
            } else if (os.contains("win")) {
                assertThat(paths[0]).contains("Windows\\Fonts");
            } else {
                assertThat(paths[0]).contains("/usr/share/fonts");
            }
        }
    }

    @Nested
    @DisplayName("loadResourceToBuffer")
    class LoadResourceToBuffer {

        @Test
        @DisplayName("should return null for non-existent resource")
        void shouldReturnNullForNonExistentResource() {
            ByteBuffer result = fontLoader.loadResourceToBuffer("non-existent-resource.ttf");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null for null path")
        void shouldReturnNullForNullPath() {
            // This tests the graceful handling of null input
            ByteBuffer result = fontLoader.loadResourceToBuffer(null);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("loadBundledFont")
    class LoadBundledFont {

        @Test
        @DisplayName("should return -1 when bundled font is missing")
        void shouldReturnNegativeOneWhenBundledFontMissing() {
            // Without a real NanoVG context, the font creation will fail
            // but this tests that the method handles missing resources gracefully
            int fontId = fontLoader.loadBundledFont();

            // Will be -1 because either the font doesn't exist or NanoVG context is invalid
            assertThat(fontId).isLessThan(0);
        }
    }

    @Nested
    @DisplayName("loadSystemFont")
    class LoadSystemFont {

        @Test
        @DisplayName("should return -1 without valid NanoVG context")
        void shouldReturnNegativeOneWithoutValidContext() {
            // Without a real NanoVG context, system font loading will fail
            int fontId = fontLoader.loadSystemFont();

            assertThat(fontId).isLessThan(0);
        }
    }

    @Nested
    @DisplayName("loadDefaultFont")
    class LoadDefaultFont {

        @Test
        @DisplayName("should return -1 without valid NanoVG context")
        void shouldReturnNegativeOneWithoutValidContext() {
            // Without a real NanoVG context, font loading will fail
            int fontId = fontLoader.loadDefaultFont();

            assertThat(fontId).isLessThan(0);
        }
    }

    @Nested
    @DisplayName("loadFontFromResource")
    class LoadFontFromResource {

        @Test
        @DisplayName("should return -1 for non-existent resource")
        void shouldReturnNegativeOneForNonExistentResource() {
            int fontId = fontLoader.loadFontFromResource("test", "non-existent.ttf");

            assertThat(fontId).isLessThan(0);
        }
    }

    @Nested
    @DisplayName("loadFontFromFile")
    class LoadFontFromFile {

        @Test
        @DisplayName("should return -1 for non-existent file")
        void shouldReturnNegativeOneForNonExistentFile() {
            int fontId = fontLoader.loadFontFromFile("test", "/non/existent/path.ttf");

            assertThat(fontId).isLessThan(0);
        }
    }
}
