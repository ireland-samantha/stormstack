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

import ca.samanthaireland.stormstack.thunder.controlplane.node.exception.NodeNotFoundException;
import ca.samanthaireland.stormstack.thunder.controlplane.node.model.Node;
import ca.samanthaireland.stormstack.thunder.controlplane.node.model.NodeCapacity;
import ca.samanthaireland.stormstack.thunder.controlplane.node.model.NodeId;
import ca.samanthaireland.stormstack.thunder.controlplane.node.model.NodeMetrics;
import ca.samanthaireland.stormstack.thunder.controlplane.node.model.NodeStatus;
import ca.samanthaireland.stormstack.thunder.controlplane.node.service.NodeRegistryService;
import ca.samanthaireland.stormstack.thunder.controlplane.provider.dto.HeartbeatRequest;
import ca.samanthaireland.stormstack.thunder.controlplane.provider.dto.NodeCapacityDto;
import ca.samanthaireland.stormstack.thunder.controlplane.provider.dto.NodeMetricsDto;
import ca.samanthaireland.stormstack.thunder.controlplane.provider.dto.NodeRegistrationRequest;
import ca.samanthaireland.stormstack.thunder.controlplane.provider.dto.NodeResponse;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link NodeResource}.
 *
 * <p>Authentication is now handled by the {@code @Scopes} filter, so these tests
 * focus on the resource's business logic without auth token handling.
 */
@ExtendWith(MockitoExtension.class)
class NodeResourceTest {

    @Mock
    private NodeRegistryService nodeRegistryService;

    private NodeResource resource;

    private static final String NODE_ID_STR = "node-1";
    private static final NodeId NODE_ID = NodeId.of(NODE_ID_STR);
    private static final String ADDRESS = "http://node-1:8080";

    @BeforeEach
    void setUp() {
        resource = new NodeResource(nodeRegistryService);
    }

    private Node createTestNode() {
        return new Node(
                NODE_ID,
                ADDRESS,
                NodeStatus.HEALTHY,
                new NodeCapacity(100),
                NodeMetrics.empty(),
                Instant.now(),
                Instant.now()
        );
    }

    @Nested
    class Register {

        @Test
        void register_withValidRequest_returns201() {
            // Arrange
            Node node = createTestNode();
            when(nodeRegistryService.register(eq(NODE_ID), eq(ADDRESS), any())).thenReturn(node);

            NodeRegistrationRequest request = new NodeRegistrationRequest(
                    NODE_ID_STR,
                    ADDRESS,
                    new NodeCapacityDto(100)
            );

            // Act
            Response response = resource.register(request);

            // Assert
            assertThat(response.getStatus()).isEqualTo(201);
            assertThat(response.getLocation().toString()).contains(NODE_ID_STR);

            NodeResponse body = (NodeResponse) response.getEntity();
            assertThat(body.nodeId()).isEqualTo(NODE_ID_STR);
            assertThat(body.advertiseAddress()).isEqualTo(ADDRESS);
            assertThat(body.status()).isEqualTo(NodeStatus.HEALTHY);
        }

        @Test
        void register_callsServiceWithCorrectParameters() {
            // Arrange
            Node node = createTestNode();
            when(nodeRegistryService.register(eq(NODE_ID), eq(ADDRESS), any())).thenReturn(node);

            NodeRegistrationRequest request = new NodeRegistrationRequest(
                    NODE_ID_STR,
                    ADDRESS,
                    new NodeCapacityDto(100)
            );

            // Act
            resource.register(request);

            // Assert
            verify(nodeRegistryService).register(eq(NODE_ID), eq(ADDRESS), any(NodeCapacity.class));
        }
    }

    @Nested
    class Heartbeat {

        @Test
        void heartbeat_withValidRequest_returns200() {
            // Arrange
            Node node = createTestNode();
            when(nodeRegistryService.heartbeat(eq(NODE_ID), any())).thenReturn(node);

            HeartbeatRequest request = new HeartbeatRequest(
                    new NodeMetricsDto(5, 3, 0.5, 1024, 4096)
            );

            // Act
            Response response = resource.heartbeat(NODE_ID_STR, request);

            // Assert
            assertThat(response.getStatus()).isEqualTo(200);

            NodeResponse body = (NodeResponse) response.getEntity();
            assertThat(body.nodeId()).isEqualTo(NODE_ID_STR);
        }

        @Test
        void heartbeat_nonExistentNode_throwsException() {
            // Arrange
            when(nodeRegistryService.heartbeat(eq(NodeId.of("unknown")), any()))
                    .thenThrow(new NodeNotFoundException(NodeId.of("unknown")));

            HeartbeatRequest request = new HeartbeatRequest(
                    new NodeMetricsDto(0, 0, 0.0, 0, 0)
            );

            // Act & Assert
            assertThatThrownBy(() -> resource.heartbeat("unknown", request))
                    .isInstanceOf(NodeNotFoundException.class);
        }

        @Test
        void heartbeat_callsServiceWithCorrectParameters() {
            // Arrange
            Node node = createTestNode();
            when(nodeRegistryService.heartbeat(eq(NODE_ID), any())).thenReturn(node);

            HeartbeatRequest request = new HeartbeatRequest(
                    new NodeMetricsDto(5, 3, 0.5, 1024, 4096)
            );

            // Act
            resource.heartbeat(NODE_ID_STR, request);

            // Assert
            verify(nodeRegistryService).heartbeat(eq(NODE_ID), any(NodeMetrics.class));
        }
    }

    @Nested
    class Drain {

        @Test
        void drain_existingNode_returns200() {
            // Arrange
            Node drainingNode = new Node(
                    NODE_ID,
                    ADDRESS,
                    NodeStatus.DRAINING,
                    new NodeCapacity(100),
                    NodeMetrics.empty(),
                    Instant.now(),
                    Instant.now()
            );
            when(nodeRegistryService.drain(NODE_ID)).thenReturn(drainingNode);

            // Act
            Response response = resource.drain(NODE_ID_STR);

            // Assert
            assertThat(response.getStatus()).isEqualTo(200);

            NodeResponse body = (NodeResponse) response.getEntity();
            assertThat(body.nodeId()).isEqualTo(NODE_ID_STR);
            assertThat(body.status()).isEqualTo(NodeStatus.DRAINING);
        }

        @Test
        void drain_nonExistentNode_throwsException() {
            // Arrange
            when(nodeRegistryService.drain(NodeId.of("unknown")))
                    .thenThrow(new NodeNotFoundException(NodeId.of("unknown")));

            // Act & Assert
            assertThatThrownBy(() -> resource.drain("unknown"))
                    .isInstanceOf(NodeNotFoundException.class);
        }
    }

    @Nested
    class Deregister {

        @Test
        void deregister_existingNode_returns204() {
            // Arrange
            doNothing().when(nodeRegistryService).deregister(NODE_ID);

            // Act
            Response response = resource.deregister(NODE_ID_STR);

            // Assert
            assertThat(response.getStatus()).isEqualTo(204);
            verify(nodeRegistryService).deregister(NODE_ID);
        }

        @Test
        void deregister_nonExistentNode_throwsException() {
            // Arrange
            doThrow(new NodeNotFoundException(NodeId.of("unknown")))
                    .when(nodeRegistryService).deregister(NodeId.of("unknown"));

            // Act & Assert
            assertThatThrownBy(() -> resource.deregister("unknown"))
                    .isInstanceOf(NodeNotFoundException.class);
        }
    }
}
