package com.lightningfirefly.game.app;

import com.lightningfirefly.game.backend.installation.GameFactory;
import com.lightningfirefly.game.domain.GameScene;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for GameFactory JAR loading.
 */
@DisplayName("GameFactory JAR Loading")
class GameFactoryJarLoaderTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("loadGameFactoryFromJar")
    class LoadGameFactoryFromJar {

        @Test
        @DisplayName("should load GameFactory using Game-Factory-Class manifest attribute")
        void shouldLoadViaManifestAttribute() throws Exception {
            // Create a test JAR with manifest
            Path jarPath = createTestJar(
                    "Game-Factory-Class",
                    TestGameFactory.class.getName()
            );

            GameFactoryJarLoader loader = new GameFactoryJarLoader();
            GameFactory factory = loader.loadFromJar(jarPath);

            assertThat(factory).isNotNull();
            assertThat(factory).isInstanceOf(TestGameFactory.class);
        }

        @Test
        @DisplayName("should read manifest attribute correctly")
        void shouldReadManifestAttributeCorrectly() throws Exception {
            String expectedClass = "com.example.MyGameFactory";
            Path jarPath = createTestJarWithEmptyClass("Game-Factory-Class", expectedClass);

            // Verify manifest
            try (JarFile jarFile = new JarFile(jarPath.toFile())) {
                Manifest manifest = jarFile.getManifest();
                assertThat(manifest).isNotNull();
                String actualClass = manifest.getMainAttributes().getValue("Game-Factory-Class");
                assertThat(actualClass).isEqualTo(expectedClass);
            }
        }

        @Test
        @DisplayName("should throw when no GameFactory found")
        void shouldThrowWhenNoGameFactoryFound() throws Exception {
            // Create JAR without manifest attribute
            Path jarPath = createEmptyJar();

            GameFactoryJarLoader loader = new GameFactoryJarLoader();

            assertThatThrownBy(() -> loader.loadFromJar(jarPath))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No GameFactory found in JAR");
        }

        @Test
        @DisplayName("should throw when class not found")
        void shouldThrowWhenClassNotFound() throws Exception {
            // Create JAR with manifest pointing to non-existent class
            Path jarPath = createTestJarWithEmptyClass(
                    "Game-Factory-Class",
                    "com.example.NonExistentFactory"
            );

            GameFactoryJarLoader loader = new GameFactoryJarLoader();

            assertThatThrownBy(() -> loader.loadFromJar(jarPath))
                    .isInstanceOf(Exception.class);
        }
    }

    // ========== Helper Methods ==========

    private Path createTestJar(String manifestKey, String manifestValue) throws Exception {
        Path jarPath = tempDir.resolve("test-game.jar");

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue(manifestKey, manifestValue);

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()), manifest)) {
            // Add the test class to the JAR
            String classPath = TestGameFactory.class.getName().replace('.', '/') + ".class";
            jos.putNextEntry(new JarEntry(classPath));

            // Get the class bytes
            byte[] classBytes = getClassBytes(TestGameFactory.class);
            jos.write(classBytes);
            jos.closeEntry();
        }

        return jarPath;
    }

    private Path createTestJarWithEmptyClass(String manifestKey, String manifestValue) throws Exception {
        Path jarPath = tempDir.resolve("test-game.jar");

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue(manifestKey, manifestValue);

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()), manifest)) {
            // Just create JAR with manifest, no class files
            jos.putNextEntry(new JarEntry("dummy.txt"));
            jos.write("test".getBytes());
            jos.closeEntry();
        }

        return jarPath;
    }

    private Path createEmptyJar() throws Exception {
        Path jarPath = tempDir.resolve("empty-game.jar");

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()), manifest)) {
            // Just create empty JAR with manifest
            jos.putNextEntry(new JarEntry("dummy.txt"));
            jos.write("test".getBytes());
            jos.closeEntry();
        }

        return jarPath;
    }

    private byte[] getClassBytes(Class<?> clazz) throws Exception {
        String resourcePath = "/" + clazz.getName().replace('.', '/') + ".class";
        try (var is = clazz.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Cannot find class resource: " + resourcePath);
            }
            return is.readAllBytes();
        }
    }

    // ========== Test GameFactory ==========

    /**
     * Test GameFactory for JAR loading tests.
     * Must be public with a no-arg constructor.
     */
    public static class TestGameFactory implements GameFactory {
        @Override
        public void attachScene(GameScene scene) {
            // Empty
        }

        @Override
        public List<String> getRequiredModules() {
            return List.of();
        }
    }
}
