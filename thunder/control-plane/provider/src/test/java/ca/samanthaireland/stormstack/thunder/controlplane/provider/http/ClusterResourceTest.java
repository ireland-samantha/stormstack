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

package ca.samanthaireland.stormstack.thunder.controlplane.provider.http;

import ca.samanthaireland.stormstack.thunder.controlplane.cluster.model.ClusterStatus;
import ca.samanthaireland.stormstack.thunder.controlplane.cluster.service.ClusterService;
import ca.samanthaireland.stormstack.thunder.controlplane.node.exception.NodeNotFoundException;
import ca.samanthaireland.stormstack.thunder.controlplane.node.model.Node;
import ca.samanthaireland.stormstack.thunder.controlplane.node.model.NodeCapacity;
import ca.samanthaireland.stormstack.thunder.controlplane.node.model.NodeId;
import ca.samanthaireland.stormstack.thunder.controlplane.node.model.NodeMetrics;
import ca.samanthaireland.stormstack.thunder.controlplane.node.model.NodeStatus;
import ca.samanthaireland.stormstack.thunder.controlplane.provider.dto.NodeResponse;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClusterResourceTest {

    @Mock
    private ClusterService clusterService;

    private ClusterResource resource;

    private static final String NODE_ID = "node-1";

    @BeforeEach
    void setUp() {
        resource = new ClusterResource(clusterService);
    }

    private Node createTestNode(String nodeId) {
        return new Node(
                NodeId.of(nodeId),
                "http://" + nodeId + ":8080",
                NodeStatus.HEALTHY,
                new NodeCapacity(100),
                new NodeMetrics(5, 3, 0.5, 1024, 4096),
                Instant.now(),
                Instant.now()
        );
    }

    @Nested
    class GetAllNodes {

        @Test
        void getAllNodes_returnsAllNodes() {
            // Arrange
            Node node1 = createTestNode("node-1");
            Node node2 = createTestNode("node-2");
            when(clusterService.getAllNodes()).thenReturn(List.of(node1, node2));

            // Act
            List<NodeResponse> response = resource.getAllNodes();

            // Assert
            assertThat(response).hasSize(2);
            assertThat(response.get(0).nodeId()).isEqualTo("node-1");
            assertThat(response.get(1).nodeId()).isEqualTo("node-2");
        }

        @Test
        void getAllNodes_emptyCluster_returnsEmptyList() {
            // Arrange
            when(clusterService.getAllNodes()).thenReturn(List.of());

            // Act
            List<NodeResponse> response = resource.getAllNodes();

            // Assert
            assertThat(response).isEmpty();
        }
    }

    @Nested
    class GetNode {

        @Test
        void getNode_existingNode_returnsNode() {
            // Arrange
            Node node = createTestNode(NODE_ID);
            when(clusterService.getNode(NodeId.of(NODE_ID))).thenReturn(Optional.of(node));

            // Act
            Response response = resource.getNode(NODE_ID);

            // Assert
            assertThat(response.getStatus()).isEqualTo(200);

            NodeResponse body = (NodeResponse) response.getEntity();
            assertThat(body.nodeId()).isEqualTo(NODE_ID);
            assertThat(body.status()).isEqualTo(NodeStatus.HEALTHY);
        }

        @Test
        void getNode_nonExistentNode_throwsException() {
            // Arrange
            when(clusterService.getNode(NodeId.of("unknown"))).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> resource.getNode("unknown"))
                    .isInstanceOf(NodeNotFoundException.class);
        }
    }

    @Nested
    class GetClusterStatus {

        @Test
        void getClusterStatus_returnsStatus() {
            // Arrange
            ClusterStatus status = new ClusterStatus(
                    5,      // totalNodes
                    4,      // healthyNodes
                    1,      // drainingNodes
                    20,     // totalContainers
                    15,     // totalMatches
                    500,    // totalCapacity
                    480     // availableCapacity
            );
            when(clusterService.getClusterStatus()).thenReturn(status);

            // Act
            ClusterStatus response = resource.getClusterStatus();

            // Assert
            assertThat(response.totalNodes()).isEqualTo(5);
            assertThat(response.healthyNodes()).isEqualTo(4);
            assertThat(response.drainingNodes()).isEqualTo(1);
            assertThat(response.totalContainers()).isEqualTo(20);
            assertThat(response.totalMatches()).isEqualTo(15);
            assertThat(response.totalCapacity()).isEqualTo(500);
            assertThat(response.availableCapacity()).isEqualTo(480);
        }

        @Test
        void getClusterStatus_emptyCluster_returnsZeros() {
            // Arrange
            ClusterStatus status = new ClusterStatus(0, 0, 0, 0, 0, 0, 0);
            when(clusterService.getClusterStatus()).thenReturn(status);

            // Act
            ClusterStatus response = resource.getClusterStatus();

            // Assert
            assertThat(response.totalNodes()).isZero();
            assertThat(response.healthyNodes()).isZero();
            assertThat(response.availableCapacity()).isZero();
        }
    }
}
