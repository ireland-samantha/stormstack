/*
 * Copyright (c) 2026 Samantha Ireland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ca.samanthaireland.stormstack.thunder.controlplane.provider.filesystem;

import ca.samanthaireland.stormstack.thunder.controlplane.module.model.ModuleMetadata;
import ca.samanthaireland.stormstack.thunder.controlplane.provider.config.QuarkusModuleStorageConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class FileSystemModuleRepositoryTest {

    @TempDir
    Path tempDir;

    private FileSystemModuleRepository repository;

    private static final String MODULE_NAME = "entity-module";
    private static final String MODULE_VERSION = "1.0.0";

    @BeforeEach
    void setUp() {
        QuarkusModuleStorageConfig config = new TestModuleStorageConfig(tempDir.toString());
        repository = new FileSystemModuleRepository(config);
    }

    private ModuleMetadata createTestMetadata(String name, String version) {
        return new ModuleMetadata(
                name,
                version,
                "Test module description",
                name + "-" + version + ".jar",
                1024L,
                "sha256:abc123def456",
                Instant.now(),
                "test-user"
        );
    }

    private byte[] createTestJarData(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    @Nested
    class Save {

        @Test
        void save_createsDirectoryStructure() throws IOException {
            // Arrange
            ModuleMetadata metadata = createTestMetadata(MODULE_NAME, MODULE_VERSION);
            byte[] jarData = createTestJarData("fake jar content");

            // Act
            repository.save(metadata, jarData);

            // Assert
            Path moduleDir = tempDir.resolve(MODULE_NAME).resolve(MODULE_VERSION);
            assertThat(Files.exists(moduleDir)).isTrue();
            assertThat(Files.exists(moduleDir.resolve("metadata.json"))).isTrue();
            assertThat(Files.exists(moduleDir.resolve("module.jar"))).isTrue();
        }

        @Test
        void save_writesCorrectJarContent() throws IOException {
            // Arrange
            ModuleMetadata metadata = createTestMetadata(MODULE_NAME, MODULE_VERSION);
            byte[] jarData = createTestJarData("test jar content for verification");

            // Act
            repository.save(metadata, jarData);

            // Assert
            Path jarPath = tempDir.resolve(MODULE_NAME).resolve(MODULE_VERSION).resolve("module.jar");
            byte[] storedData = Files.readAllBytes(jarPath);
            assertThat(storedData).isEqualTo(jarData);
        }

        @Test
        void save_writesMetadataJson() throws IOException {
            // Arrange
            ModuleMetadata metadata = createTestMetadata(MODULE_NAME, MODULE_VERSION);
            byte[] jarData = createTestJarData("jar");

            // Act
            repository.save(metadata, jarData);

            // Assert
            Path metadataPath = tempDir.resolve(MODULE_NAME).resolve(MODULE_VERSION).resolve("metadata.json");
            String metadataContent = Files.readString(metadataPath);
            assertThat(metadataContent).contains("\"name\":\"" + MODULE_NAME + "\"");
            assertThat(metadataContent).contains("\"version\":\"" + MODULE_VERSION + "\"");
        }

        @Test
        void save_returnsMetadata() {
            // Arrange
            ModuleMetadata metadata = createTestMetadata(MODULE_NAME, MODULE_VERSION);
            byte[] jarData = createTestJarData("jar");

            // Act
            ModuleMetadata result = repository.save(metadata, jarData);

            // Assert
            assertThat(result).isEqualTo(metadata);
        }

        @Test
        void save_multipleVersions_createsSeperateDirectories() {
            // Arrange
            byte[] jarData = createTestJarData("jar");
            ModuleMetadata v1 = createTestMetadata(MODULE_NAME, "1.0.0");
            ModuleMetadata v2 = createTestMetadata(MODULE_NAME, "2.0.0");

            // Act
            repository.save(v1, jarData);
            repository.save(v2, jarData);

            // Assert
            assertThat(Files.exists(tempDir.resolve(MODULE_NAME).resolve("1.0.0"))).isTrue();
            assertThat(Files.exists(tempDir.resolve(MODULE_NAME).resolve("2.0.0"))).isTrue();
        }

        @Test
        void save_sanitizesModuleName() {
            // Arrange
            ModuleMetadata metadata = createTestMetadata("my/dangerous:module*name", MODULE_VERSION);
            byte[] jarData = createTestJarData("jar");

            // Act
            repository.save(metadata, jarData);

            // Assert
            // Special characters should be replaced with underscores
            Path expectedPath = tempDir.resolve("my_dangerous_module_name").resolve(MODULE_VERSION);
            assertThat(Files.exists(expectedPath)).isTrue();
        }
    }

    @Nested
    class FindByNameAndVersion {

        @Test
        void findByNameAndVersion_existingModule_returnsMetadata() {
            // Arrange
            ModuleMetadata metadata = createTestMetadata(MODULE_NAME, MODULE_VERSION);
            repository.save(metadata, createTestJarData("jar"));

            // Act
            Optional<ModuleMetadata> result = repository.findByNameAndVersion(MODULE_NAME, MODULE_VERSION);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().name()).isEqualTo(MODULE_NAME);
            assertThat(result.get().version()).isEqualTo(MODULE_VERSION);
        }

        @Test
        void findByNameAndVersion_nonExistent_returnsEmpty() {
            // Act
            Optional<ModuleMetadata> result = repository.findByNameAndVersion("non-existent", "1.0.0");

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        void findByNameAndVersion_wrongVersion_returnsEmpty() {
            // Arrange
            repository.save(createTestMetadata(MODULE_NAME, "1.0.0"), createTestJarData("jar"));

            // Act
            Optional<ModuleMetadata> result = repository.findByNameAndVersion(MODULE_NAME, "2.0.0");

            // Assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class FindByName {

        @Test
        void findByName_returnsAllVersions() {
            // Arrange
            byte[] jarData = createTestJarData("jar");
            repository.save(createTestMetadata(MODULE_NAME, "1.0.0"), jarData);
            repository.save(createTestMetadata(MODULE_NAME, "1.1.0"), jarData);
            repository.save(createTestMetadata(MODULE_NAME, "2.0.0"), jarData);

            // Act
            List<ModuleMetadata> result = repository.findByName(MODULE_NAME);

            // Assert
            assertThat(result).hasSize(3);
            assertThat(result).allMatch(m -> m.name().equals(MODULE_NAME));
        }

        @Test
        void findByName_nonExistent_returnsEmptyList() {
            // Act
            List<ModuleMetadata> result = repository.findByName("non-existent");

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        void findByName_excludesOtherModules() {
            // Arrange
            byte[] jarData = createTestJarData("jar");
            repository.save(createTestMetadata(MODULE_NAME, "1.0.0"), jarData);
            repository.save(createTestMetadata("other-module", "1.0.0"), jarData);

            // Act
            List<ModuleMetadata> result = repository.findByName(MODULE_NAME);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).name()).isEqualTo(MODULE_NAME);
        }
    }

    @Nested
    class FindAll {

        @Test
        void findAll_returnsAllModules() {
            // Arrange
            byte[] jarData = createTestJarData("jar");
            repository.save(createTestMetadata("module-a", "1.0.0"), jarData);
            repository.save(createTestMetadata("module-b", "1.0.0"), jarData);
            repository.save(createTestMetadata("module-c", "1.0.0"), jarData);

            // Act
            List<ModuleMetadata> result = repository.findAll();

            // Assert
            assertThat(result).hasSize(3);
        }

        @Test
        void findAll_emptyRepository_returnsEmptyList() {
            // Act
            List<ModuleMetadata> result = repository.findAll();

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        void findAll_includesMultipleVersions() {
            // Arrange
            byte[] jarData = createTestJarData("jar");
            repository.save(createTestMetadata("module-a", "1.0.0"), jarData);
            repository.save(createTestMetadata("module-a", "2.0.0"), jarData);
            repository.save(createTestMetadata("module-b", "1.0.0"), jarData);

            // Act
            List<ModuleMetadata> result = repository.findAll();

            // Assert
            assertThat(result).hasSize(3);
        }
    }

    @Nested
    class GetJarFile {

        @Test
        void getJarFile_existingModule_returnsInputStream() throws IOException {
            // Arrange
            ModuleMetadata metadata = createTestMetadata(MODULE_NAME, MODULE_VERSION);
            byte[] jarData = createTestJarData("test module jar content");
            repository.save(metadata, jarData);

            // Act
            Optional<InputStream> result = repository.getJarFile(MODULE_NAME, MODULE_VERSION);

            // Assert
            assertThat(result).isPresent();
            try (InputStream is = result.get()) {
                byte[] readData = is.readAllBytes();
                assertThat(readData).isEqualTo(jarData);
            }
        }

        @Test
        void getJarFile_nonExistent_returnsEmpty() {
            // Act
            Optional<InputStream> result = repository.getJarFile("non-existent", "1.0.0");

            // Assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class Delete {

        @Test
        void delete_existingModule_returnsTrue() {
            // Arrange
            repository.save(createTestMetadata(MODULE_NAME, MODULE_VERSION), createTestJarData("jar"));

            // Act
            boolean result = repository.delete(MODULE_NAME, MODULE_VERSION);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        void delete_removesFiles() {
            // Arrange
            repository.save(createTestMetadata(MODULE_NAME, MODULE_VERSION), createTestJarData("jar"));
            Path moduleVersionDir = tempDir.resolve(MODULE_NAME).resolve(MODULE_VERSION);
            assertThat(Files.exists(moduleVersionDir)).isTrue();

            // Act
            repository.delete(MODULE_NAME, MODULE_VERSION);

            // Assert
            assertThat(Files.exists(moduleVersionDir)).isFalse();
        }

        @Test
        void delete_removesEmptyParentDirectory() {
            // Arrange
            repository.save(createTestMetadata(MODULE_NAME, MODULE_VERSION), createTestJarData("jar"));
            Path moduleDir = tempDir.resolve(MODULE_NAME);
            assertThat(Files.exists(moduleDir)).isTrue();

            // Act
            repository.delete(MODULE_NAME, MODULE_VERSION);

            // Assert - parent directory should be deleted if empty
            assertThat(Files.exists(moduleDir)).isFalse();
        }

        @Test
        void delete_keepsParentIfOtherVersionsExist() {
            // Arrange
            byte[] jarData = createTestJarData("jar");
            repository.save(createTestMetadata(MODULE_NAME, "1.0.0"), jarData);
            repository.save(createTestMetadata(MODULE_NAME, "2.0.0"), jarData);
            Path moduleDir = tempDir.resolve(MODULE_NAME);

            // Act
            repository.delete(MODULE_NAME, "1.0.0");

            // Assert - parent directory should still exist
            assertThat(Files.exists(moduleDir)).isTrue();
            assertThat(Files.exists(moduleDir.resolve("2.0.0"))).isTrue();
        }

        @Test
        void delete_nonExistent_returnsFalse() {
            // Act
            boolean result = repository.delete("non-existent", "1.0.0");

            // Assert
            assertThat(result).isFalse();
        }
    }

    @Nested
    class Exists {

        @Test
        void exists_existingModule_returnsTrue() {
            // Arrange
            repository.save(createTestMetadata(MODULE_NAME, MODULE_VERSION), createTestJarData("jar"));

            // Act
            boolean result = repository.exists(MODULE_NAME, MODULE_VERSION);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        void exists_nonExistent_returnsFalse() {
            // Act
            boolean result = repository.exists("non-existent", "1.0.0");

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        void exists_afterDelete_returnsFalse() {
            // Arrange
            repository.save(createTestMetadata(MODULE_NAME, MODULE_VERSION), createTestJarData("jar"));
            repository.delete(MODULE_NAME, MODULE_VERSION);

            // Act
            boolean result = repository.exists(MODULE_NAME, MODULE_VERSION);

            // Assert
            assertThat(result).isFalse();
        }
    }

    /**
     * Test implementation of QuarkusModuleStorageConfig.
     */
    private static class TestModuleStorageConfig implements QuarkusModuleStorageConfig {
        private final String directory;

        TestModuleStorageConfig(String directory) {
            this.directory = directory;
        }

        @Override
        public String directory() {
            return directory;
        }

        @Override
        public long maxFileSize() {
            return 104857600L; // 100MB
        }

        @Override
        public String storageDirectory() {
            return directory;
        }
    }
}
