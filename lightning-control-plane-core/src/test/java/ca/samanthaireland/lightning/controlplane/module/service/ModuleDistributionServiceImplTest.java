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

import ca.samanthaireland.lightning.controlplane.client.LightningNodeClient;
import ca.samanthaireland.lightning.controlplane.module.exception.ModuleDistributionException;
import ca.samanthaireland.lightning.controlplane.module.exception.ModuleNotFoundException;
import ca.samanthaireland.lightning.controlplane.module.model.ModuleMetadata;
import ca.samanthaireland.lightning.controlplane.module.repository.ModuleRepository;
import ca.samanthaireland.lightning.controlplane.node.exception.NodeNotFoundException;
import ca.samanthaireland.lightning.controlplane.node.model.Node;
import ca.samanthaireland.lightning.controlplane.node.model.NodeCapacity;
import ca.samanthaireland.lightning.controlplane.node.model.NodeId;
import ca.samanthaireland.lightning.controlplane.node.model.NodeMetrics;
import ca.samanthaireland.lightning.controlplane.node.service.NodeRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ModuleDistributionServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ModuleDistributionServiceImpl")
class ModuleDistributionServiceImplTest {

    @Mock
    private ModuleRepository moduleRepository;

    @Mock
    private NodeRegistryService nodeRegistryService;

    @Mock
    private LightningNodeClient nodeClient;

