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

import ca.samanthaireland.lightning.controlplane.match.model.ClusterMatchId;
import ca.samanthaireland.lightning.controlplane.match.model.MatchRegistryEntry;
import ca.samanthaireland.lightning.controlplane.match.model.MatchStatus;
import ca.samanthaireland.lightning.controlplane.match.repository.MatchRegistry;
import ca.samanthaireland.lightning.controlplane.node.model.NodeId;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for RedisMatchRegistry.
 */
@QuarkusTest
@TestProfile(RedisTestProfile.class)
class RedisMatchRegistryIT {

    @Inject
    MatchRegistry matchRegistry;

    @BeforeEach
    void setUp() {
        // Clean up any existing test data
        matchRegistry.findAll().forEach(match -> matchRegistry.deleteById(match.matchId()));
    }

    private MatchRegistryEntry createTestEntry(String matchId, String nodeId, MatchStatus status) {
        return new MatchRegistryEntry(
                ClusterMatchId.fromString(matchId),
                NodeId.of(nodeId),
                1L,
                status,
                Instant.now(),
                List.of("entity-module", "grid-map-module"),
                "http://localhost:8080",
                "ws://localhost:8080/ws/match/" + matchId,
                0,
                0  // playerLimit
        );
    }

    @Test
    void save_persistsMatch() {
        // Arrange
        MatchRegistryEntry entry = createTestEntry("match-1", "node-1", MatchStatus.RUNNING);

        // Act
        MatchRegistryEntry saved = matchRegistry.save(entry);

        // Assert
        assertThat(saved.matchId()).isEqualTo(ClusterMatchId.fromString("match-1"));
        assertThat(matchRegistry.existsById(ClusterMatchId.fromString("match-1"))).isTrue();
    }

