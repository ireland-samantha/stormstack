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

package ca.samanthaireland.stormstack.thunder.controlplane.scheduler.service;

import ca.samanthaireland.stormstack.thunder.controlplane.node.model.Node;
import ca.samanthaireland.stormstack.thunder.controlplane.node.model.NodeCapacity;
import ca.samanthaireland.stormstack.thunder.controlplane.node.model.NodeId;
import ca.samanthaireland.stormstack.thunder.controlplane.node.model.NodeMetrics;
import ca.samanthaireland.stormstack.thunder.controlplane.node.model.NodeStatus;
import ca.samanthaireland.stormstack.thunder.controlplane.node.service.NodeRegistryService;
import ca.samanthaireland.stormstack.thunder.controlplane.scheduler.exception.NoAvailableNodesException;
import ca.samanthaireland.stormstack.thunder.controlplane.scheduler.exception.NoCapableNodesException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SchedulerServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SchedulerServiceImpl")
class SchedulerServiceImplTest {

    @Mock
    private NodeRegistryService nodeRegistryService;

    private SchedulerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SchedulerServiceImpl(nodeRegistryService);
    }

    @Nested
    @DisplayName("selectNodeForMatch")
    class SelectNodeForMatch {

        @Test
        @DisplayName("should select least loaded node")
        void shouldSelectLeastLoadedNode() {
            // Arrange - node-2 has lower saturation (30/100 vs 50/100)
            Node node1 = createHealthyNodeWithCapacity("node-1", 50, 100, 100);
            Node node2 = createHealthyNodeWithCapacity("node-2", 30, 60, 100);
            when(nodeRegistryService.findAll()).thenReturn(List.of(node1, node2));

            // Act
            Node selected = service.selectNodeForMatch(List.of("module-a"), null);

            // Assert
            assertThat(selected.nodeId()).isEqualTo(NodeId.of("node-2"));
        }

        @Test
        @DisplayName("should select preferred node when available")
        void shouldSelectPreferredNodeWhenAvailable() {
            // Arrange - node-1 has higher load but is preferred
            Node node1 = createHealthyNodeWithCapacity("node-1", 50, 100, 100);
            Node node2 = createHealthyNodeWithCapacity("node-2", 30, 60, 100);
            when(nodeRegistryService.findAll()).thenReturn(List.of(node1, node2));

            // Act
            Node selected = service.selectNodeForMatch(List.of("module-a"), NodeId.of("node-1"));

            // Assert
            assertThat(selected.nodeId()).isEqualTo(NodeId.of("node-1"));
        }

        @Test
        @DisplayName("should fall back to least loaded when preferred not available")
        void shouldFallBackToLeastLoadedWhenPreferredNotAvailable() {
            // Arrange
            Node node1 = createHealthyNodeWithCapacity("node-1", 50, 100, 100);
            Node node2 = createHealthyNodeWithCapacity("node-2", 30, 60, 100);
            when(nodeRegistryService.findAll()).thenReturn(List.of(node1, node2));

            // Act
            Node selected = service.selectNodeForMatch(List.of("module-a"), NodeId.of("node-3"));

            // Assert - should fall back to least loaded (node-2)
            assertThat(selected.nodeId()).isEqualTo(NodeId.of("node-2"));
        }

        @Test
        @DisplayName("should throw exception when no healthy nodes")
        void shouldThrowExceptionWhenNoHealthyNodes() {
            // Arrange - only draining nodes
            Node draining = createDrainingNode("node-1", 50, 100);
            when(nodeRegistryService.findAll()).thenReturn(List.of(draining));

            // Act & Assert
            assertThatThrownBy(() -> service.selectNodeForMatch(List.of("module-a"), null))
                    .isInstanceOf(NoAvailableNodesException.class);
        }

        @Test
        @DisplayName("should throw exception when no nodes have capacity")
        void shouldThrowExceptionWhenNoNodesHaveCapacity() {
            // Arrange - node is at capacity (100/100)
            Node fullNode = createHealthyNodeWithCapacity("node-1", 100, 200, 100);
            when(nodeRegistryService.findAll()).thenReturn(List.of(fullNode));

            // Act & Assert
            assertThatThrownBy(() -> service.selectNodeForMatch(List.of("module-a"), null))
                    .isInstanceOf(NoCapableNodesException.class);
        }

        @Test
        @DisplayName("should ignore draining nodes for selection")
        void shouldIgnoreDrainingNodesForSelection() {
            // Arrange - draining node has lower load but should be ignored
            Node healthy = createHealthyNodeWithCapacity("node-1", 50, 100, 100);
            Node draining = createDrainingNode("node-2", 10, 20);
            when(nodeRegistryService.findAll()).thenReturn(List.of(healthy, draining));

            // Act
            Node selected = service.selectNodeForMatch(List.of("module-a"), null);

            // Assert
            assertThat(selected.nodeId()).isEqualTo(NodeId.of("node-1"));
        }

        @Test
        @DisplayName("should throw exception when cluster is empty")
        void shouldThrowExceptionWhenClusterIsEmpty() {
            // Arrange
            when(nodeRegistryService.findAll()).thenReturn(List.of());

            // Act & Assert
            assertThatThrownBy(() -> service.selectNodeForMatch(List.of("module-a"), null))
                    .isInstanceOf(NoAvailableNodesException.class);
        }

        @Test
        @DisplayName("should not select preferred draining node")
        void shouldNotSelectPreferredDrainingNode() {
            // Arrange - preferred node is draining
            Node healthy = createHealthyNodeWithCapacity("node-1", 50, 100, 100);
            Node draining = createDrainingNode("node-2", 10, 20);
            when(nodeRegistryService.findAll()).thenReturn(List.of(healthy, draining));

            // Act - request draining node as preferred
            Node selected = service.selectNodeForMatch(List.of("module-a"), NodeId.of("node-2"));

            // Assert - should fall back to healthy node
            assertThat(selected.nodeId()).isEqualTo(NodeId.of("node-1"));
        }
    }

    @Nested
    @DisplayName("calculateSaturation")
    class CalculateSaturation {

        @Test
        @DisplayName("should calculate saturation as containers divided by capacity")
        void shouldCalculateSaturationCorrectly() {
            // Arrange - 50 containers out of 100 = 0.5
            Node node = createHealthyNodeWithCapacity("node-1", 50, 100, 100);

            // Act
            double saturation = service.calculateSaturation(node);

            // Assert
            assertThat(saturation).isCloseTo(0.5, within(0.001));
        }

        @Test
        @DisplayName("should return 1.0 when node is at capacity")
        void shouldReturnOneWhenNodeIsAtCapacity() {
            // Arrange - node at full capacity
            Node node = createHealthyNodeWithCapacity("node-1", 1, 0, 1);

            // Act
            double saturation = service.calculateSaturation(node);

            // Assert
            assertThat(saturation).isEqualTo(1.0);
        }

        @Test
        @DisplayName("should return 0.0 when empty")
        void shouldReturnZeroWhenEmpty() {
            // Arrange - empty node
            Node node = createHealthyNodeWithCapacity("node-1", 0, 0, 100);

            // Act
            double saturation = service.calculateSaturation(node);

            // Assert
            assertThat(saturation).isEqualTo(0.0);
        }

    }

    @Nested
    @DisplayName("getClusterSaturation")
    class GetClusterSaturation {

        @Test
        @DisplayName("should calculate cluster saturation from all healthy nodes")
        void shouldCalculateClusterSaturationFromAllHealthyNodes() {
            // Arrange - total: 80 containers / 200 capacity = 0.4
            Node node1 = createHealthyNodeWithCapacity("node-1", 50, 100, 100);
            Node node2 = createHealthyNodeWithCapacity("node-2", 30, 60, 100);
            when(nodeRegistryService.findAll()).thenReturn(List.of(node1, node2));

            // Act
            double saturation = service.getClusterSaturation();

            // Assert
            assertThat(saturation).isCloseTo(0.4, within(0.001));
        }

        @Test
        @DisplayName("should return 1.0 when no healthy nodes")
        void shouldReturnOneWhenNoHealthyNodes() {
            // Arrange - only draining nodes
            Node draining = createDrainingNode("node-1", 50, 100);
            when(nodeRegistryService.findAll()).thenReturn(List.of(draining));

            // Act
            double saturation = service.getClusterSaturation();

            // Assert
            assertThat(saturation).isEqualTo(1.0);
        }

        @Test
        @DisplayName("should return 1.0 when cluster is empty")
        void shouldReturnOneWhenClusterIsEmpty() {
            // Arrange
            when(nodeRegistryService.findAll()).thenReturn(List.of());

            // Act
            double saturation = service.getClusterSaturation();

            // Assert
            assertThat(saturation).isEqualTo(1.0);
        }

        @Test
        @DisplayName("should return 1.0 when all nodes are at full capacity")
        void shouldReturnOneWhenAllNodesAtFullCapacity() {
            // Arrange - node at full capacity
            Node node = createHealthyNodeWithCapacity("node-1", 1, 0, 1);
            when(nodeRegistryService.findAll()).thenReturn(List.of(node));

            // Act
            double saturation = service.getClusterSaturation();

            // Assert
            assertThat(saturation).isEqualTo(1.0);
        }

        @Test
        @DisplayName("should ignore draining nodes in saturation calculation")
        void shouldIgnoreDrainingNodesInSaturationCalculation() {
            // Arrange - only count healthy node
            Node healthy = createHealthyNodeWithCapacity("node-1", 50, 100, 100);
            Node draining = createDrainingNode("node-2", 10, 20);
            when(nodeRegistryService.findAll()).thenReturn(List.of(healthy, draining));

            // Act
            double saturation = service.getClusterSaturation();

            // Assert - 50/100 = 0.5 (draining node ignored)
            assertThat(saturation).isCloseTo(0.5, within(0.001));
        }
    }

    // Helper methods

    private Node createHealthyNodeWithCapacity(String nodeId, int containerCount, int matchCount, int maxContainers) {
        return Node.register(NodeId.of(nodeId), "http://" + nodeId + ":8080", new NodeCapacity(maxContainers))
                .withHeartbeat(new NodeMetrics(containerCount, matchCount, 0.5, 256, 512));
    }

    private Node createDrainingNode(String nodeId, int containerCount, int matchCount) {
        return createHealthyNodeWithCapacity(nodeId, containerCount, matchCount, 100).drain();
    }
}
