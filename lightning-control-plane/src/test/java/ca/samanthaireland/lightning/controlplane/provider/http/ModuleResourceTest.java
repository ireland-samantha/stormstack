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

package ca.samanthaireland.lightning.controlplane.provider.http;

import ca.samanthaireland.lightning.controlplane.module.exception.ModuleNotFoundException;
import ca.samanthaireland.lightning.controlplane.module.model.ModuleMetadata;
import ca.samanthaireland.lightning.controlplane.module.service.ModuleDistributionService;
import ca.samanthaireland.lightning.controlplane.module.service.ModuleRegistryService;
import ca.samanthaireland.lightning.controlplane.node.model.NodeId;
import ca.samanthaireland.lightning.controlplane.provider.dto.ModuleResponse;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModuleResourceTest {

    @Mock
    private ModuleRegistryService moduleRegistryService;

    @Mock
    private ModuleDistributionService moduleDistributionService;

    private ModuleResource resource;

    private static final String MODULE_NAME = "entity-module";
    private static final String MODULE_VERSION = "1.0.0";

    @BeforeEach
    void setUp() {
        resource = new ModuleResource(moduleRegistryService, moduleDistributionService);
    }

    private ModuleMetadata createTestMetadata() {
        return new ModuleMetadata(
                MODULE_NAME,
                MODULE_VERSION,
                "Entity spawning and lifecycle module",
                "entity-module-1.0.0.jar",
                12345L,
                "sha256:abc123",
                Instant.now(),
                "api"
        );
    }

    @Nested
    class ListModules {

        @Test
        void listModules_returnsAllModules() {
            // Arrange
            ModuleMetadata module1 = createTestMetadata();
            ModuleMetadata module2 = new ModuleMetadata(
                    "grid-map-module", "1.0.0", "Grid-based map system",
                    "grid-map-module-1.0.0.jar", 5678L, "sha256:def456",
                    Instant.now(), "api"
            );
            when(moduleRegistryService.listAllModules()).thenReturn(List.of(module1, module2));

            // Act
            List<ModuleResponse> response = resource.listModules();

            // Assert
            assertThat(response).hasSize(2);
            assertThat(response.get(0).name()).isEqualTo(MODULE_NAME);
            assertThat(response.get(1).name()).isEqualTo("grid-map-module");
        }

        @Test
        void listModules_emptyRegistry_returnsEmptyList() {
            // Arrange
            when(moduleRegistryService.listAllModules()).thenReturn(List.of());

            // Act
            List<ModuleResponse> response = resource.listModules();

            // Assert
            assertThat(response).isEmpty();
        }
    }

    @Nested
    class ListModuleVersions {

        @Test
        void listModuleVersions_existingModule_returnsVersions() {
            // Arrange
            ModuleMetadata v1 = createTestMetadata();
            ModuleMetadata v2 = new ModuleMetadata(
                    MODULE_NAME, "1.1.0", "Entity spawning and lifecycle module",
                    "entity-module-1.1.0.jar", 13000L, "sha256:xyz789",
                    Instant.now(), "api"
            );
            when(moduleRegistryService.getModuleVersions(MODULE_NAME))
                    .thenReturn(List.of(v1, v2));

            // Act
            List<ModuleResponse> response = resource.listModuleVersions(MODULE_NAME);

            // Assert
            assertThat(response).hasSize(2);
            assertThat(response.get(0).version()).isEqualTo(MODULE_VERSION);
            assertThat(response.get(1).version()).isEqualTo("1.1.0");
        }

        @Test
        void listModuleVersions_unknownModule_returnsEmptyList() {
            // Arrange
            when(moduleRegistryService.getModuleVersions("unknown")).thenReturn(List.of());

            // Act
            List<ModuleResponse> response = resource.listModuleVersions("unknown");

            // Assert
            assertThat(response).isEmpty();
        }
    }

    @Nested
    class GetModule {

        @Test
        void getModule_existingModule_returnsMetadata() {
            // Arrange
            ModuleMetadata metadata = createTestMetadata();
            when(moduleRegistryService.getModule(MODULE_NAME, MODULE_VERSION))
                    .thenReturn(Optional.of(metadata));

            // Act
            ModuleResponse response = resource.getModule(MODULE_NAME, MODULE_VERSION);

            // Assert
            assertThat(response.name()).isEqualTo(MODULE_NAME);
            assertThat(response.version()).isEqualTo(MODULE_VERSION);
            assertThat(response.description()).isEqualTo("Entity spawning and lifecycle module");
        }

        @Test
        void getModule_unknownModule_throwsException() {
            // Arrange
            when(moduleRegistryService.getModule("unknown", "1.0.0"))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> resource.getModule("unknown", "1.0.0"))
                    .isInstanceOf(ModuleNotFoundException.class);
        }
    }

    @Nested
    class DownloadModule {

        @Test
        void downloadModule_existingModule_returnsJarStream() {
            // Arrange
            ModuleMetadata metadata = createTestMetadata();
            when(moduleRegistryService.getModule(MODULE_NAME, MODULE_VERSION))
                    .thenReturn(Optional.of(metadata));

            byte[] jarData = "fake jar content".getBytes();
            InputStream stream = new ByteArrayInputStream(jarData);
            when(moduleRegistryService.downloadModule(MODULE_NAME, MODULE_VERSION))
                    .thenReturn(stream);

            // Act
            Response response = resource.downloadModule(MODULE_NAME, MODULE_VERSION);

            // Assert
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getHeaderString("Content-Disposition"))
                    .contains("entity-module-1.0.0.jar");
            assertThat(response.getHeaderString("Content-Length"))
                    .isEqualTo("12345");
        }

        @Test
        void downloadModule_unknownModule_throwsException() {
            // Arrange
            when(moduleRegistryService.getModule("unknown", "1.0.0"))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> resource.downloadModule("unknown", "1.0.0"))
                    .isInstanceOf(ModuleNotFoundException.class);
        }
    }

    @Nested
    class DeleteModule {

        @Test
        void deleteModule_existingModule_returns204() {
            // Arrange
            doNothing().when(moduleRegistryService).deleteModule(MODULE_NAME, MODULE_VERSION);

            // Act
            Response response = resource.deleteModule(MODULE_NAME, MODULE_VERSION);

            // Assert
            assertThat(response.getStatus()).isEqualTo(204);
            verify(moduleRegistryService).deleteModule(MODULE_NAME, MODULE_VERSION);
        }

        @Test
        void deleteModule_unknownModule_throwsException() {
            // Arrange
            doThrow(new ModuleNotFoundException("unknown", "1.0.0"))
                    .when(moduleRegistryService).deleteModule("unknown", "1.0.0");

            // Act & Assert
            assertThatThrownBy(() -> resource.deleteModule("unknown", "1.0.0"))
                    .isInstanceOf(ModuleNotFoundException.class);
        }
    }

    @Nested
    class DistributeModule {

        @Test
        void distributeToAllNodes_success_returnsCount() {
            // Arrange
            when(moduleDistributionService.distributeToAllNodes(MODULE_NAME, MODULE_VERSION))
                    .thenReturn(5);

            // Act
            ModuleResource.DistributionResult result =
                    resource.distributeToAllNodes(MODULE_NAME, MODULE_VERSION);

            // Assert
            assertThat(result.moduleName()).isEqualTo(MODULE_NAME);
            assertThat(result.moduleVersion()).isEqualTo(MODULE_VERSION);
            assertThat(result.nodesUpdated()).isEqualTo(5);
            assertThat(result.targetNode()).isNull();
        }

        @Test
        void distributeToNode_success_returns200() {
            // Arrange
            String targetNode = "node-1";
            doNothing().when(moduleDistributionService)
                    .distributeToNode(MODULE_NAME, MODULE_VERSION, NodeId.of(targetNode));

            // Act
            Response response = resource.distributeToNode(MODULE_NAME, MODULE_VERSION, targetNode);

            // Assert
            assertThat(response.getStatus()).isEqualTo(200);

            ModuleResource.DistributionResult result =
                    (ModuleResource.DistributionResult) response.getEntity();
            assertThat(result.targetNode()).isEqualTo(targetNode);
            assertThat(result.nodesUpdated()).isEqualTo(1);
        }
    }
}
