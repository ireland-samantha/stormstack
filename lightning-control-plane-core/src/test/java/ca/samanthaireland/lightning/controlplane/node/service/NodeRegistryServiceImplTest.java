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

package ca.samanthaireland.lightning.controlplane.node.service;

import ca.samanthaireland.lightning.controlplane.config.ControlPlaneConfiguration;
import ca.samanthaireland.lightning.controlplane.node.exception.NodeNotFoundException;
import ca.samanthaireland.lightning.controlplane.node.model.Node;
import ca.samanthaireland.lightning.controlplane.node.model.NodeCapacity;
import ca.samanthaireland.lightning.controlplane.node.model.NodeId;
import ca.samanthaireland.lightning.controlplane.node.model.NodeMetrics;
import ca.samanthaireland.lightning.controlplane.node.model.NodeStatus;
import ca.samanthaireland.lightning.controlplane.node.repository.NodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NodeRegistryServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NodeRegistryServiceImpl")
class NodeRegistryServiceImplTest {

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private ControlPlaneConfiguration config;

    private NodeRegistryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NodeRegistryServiceImpl(nodeRepository, config);
    }

    private void stubNodeTtl() {
        when(config.nodeTtlSeconds()).thenReturn(30);
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("should register new node with healthy status")
        void shouldRegisterNewNodeWithHealthyStatus() {
            // Arrange
            stubNodeTtl();
            NodeId nodeId = NodeId.of("node-1");
            String advertiseAddress = "http://node-1:8080";
            NodeCapacity capacity = new NodeCapacity(100);
            when(nodeRepository.findById(nodeId)).thenReturn(Optional.empty());
            when(nodeRepository.save(any(Node.class), eq(30))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            Node result = service.register(nodeId, advertiseAddress, capacity);

            // Assert
            assertThat(result.nodeId()).isEqualTo(nodeId);
            assertThat(result.advertiseAddress()).isEqualTo(advertiseAddress);
            assertThat(result.status()).isEqualTo(NodeStatus.HEALTHY);
            assertThat(result.capacity()).isEqualTo(capacity);

            ArgumentCaptor<Node> nodeCaptor = ArgumentCaptor.forClass(Node.class);
            verify(nodeRepository).save(nodeCaptor.capture(), eq(30));
            assertThat(nodeCaptor.getValue().status()).isEqualTo(NodeStatus.HEALTHY);
        }

        @Test
        @DisplayName("should re-register existing node preserving status")
        void shouldReRegisterExistingNodePreservingStatus() {
            // Arrange
            stubNodeTtl();
            NodeId nodeId = NodeId.of("node-1");
            String advertiseAddress = "http://node-1:8080";
            NodeCapacity newCapacity = new NodeCapacity(200);
            Node existingNode = createHealthyNode("node-1", 50, 100);
            when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(existingNode));
            when(nodeRepository.save(any(Node.class), eq(30))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            Node result = service.register(nodeId, advertiseAddress, newCapacity);

            // Assert
            assertThat(result.nodeId()).isEqualTo(nodeId);
            assertThat(result.advertiseAddress()).isEqualTo(advertiseAddress);
            assertThat(result.capacity()).isEqualTo(newCapacity);
            assertThat(result.status()).isEqualTo(existingNode.status());
            assertThat(result.metrics()).isEqualTo(existingNode.metrics());
        }

        @Test
        @DisplayName("should preserve draining status on re-registration")
        void shouldPreserveDrainingStatusOnReRegistration() {
            // Arrange
            stubNodeTtl();
            NodeId nodeId = NodeId.of("node-1");
            String advertiseAddress = "http://node-1:8080";
            NodeCapacity newCapacity = new NodeCapacity(200);
            Node drainingNode = createDrainingNode("node-1", 50, 100);
            when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(drainingNode));
            when(nodeRepository.save(any(Node.class), eq(30))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            Node result = service.register(nodeId, advertiseAddress, newCapacity);

            // Assert
            assertThat(result.status()).isEqualTo(NodeStatus.DRAINING);
        }
    }

    @Nested
    @DisplayName("heartbeat")
    class Heartbeat {

        @Test
        @DisplayName("should update metrics on heartbeat")
        void shouldUpdateMetricsOnHeartbeat() {
            // Arrange
            stubNodeTtl();
            NodeId nodeId = NodeId.of("node-1");
            Node existingNode = createHealthyNode("node-1", 10, 20);
            NodeMetrics newMetrics = new NodeMetrics(50, 100, 0.8, 512, 1024);
            when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(existingNode));
            when(nodeRepository.save(any(Node.class), eq(30))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            Node result = service.heartbeat(nodeId, newMetrics);

            // Assert
            assertThat(result.metrics()).isEqualTo(newMetrics);
            verify(nodeRepository).save(any(Node.class), eq(30));
        }

        @Test
        @DisplayName("should throw exception when node not found")
        void shouldThrowExceptionWhenNodeNotFound() {
            // Arrange
            NodeId nodeId = NodeId.of("nonexistent");
            NodeMetrics metrics = new NodeMetrics(50, 100, 0.8, 512, 1024);
            when(nodeRepository.findById(nodeId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.heartbeat(nodeId, metrics))
                    .isInstanceOf(NodeNotFoundException.class);
            verify(nodeRepository, never()).save(any(), anyInt());
        }
    }

    @Nested
    @DisplayName("drain")
    class Drain {

        @Test
        @DisplayName("should change node status to draining")
        void shouldChangeNodeStatusToDraining() {
            // Arrange
            stubNodeTtl();
            NodeId nodeId = NodeId.of("node-1");
            Node healthyNode = createHealthyNode("node-1", 50, 100);
            when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(healthyNode));
            when(nodeRepository.save(any(Node.class), eq(30))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            Node result = service.drain(nodeId);

            // Assert
            assertThat(result.status()).isEqualTo(NodeStatus.DRAINING);
            assertThat(result.nodeId()).isEqualTo(nodeId);

            ArgumentCaptor<Node> nodeCaptor = ArgumentCaptor.forClass(Node.class);
            verify(nodeRepository).save(nodeCaptor.capture(), eq(30));
            assertThat(nodeCaptor.getValue().status()).isEqualTo(NodeStatus.DRAINING);
        }

        @Test
        @DisplayName("should throw exception when node not found")
        void shouldThrowExceptionWhenNodeNotFound() {
            // Arrange
            NodeId nodeId = NodeId.of("nonexistent");
            when(nodeRepository.findById(nodeId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.drain(nodeId))
                    .isInstanceOf(NodeNotFoundException.class);
            verify(nodeRepository, never()).save(any(), anyInt());
        }
    }

    @Nested
    @DisplayName("deregister")
    class Deregister {

        @Test
        @DisplayName("should delete node when exists")
        void shouldDeleteNodeWhenExists() {
            // Arrange
            NodeId nodeId = NodeId.of("node-1");
            when(nodeRepository.existsById(nodeId)).thenReturn(true);

            // Act
            service.deregister(nodeId);

            // Assert
            verify(nodeRepository).deleteById(nodeId);
        }

        @Test
        @DisplayName("should throw exception when node not found")
        void shouldThrowExceptionWhenNodeNotFound() {
            // Arrange
            NodeId nodeId = NodeId.of("nonexistent");
            when(nodeRepository.existsById(nodeId)).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> service.deregister(nodeId))
                    .isInstanceOf(NodeNotFoundException.class);
            verify(nodeRepository, never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("should return node when found")
        void shouldReturnNodeWhenFound() {
            // Arrange
            NodeId nodeId = NodeId.of("node-1");
            Node node = createHealthyNode("node-1", 50, 100);
            when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));

            // Act
            Optional<Node> result = service.findById(nodeId);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(node);
        }

        @Test
        @DisplayName("should return empty when node not found")
        void shouldReturnEmptyWhenNodeNotFound() {
            // Arrange
            NodeId nodeId = NodeId.of("nonexistent");
            when(nodeRepository.findById(nodeId)).thenReturn(Optional.empty());

            // Act
            Optional<Node> result = service.findById(nodeId);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("should return all nodes")
        void shouldReturnAllNodes() {
            // Arrange
            Node node1 = createHealthyNode("node-1", 50, 100);
            Node node2 = createHealthyNode("node-2", 30, 60);
            when(nodeRepository.findAll()).thenReturn(List.of(node1, node2));

            // Act
            List<Node> result = service.findAll();

            // Assert
            assertThat(result).containsExactly(node1, node2);
        }

        @Test
        @DisplayName("should return empty list when no nodes")
        void shouldReturnEmptyListWhenNoNodes() {
            // Arrange
            when(nodeRepository.findAll()).thenReturn(List.of());

            // Act
            List<Node> result = service.findAll();

            // Assert
            assertThat(result).isEmpty();
        }
    }

    // Helper methods

    private Node createHealthyNode(String nodeId, int containerCount, int matchCount) {
        return Node.register(NodeId.of(nodeId), "http://" + nodeId + ":8080", new NodeCapacity(100))
                .withHeartbeat(new NodeMetrics(containerCount, matchCount, 0.5, 256, 512));
    }

    private Node createDrainingNode(String nodeId, int containerCount, int matchCount) {
        return createHealthyNode(nodeId, containerCount, matchCount).drain();
    }
}
