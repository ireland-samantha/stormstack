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

package ca.samanthaireland.lightning.controlplane.provider.redis;

import ca.samanthaireland.lightning.controlplane.node.model.Node;
import ca.samanthaireland.lightning.controlplane.node.model.NodeCapacity;
import ca.samanthaireland.lightning.controlplane.node.model.NodeId;
import ca.samanthaireland.lightning.controlplane.node.model.NodeMetrics;
import ca.samanthaireland.lightning.controlplane.node.repository.NodeRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for RedisNodeRepository.
 */
@QuarkusTest
@TestProfile(RedisTestProfile.class)
class RedisNodeRepositoryIT {

    @Inject
    NodeRepository nodeRepository;

    @BeforeEach
    void setUp() {
        // Clean up any existing test data
        nodeRepository.findAll().forEach(node -> nodeRepository.deleteById(node.nodeId()));
    }

    @Test
    void save_persistsNode() {
        // Arrange
        Node node = Node.register("test-node-1", "http://localhost:8080", new NodeCapacity(100));

        // Act
        Node saved = nodeRepository.save(node, 60);

        // Assert
        assertThat(saved.nodeId()).isEqualTo(NodeId.of("test-node-1"));
        assertThat(nodeRepository.existsById(NodeId.of("test-node-1"))).isTrue();
    }

    @Test
    void findById_existingNode_returnsNode() {
        // Arrange
        Node node = Node.register("test-node-2", "http://localhost:8081", new NodeCapacity(50));
        nodeRepository.save(node, 60);

        // Act
        Optional<Node> found = nodeRepository.findById(NodeId.of("test-node-2"));

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().nodeId()).isEqualTo(NodeId.of("test-node-2"));
        assertThat(found.get().advertiseAddress()).isEqualTo("http://localhost:8081");
        assertThat(found.get().capacity().maxContainers()).isEqualTo(50);
    }

    @Test
    void findById_nonExistent_returnsEmpty() {
        // Act
        Optional<Node> found = nodeRepository.findById(NodeId.of("non-existent"));

        // Assert
        assertThat(found).isEmpty();
    }

    @Test
    void findAll_returnsAllNodes() {
        // Arrange
        Node node1 = Node.register("node-a", "http://localhost:8080", new NodeCapacity(100));
        Node node2 = Node.register("node-b", "http://localhost:8081", new NodeCapacity(200));
        nodeRepository.save(node1, 60);
        nodeRepository.save(node2, 60);

        // Act
        List<Node> all = nodeRepository.findAll();

        // Assert
        assertThat(all).hasSize(2);
        assertThat(all).extracting(Node::nodeId).containsExactlyInAnyOrder(NodeId.of("node-a"), NodeId.of("node-b"));
    }

    @Test
    void deleteById_removesNode() {
        // Arrange
        Node node = Node.register("to-delete", "http://localhost:8080", new NodeCapacity(100));
        nodeRepository.save(node, 60);
        assertThat(nodeRepository.existsById(NodeId.of("to-delete"))).isTrue();

        // Act
        nodeRepository.deleteById(NodeId.of("to-delete"));

        // Assert
        assertThat(nodeRepository.existsById(NodeId.of("to-delete"))).isFalse();
    }

    @Test
    void existsById_existingNode_returnsTrue() {
        // Arrange
        Node node = Node.register("existing-node", "http://localhost:8080", new NodeCapacity(100));
        nodeRepository.save(node, 60);

        // Act & Assert
        assertThat(nodeRepository.existsById(NodeId.of("existing-node"))).isTrue();
    }

    @Test
    void existsById_nonExistent_returnsFalse() {
        // Act & Assert
        assertThat(nodeRepository.existsById(NodeId.of("non-existent"))).isFalse();
    }

    @Test
    void save_updatesExistingNode() {
        // Arrange
        Node original = Node.register("update-node", "http://localhost:8080", new NodeCapacity(100));
        nodeRepository.save(original, 60);

        Node updated = original.withHeartbeat(new NodeMetrics(10, 5, 0.5, 1024, 4096));

        // Act
        nodeRepository.save(updated, 60);
        Optional<Node> found = nodeRepository.findById(NodeId.of("update-node"));

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().metrics().containerCount()).isEqualTo(10);
        assertThat(found.get().metrics().matchCount()).isEqualTo(5);
    }

    @Test
    void save_preservesNodeStatus() {
        // Arrange
        Node node = Node.register("status-test", "http://localhost:8080", new NodeCapacity(100));
        Node draining = node.drain();
        nodeRepository.save(draining, 60);

        // Act
        Optional<Node> found = nodeRepository.findById(NodeId.of("status-test"));

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().status()).isEqualTo(ca.samanthaireland.lightning.controlplane.node.model.NodeStatus.DRAINING);
    }
}