    @Test
    void findById_existingMatch_returnsMatch() {
        // Arrange
        MatchRegistryEntry entry = createTestEntry("match-2", "node-2", MatchStatus.RUNNING);
        matchRegistry.save(entry);

        // Act
        Optional<MatchRegistryEntry> found = matchRegistry.findById(ClusterMatchId.fromString("match-2"));

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().matchId()).isEqualTo(ClusterMatchId.fromString("match-2"));
        assertThat(found.get().nodeId()).isEqualTo(NodeId.of("node-2"));
        assertThat(found.get().status()).isEqualTo(MatchStatus.RUNNING);
    }

    @Test
    void findById_nonExistent_returnsEmpty() {
        // Act
        Optional<MatchRegistryEntry> found = matchRegistry.findById(ClusterMatchId.fromString("non-existent"));

        // Assert
        assertThat(found).isEmpty();
    }

    @Test
    void findAll_returnsAllMatches() {
        // Arrange
        matchRegistry.save(createTestEntry("match-a", "node-1", MatchStatus.RUNNING));
        matchRegistry.save(createTestEntry("match-b", "node-2", MatchStatus.CREATING));
        matchRegistry.save(createTestEntry("match-c", "node-1", MatchStatus.FINISHED));

        // Act
        List<MatchRegistryEntry> all = matchRegistry.findAll();

        // Assert
        assertThat(all).hasSize(3);
        assertThat(all).extracting(MatchRegistryEntry::matchId)
                .containsExactlyInAnyOrder(
                        ClusterMatchId.fromString("match-a"),
                        ClusterMatchId.fromString("match-b"),
                        ClusterMatchId.fromString("match-c")
                );
    }

    @Test
    void findByNodeId_returnsMatchesForNode() {
        // Arrange
        matchRegistry.save(createTestEntry("match-1", "node-1", MatchStatus.RUNNING));
        matchRegistry.save(createTestEntry("match-2", "node-1", MatchStatus.RUNNING));
        matchRegistry.save(createTestEntry("match-3", "node-2", MatchStatus.RUNNING));

        // Act
        List<MatchRegistryEntry> node1Matches = matchRegistry.findByNodeId(NodeId.of("node-1"));

        // Assert
        assertThat(node1Matches).hasSize(2);
        assertThat(node1Matches).allMatch(m -> m.nodeId().equals(NodeId.of("node-1")));
    }

    @Test
    void findByStatus_returnsMatchesWithStatus() {
        // Arrange
        matchRegistry.save(createTestEntry("match-1", "node-1", MatchStatus.RUNNING));
        matchRegistry.save(createTestEntry("match-2", "node-2", MatchStatus.RUNNING));
        matchRegistry.save(createTestEntry("match-3", "node-1", MatchStatus.FINISHED));

        // Act
        List<MatchRegistryEntry> running = matchRegistry.findByStatus(MatchStatus.RUNNING);

        // Assert
        assertThat(running).hasSize(2);
        assertThat(running).allMatch(m -> m.status() == MatchStatus.RUNNING);
    }

    @Test
    void deleteById_removesMatch() {
        // Arrange
        matchRegistry.save(createTestEntry("to-delete", "node-1", MatchStatus.RUNNING));
        assertThat(matchRegistry.existsById(ClusterMatchId.fromString("to-delete"))).isTrue();

        // Act
        matchRegistry.deleteById(ClusterMatchId.fromString("to-delete"));

        // Assert
        assertThat(matchRegistry.existsById(ClusterMatchId.fromString("to-delete"))).isFalse();
    }

    @Test
    void deleteByNodeId_removesAllMatchesForNode() {
        // Arrange
        matchRegistry.save(createTestEntry("match-1", "node-to-clean", MatchStatus.RUNNING));
        matchRegistry.save(createTestEntry("match-2", "node-to-clean", MatchStatus.RUNNING));
        matchRegistry.save(createTestEntry("match-3", "other-node", MatchStatus.RUNNING));

        // Act
        matchRegistry.deleteByNodeId(NodeId.of("node-to-clean"));

        // Assert
        assertThat(matchRegistry.findByNodeId(NodeId.of("node-to-clean"))).isEmpty();
        assertThat(matchRegistry.existsById(ClusterMatchId.fromString("match-3"))).isTrue();
    }

    @Test
    void countActive_countsRunningAndCreating() {
        // Arrange
        matchRegistry.save(createTestEntry("match-1", "node-1", MatchStatus.RUNNING));
        matchRegistry.save(createTestEntry("match-2", "node-2", MatchStatus.CREATING));
        matchRegistry.save(createTestEntry("match-3", "node-1", MatchStatus.FINISHED));
        matchRegistry.save(createTestEntry("match-4", "node-2", MatchStatus.ERROR));

        // Act
        long active = matchRegistry.countActive();

        // Assert
        assertThat(active).isEqualTo(2); // Only RUNNING and CREATING
    }

    @Test
    void countActiveByNodeId_countsActiveMatchesForNode() {
        // Arrange
        matchRegistry.save(createTestEntry("match-1", "count-node", MatchStatus.RUNNING));
        matchRegistry.save(createTestEntry("match-2", "count-node", MatchStatus.CREATING));
        matchRegistry.save(createTestEntry("match-3", "count-node", MatchStatus.FINISHED));
        matchRegistry.save(createTestEntry("match-4", "other-node", MatchStatus.RUNNING));

        // Act
        long active = matchRegistry.countActiveByNodeId(NodeId.of("count-node"));

        // Assert
        assertThat(active).isEqualTo(2);
    }

    @Test
    void save_preservesModuleNames() {
        // Arrange
        List<String> modules = List.of("entity-module", "grid-map-module", "health-module");
        MatchRegistryEntry entry = new MatchRegistryEntry(
                ClusterMatchId.fromString("modules-test"),
                NodeId.of("node-1"),
                1L,
                MatchStatus.RUNNING,
                Instant.now(),
                modules,
                "http://localhost:8080",
                "ws://localhost:8080/ws",
                0,
                0  // playerLimit
        );
        matchRegistry.save(entry);

        // Act
        Optional<MatchRegistryEntry> found = matchRegistry.findById(ClusterMatchId.fromString("modules-test"));

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().moduleNames()).containsExactlyElementsOf(modules);
    }

    @Test
    void save_preservesPlayerCount() {
        // Arrange
        MatchRegistryEntry entry = new MatchRegistryEntry(
                ClusterMatchId.fromString("player-test"),
                NodeId.of("node-1"),
                1L,
                MatchStatus.RUNNING,
                Instant.now(),
                List.of("entity-module"),
                "http://localhost:8080",
                "ws://localhost:8080/ws",
                25,
                0  // playerLimit
        );
        matchRegistry.save(entry);

        // Act
        Optional<MatchRegistryEntry> found = matchRegistry.findById(ClusterMatchId.fromString("player-test"));

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().playerCount()).isEqualTo(25);
    }
}