    private ModuleDistributionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ModuleDistributionServiceImpl(moduleRepository, nodeRegistryService, nodeClient);
    }

    @Nested
    @DisplayName("distributeToNode")
    class DistributeToNode {

        @Test
        @DisplayName("should distribute module to specific node")
        void shouldDistributeModuleToSpecificNode() {
            // Arrange
            ModuleMetadata metadata = createMetadata("test-module", "1.0.0");
            byte[] jarContent = "jar content".getBytes();
            Node node = createHealthyNode("node-1");
            NodeId nodeId = NodeId.of("node-1");

            when(moduleRepository.findByNameAndVersion("test-module", "1.0.0"))
                    .thenReturn(Optional.of(metadata));
            when(moduleRepository.getJarFile("test-module", "1.0.0"))
                    .thenReturn(Optional.of(new ByteArrayInputStream(jarContent)));
            when(nodeRegistryService.findById(nodeId)).thenReturn(Optional.of(node));

            // Act
            service.distributeToNode("test-module", "1.0.0", nodeId);

            // Assert
            verify(nodeClient).uploadModule(eq(node), eq("test-module"), eq("1.0.0"), eq("test-module-1.0.0.jar"), any());
        }

        @Test
        @DisplayName("should throw exception when module not found")
        void shouldThrowExceptionWhenModuleNotFound() {
            // Arrange
            NodeId nodeId = NodeId.of("node-1");
            when(moduleRepository.findByNameAndVersion("nonexistent", "1.0.0"))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.distributeToNode("nonexistent", "1.0.0", nodeId))
                    .isInstanceOf(ModuleNotFoundException.class);
            verify(nodeClient, never()).uploadModule(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should throw exception when node not found")
        void shouldThrowExceptionWhenNodeNotFound() {
            // Arrange
            ModuleMetadata metadata = createMetadata("test-module", "1.0.0");
            byte[] jarContent = "jar content".getBytes();
            NodeId nodeId = NodeId.of("nonexistent");

            when(moduleRepository.findByNameAndVersion("test-module", "1.0.0"))
                    .thenReturn(Optional.of(metadata));
            when(moduleRepository.getJarFile("test-module", "1.0.0"))
                    .thenReturn(Optional.of(new ByteArrayInputStream(jarContent)));
            when(nodeRegistryService.findById(nodeId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.distributeToNode("test-module", "1.0.0", nodeId))
                    .isInstanceOf(NodeNotFoundException.class);
            verify(nodeClient, never()).uploadModule(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should throw exception when distribution fails")
        void shouldThrowExceptionWhenDistributionFails() {
            // Arrange
            ModuleMetadata metadata = createMetadata("test-module", "1.0.0");
            byte[] jarContent = "jar content".getBytes();
            Node node = createHealthyNode("node-1");
            NodeId nodeId = NodeId.of("node-1");

            when(moduleRepository.findByNameAndVersion("test-module", "1.0.0"))
                    .thenReturn(Optional.of(metadata));
            when(moduleRepository.getJarFile("test-module", "1.0.0"))
                    .thenReturn(Optional.of(new ByteArrayInputStream(jarContent)));
            when(nodeRegistryService.findById(nodeId)).thenReturn(Optional.of(node));
            doThrow(new RuntimeException("Connection failed"))
                    .when(nodeClient).uploadModule(any(), any(), any(), any(), any());

            // Act & Assert
            assertThatThrownBy(() -> service.distributeToNode("test-module", "1.0.0", nodeId))
                    .isInstanceOf(ModuleDistributionException.class);
        }
    }

    @Nested
    @DisplayName("distributeToAllNodes")
    class DistributeToAllNodes {

        @Test
        @DisplayName("should distribute to all healthy nodes")
        void shouldDistributeToAllHealthyNodes() {
            // Arrange
            ModuleMetadata metadata = createMetadata("test-module", "1.0.0");
            byte[] jarContent = "jar content".getBytes();
            Node node1 = createHealthyNode("node-1");
            Node node2 = createHealthyNode("node-2");

            when(moduleRepository.findByNameAndVersion("test-module", "1.0.0"))
                    .thenReturn(Optional.of(metadata));
            when(moduleRepository.getJarFile("test-module", "1.0.0"))
                    .thenReturn(Optional.of(new ByteArrayInputStream(jarContent)));
            when(nodeRegistryService.findAll()).thenReturn(List.of(node1, node2));

            // Act
            int count = service.distributeToAllNodes("test-module", "1.0.0");

            // Assert
            assertThat(count).isEqualTo(2);
            verify(nodeClient, times(2)).uploadModule(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should skip draining nodes")
        void shouldSkipDrainingNodes() {
            // Arrange
            ModuleMetadata metadata = createMetadata("test-module", "1.0.0");
            byte[] jarContent = "jar content".getBytes();
            Node healthy = createHealthyNode("node-1");
            Node draining = createDrainingNode("node-2");

            when(moduleRepository.findByNameAndVersion("test-module", "1.0.0"))
                    .thenReturn(Optional.of(metadata));
            when(moduleRepository.getJarFile("test-module", "1.0.0"))
                    .thenReturn(Optional.of(new ByteArrayInputStream(jarContent)));
            when(nodeRegistryService.findAll()).thenReturn(List.of(healthy, draining));

            // Act
            int count = service.distributeToAllNodes("test-module", "1.0.0");

            // Assert
            assertThat(count).isEqualTo(1);
            verify(nodeClient, times(1)).uploadModule(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should continue distributing even if some nodes fail")
        void shouldContinueDistributingEvenIfSomeNodesFail() {
            // Arrange
            ModuleMetadata metadata = createMetadata("test-module", "1.0.0");
            byte[] jarContent = "jar content".getBytes();
            Node node1 = createHealthyNode("node-1");
            Node node2 = createHealthyNode("node-2");

            when(moduleRepository.findByNameAndVersion("test-module", "1.0.0"))
                    .thenReturn(Optional.of(metadata));
            when(moduleRepository.getJarFile("test-module", "1.0.0"))
                    .thenReturn(Optional.of(new ByteArrayInputStream(jarContent)));
            when(nodeRegistryService.findAll()).thenReturn(List.of(node1, node2));

            // First call throws, second succeeds
            doThrow(new RuntimeException("Connection failed"))
                    .doNothing()
                    .when(nodeClient).uploadModule(any(), any(), any(), any(), any());

            // Act
            int count = service.distributeToAllNodes("test-module", "1.0.0");

            // Assert - only 1 success
            assertThat(count).isEqualTo(1);
            verify(nodeClient, times(2)).uploadModule(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should return zero when no healthy nodes")
        void shouldReturnZeroWhenNoHealthyNodes() {
            // Arrange
            ModuleMetadata metadata = createMetadata("test-module", "1.0.0");
            byte[] jarContent = "jar content".getBytes();
            Node draining = createDrainingNode("node-1");

            when(moduleRepository.findByNameAndVersion("test-module", "1.0.0"))
                    .thenReturn(Optional.of(metadata));
            when(moduleRepository.getJarFile("test-module", "1.0.0"))
                    .thenReturn(Optional.of(new ByteArrayInputStream(jarContent)));
            when(nodeRegistryService.findAll()).thenReturn(List.of(draining));

            // Act
            int count = service.distributeToAllNodes("test-module", "1.0.0");

            // Assert
            assertThat(count).isZero();
            verify(nodeClient, never()).uploadModule(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should throw exception when module not found")
        void shouldThrowExceptionWhenModuleNotFound() {
            // Arrange
            when(moduleRepository.findByNameAndVersion("nonexistent", "1.0.0"))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.distributeToAllNodes("nonexistent", "1.0.0"))
                    .isInstanceOf(ModuleNotFoundException.class);
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

    private Node createHealthyNode(String nodeId) {
        return Node.register(NodeId.of(nodeId), "http://" + nodeId + ":8080", new NodeCapacity(100))
                .withHeartbeat(new NodeMetrics(10, 20, 0.5, 256, 512));
    }

    private Node createDrainingNode(String nodeId) {
        return createHealthyNode(nodeId).drain();
    }
}
