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

package ca.samanthaireland.stormstack.thunder.controlplane.match.repository;

import ca.samanthaireland.stormstack.thunder.controlplane.match.model.ClusterMatchId;
import ca.samanthaireland.stormstack.thunder.controlplane.match.model.MatchRegistryEntry;
import ca.samanthaireland.stormstack.thunder.controlplane.match.model.MatchStatus;
import ca.samanthaireland.stormstack.thunder.controlplane.node.model.NodeId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract tests for {@link MatchRegistry} interface.
 *
 * <p>These tests verify the expected behavior of any MatchRegistry implementation.
 * They use an in-memory implementation to test the interface contract without
 * requiring external dependencies like Redis.
 */
@DisplayName("MatchRegistry Contract Tests")
class MatchRegistryContractTest {

    private MatchRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InMemoryMatchRegistry();
    }

    @Test
    @DisplayName("save() should persist a new match")
    void save_shouldPersistNewMatch() {
        MatchRegistryEntry entry = createTestEntry("node-1", 1L, 1L);

        registry.save(entry);

        Optional<MatchRegistryEntry> found = registry.findById(ClusterMatchId.of("node-1", 1L, 1L));
        assertThat(found).isPresent();
        assertThat(found.get().matchId()).isEqualTo(ClusterMatchId.of("node-1", 1L, 1L));
    }

    @Test
    @DisplayName("save() should update an existing match")
    void save_shouldUpdateExistingMatch() {
        MatchRegistryEntry original = createTestEntry("node-1", 1L, 1L);
        registry.save(original);

        MatchRegistryEntry updated = original.finished();
        registry.save(updated);

        Optional<MatchRegistryEntry> found = registry.findById(ClusterMatchId.of("node-1", 1L, 1L));
        assertThat(found).isPresent();
        assertThat(found.get().status()).isEqualTo(MatchStatus.FINISHED);
    }

    @Test
    @DisplayName("findById() should return empty for non-existent match")
    void findById_shouldReturnEmptyForNonExistent() {
        Optional<MatchRegistryEntry> found = registry.findById(ClusterMatchId.of("non-existent", 1L, 1L));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findByNodeId() should return matches for a node")
    void findByNodeId_shouldReturnMatchesForNode() {
        registry.save(createTestEntry("node-1", 1L, 1L));
        registry.save(createTestEntry("node-1", 1L, 2L));
        registry.save(createTestEntry("node-2", 1L, 1L));

        var node1Matches = registry.findByNodeId(NodeId.of("node-1"));
        var node2Matches = registry.findByNodeId(NodeId.of("node-2"));

        assertThat(node1Matches).hasSize(2);
        assertThat(node2Matches).hasSize(1);
    }

    @Test
    @DisplayName("findByStatus() should filter matches by status")
    void findByStatus_shouldFilterByStatus() {
        registry.save(createTestEntry("node-1", 1L, 1L).running());
        registry.save(createTestEntry("node-1", 1L, 2L)); // CREATING
        registry.save(createTestEntry("node-2", 1L, 1L).running());

        var runningMatches = registry.findByStatus(MatchStatus.RUNNING);
        var creatingMatches = registry.findByStatus(MatchStatus.CREATING);

        assertThat(runningMatches).hasSize(2);
        assertThat(creatingMatches).hasSize(1);
    }

    @Test
    @DisplayName("findAll() should return all matches")
    void findAll_shouldReturnAllMatches() {
        registry.save(createTestEntry("node-1", 1L, 1L));
        registry.save(createTestEntry("node-2", 1L, 1L));
        registry.save(createTestEntry("node-3", 1L, 1L));

        var matches = registry.findAll();

        assertThat(matches).hasSize(3);
    }

    @Test
    @DisplayName("deleteById() should remove a match")
    void deleteById_shouldRemoveMatch() {
        registry.save(createTestEntry("node-1", 1L, 1L));

        registry.deleteById(ClusterMatchId.of("node-1", 1L, 1L));

        assertThat(registry.findById(ClusterMatchId.of("node-1", 1L, 1L))).isEmpty();
    }

    @Test
    @DisplayName("deleteByNodeId() should remove all matches for a node")
    void deleteByNodeId_shouldRemoveAllMatchesForNode() {
        registry.save(createTestEntry("node-1", 1L, 1L));
        registry.save(createTestEntry("node-1", 1L, 2L));
        registry.save(createTestEntry("node-2", 1L, 1L));

        registry.deleteByNodeId(NodeId.of("node-1"));

        assertThat(registry.findByNodeId(NodeId.of("node-1"))).isEmpty();
        assertThat(registry.findByNodeId(NodeId.of("node-2"))).hasSize(1);
    }

    @Test
    @DisplayName("existsById() should return true for existing match")
    void existsById_shouldReturnTrueForExisting() {
        registry.save(createTestEntry("node-1", 1L, 1L));

        assertThat(registry.existsById(ClusterMatchId.of("node-1", 1L, 1L))).isTrue();
    }

    @Test
    @DisplayName("existsById() should return false for non-existent match")
    void existsById_shouldReturnFalseForNonExistent() {
        assertThat(registry.existsById(ClusterMatchId.of("non-existent", 1L, 1L))).isFalse();
    }

    @Test
    @DisplayName("countActive() should return count of CREATING and RUNNING matches")
    void countActive_shouldReturnCorrectCount() {
        registry.save(createTestEntry("node-1", 1L, 1L).running());
        registry.save(createTestEntry("node-1", 1L, 2L)); // CREATING
        registry.save(createTestEntry("node-2", 1L, 1L).finished());

        assertThat(registry.countActive()).isEqualTo(2);
    }

    @Test
    @DisplayName("countActiveByNodeId() should return active matches for a node")
    void countActiveByNodeId_shouldReturnCorrectCount() {
        registry.save(createTestEntry("node-1", 1L, 1L).running());
        registry.save(createTestEntry("node-1", 1L, 2L).finished());
        registry.save(createTestEntry("node-2", 1L, 1L).running());

        assertThat(registry.countActiveByNodeId(NodeId.of("node-1"))).isEqualTo(1);
        assertThat(registry.countActiveByNodeId(NodeId.of("node-2"))).isEqualTo(1);
        assertThat(registry.countActiveByNodeId(NodeId.of("node-3"))).isEqualTo(0);
    }

    private MatchRegistryEntry createTestEntry(String nodeId, long containerId, long matchId) {
        return MatchRegistryEntry.creating(
                ClusterMatchId.of(nodeId, containerId, matchId),
                NodeId.of(nodeId),
                containerId,
                List.of("EntityModule", "HealthModule"),
                "http://localhost:8080"
        );
    }

    /**
     * In-memory implementation for contract testing.
     */
    private static class InMemoryMatchRegistry implements MatchRegistry {
        private final Map<ClusterMatchId, MatchRegistryEntry> matches = new HashMap<>();

        @Override
        public MatchRegistryEntry save(MatchRegistryEntry entry) {
            matches.put(entry.matchId(), entry);
            return entry;
        }

        @Override
        public Optional<MatchRegistryEntry> findById(ClusterMatchId matchId) {
            return Optional.ofNullable(matches.get(matchId));
        }

        @Override
        public List<MatchRegistryEntry> findAll() {
            return new ArrayList<>(matches.values());
        }

        @Override
        public List<MatchRegistryEntry> findByNodeId(NodeId nodeId) {
            return matches.values().stream()
                    .filter(m -> m.nodeId().equals(nodeId))
                    .toList();
        }

        @Override
        public List<MatchRegistryEntry> findByStatus(MatchStatus status) {
            return matches.values().stream()
                    .filter(m -> m.status() == status)
                    .toList();
        }

        @Override
        public void deleteById(ClusterMatchId matchId) {
            matches.remove(matchId);
        }

        @Override
        public void deleteByNodeId(NodeId nodeId) {
            matches.entrySet().removeIf(e -> e.getValue().nodeId().equals(nodeId));
        }

        @Override
        public boolean existsById(ClusterMatchId matchId) {
            return matches.containsKey(matchId);
        }

        @Override
        public long countActive() {
            return matches.values().stream()
                    .filter(m -> m.status() == MatchStatus.CREATING || m.status() == MatchStatus.RUNNING)
                    .count();
        }

        @Override
        public long countActiveByNodeId(NodeId nodeId) {
            return matches.values().stream()
                    .filter(m -> m.nodeId().equals(nodeId))
                    .filter(m -> m.status() == MatchStatus.CREATING || m.status() == MatchStatus.RUNNING)
                    .count();
        }
    }
}
