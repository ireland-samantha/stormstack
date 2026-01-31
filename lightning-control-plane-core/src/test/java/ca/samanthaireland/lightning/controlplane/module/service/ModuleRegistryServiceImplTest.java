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

package ca.samanthaireland.lightning.controlplane.module.service;

import ca.samanthaireland.lightning.controlplane.config.ModuleStorageConfiguration;
import ca.samanthaireland.lightning.controlplane.module.exception.ModuleNotFoundException;
import ca.samanthaireland.lightning.controlplane.module.model.ModuleMetadata;
import ca.samanthaireland.lightning.controlplane.module.repository.ModuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ModuleRegistryServiceImpl}.
 *
 * <p>Note: Distribution tests are in {@link ModuleDistributionServiceImplTest}
 * following the Interface Segregation Principle.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ModuleRegistryServiceImpl")
class ModuleRegistryServiceImplTest {

    @Mock
    private ModuleRepository moduleRepository;

    @Mock
    private ModuleStorageConfiguration config;

    private ModuleRegistryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ModuleRegistryServiceImpl(moduleRepository, config);
    }

    private void stubMaxFileSize() {
        when(config.maxFileSize()).thenReturn(10_000_000L); // 10MB
    }

    @Nested
    @DisplayName("uploadModule")
    class UploadModule {

        @Test
        @DisplayName("should upload module and calculate checksum")
        void shouldUploadModuleAndCalculateChecksum() {
            // Arrange
            stubMaxFileSize();
            String name = "test-module";
            String version = "1.0.0";
            String description = "Test module";
            String fileName = "test-module-1.0.0.jar";
            byte[] jarData = "test jar content".getBytes();
            String uploadedBy = "admin";

            when(moduleRepository.save(any(ModuleMetadata.class), eq(jarData)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            ModuleMetadata result = service.uploadModule(name, version, description, fileName, jarData, uploadedBy);

            // Assert
            assertThat(result.name()).isEqualTo(name);
            assertThat(result.version()).isEqualTo(version);
            assertThat(result.description()).isEqualTo(description);
            assertThat(result.fileName()).isEqualTo(fileName);
            assertThat(result.fileSize()).isEqualTo(jarData.length);
            assertThat(result.checksum()).isNotBlank();
            assertThat(result.uploadedBy()).isEqualTo(uploadedBy);
            verify(moduleRepository).save(any(ModuleMetadata.class), eq(jarData));
        }

        @Test
        @DisplayName("should reject files exceeding max size")
        void shouldRejectFilesExceedingMaxSize() {
            // Arrange
            when(config.maxFileSize()).thenReturn(100L);
            service = new ModuleRegistryServiceImpl(moduleRepository, config);
            byte[] largeJarData = new byte[101];

            // Act & Assert
            assertThatThrownBy(() -> service.uploadModule(
                    "test", "1.0.0", "desc", "test.jar", largeJarData, "admin"
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds maximum");

            verify(moduleRepository, never()).save(any(), any());
        }

        @Test
        @DisplayName("should generate consistent checksum for same content")
        void shouldGenerateConsistentChecksum() {
            // Arrange
            stubMaxFileSize();
            byte[] jarData = "test jar content".getBytes();
            when(moduleRepository.save(any(ModuleMetadata.class), eq(jarData)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            ModuleMetadata result1 = service.uploadModule("mod1", "1.0", null, "mod1.jar", jarData, null);
            ModuleMetadata result2 = service.uploadModule("mod2", "1.0", null, "mod2.jar", jarData, null);

            // Assert - same content should have same checksum
            assertThat(result1.checksum()).isEqualTo(result2.checksum());
        }
    }

    @Nested
    @DisplayName("getModule")
    class GetModule {

        @Test
        @DisplayName("should return module when found")
        void shouldReturnModuleWhenFound() {
            // Arrange
            ModuleMetadata metadata = createMetadata("test-module", "1.0.0");
            when(moduleRepository.findByNameAndVersion("test-module", "1.0.0"))
                    .thenReturn(Optional.of(metadata));

            // Act
            Optional<ModuleMetadata> result = service.getModule("test-module", "1.0.0");

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(metadata);
        }

        @Test
        @DisplayName("should return empty when module not found")
        void shouldReturnEmptyWhenModuleNotFound() {
            // Arrange
            when(moduleRepository.findByNameAndVersion("nonexistent", "1.0.0"))
                    .thenReturn(Optional.empty());

            // Act
            Optional<ModuleMetadata> result = service.getModule("nonexistent", "1.0.0");

            // Assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getModuleVersions")
    class GetModuleVersions {

        @Test
        @DisplayName("should return all versions of a module")
        void shouldReturnAllVersionsOfModule() {
            // Arrange
            ModuleMetadata v1 = createMetadata("test-module", "1.0.0");
            ModuleMetadata v2 = createMetadata("test-module", "2.0.0");
            when(moduleRepository.findByName("test-module")).thenReturn(List.of(v1, v2));

            // Act
            List<ModuleMetadata> result = service.getModuleVersions("test-module");

            // Assert
            assertThat(result).containsExactly(v1, v2);
        }

        @Test
        @DisplayName("should return empty list when no versions")
        void shouldReturnEmptyListWhenNoVersions() {
            // Arrange
            when(moduleRepository.findByName("nonexistent")).thenReturn(List.of());

            // Act
            List<ModuleMetadata> result = service.getModuleVersions("nonexistent");

            // Assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("listAllModules")
    class ListAllModules {

        @Test
        @DisplayName("should return all modules")
        void shouldReturnAllModules() {
            // Arrange
            ModuleMetadata mod1 = createMetadata("module-a", "1.0.0");
            ModuleMetadata mod2 = createMetadata("module-b", "2.0.0");
            when(moduleRepository.findAll()).thenReturn(List.of(mod1, mod2));

            // Act
            List<ModuleMetadata> result = service.listAllModules();

            // Assert
            assertThat(result).containsExactly(mod1, mod2);
        }
    }

    @Nested
    @DisplayName("downloadModule")
    class DownloadModule {

        @Test
        @DisplayName("should return input stream for module JAR")
        void shouldReturnInputStreamForModuleJar() {
            // Arrange
            byte[] jarContent = "jar content".getBytes();
            InputStream inputStream = new ByteArrayInputStream(jarContent);
            when(moduleRepository.getJarFile("test-module", "1.0.0"))
                    .thenReturn(Optional.of(inputStream));

            // Act
            InputStream result = service.downloadModule("test-module", "1.0.0");

            // Assert
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should throw exception when module not found")
        void shouldThrowExceptionWhenModuleNotFound() {
            // Arrange
            when(moduleRepository.getJarFile("nonexistent", "1.0.0"))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.downloadModule("nonexistent", "1.0.0"))
                    .isInstanceOf(ModuleNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deleteModule")
    class DeleteModule {

        @Test
        @DisplayName("should delete module when exists")
        void shouldDeleteModuleWhenExists() {
            // Arrange
            when(moduleRepository.exists("test-module", "1.0.0")).thenReturn(true);

            // Act
            service.deleteModule("test-module", "1.0.0");

            // Assert
            verify(moduleRepository).delete("test-module", "1.0.0");
        }

        @Test
        @DisplayName("should throw exception when module not found")
        void shouldThrowExceptionWhenModuleNotFound() {
            // Arrange
            when(moduleRepository.exists("nonexistent", "1.0.0")).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> service.deleteModule("nonexistent", "1.0.0"))
                    .isInstanceOf(ModuleNotFoundException.class);
            verify(moduleRepository, never()).delete(any(), any());
        }
    }

    // Helper methods

    private ModuleMetadata createMetadata(String name, String version) {
        return ModuleMetadata.create(
                name,
                version,
                "Test module",
                name + "-" + version + ".jar",
                1024,
                "abc123def456",
                "admin"
        );
    }
}
