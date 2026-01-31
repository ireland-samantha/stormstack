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

package ca.samanthaireland.stormstack.thunder.controlplane.match.service;

import ca.samanthaireland.stormstack.thunder.controlplane.client.LightningNodeClient;
import ca.samanthaireland.stormstack.thunder.controlplane.match.exception.MatchNotFoundException;
import ca.samanthaireland.stormstack.thunder.controlplane.match.model.ClusterMatchId;
import ca.samanthaireland.stormstack.thunder.controlplane.match.model.MatchRegistryEntry;
import ca.samanthaireland.stormstack.thunder.controlplane.match.model.MatchStatus;
import ca.samanthaireland.stormstack.thunder.controlplane.match.repository.MatchRegistry;
import ca.samanthaireland.stormstack.thunder.controlplane.node.model.Node;
import ca.samanthaireland.stormstack.thunder.controlplane.node.model.NodeCapacity;
import ca.samanthaireland.stormstack.thunder.controlplane.node.model.NodeId;
import ca.samanthaireland.stormstack.thunder.controlplane.scheduler.service.SchedulerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Implementation of MatchRoutingService that coordinates between scheduler,
 * Lightning Engine nodes, and the match registry.
 *
 * <p>This is a pure domain implementation with no framework dependencies.
 * Dependencies are provided via constructor injection.
 */
public class MatchRoutingServiceImpl implements MatchRoutingService {
    private static final Logger log = LoggerFactory.getLogger(MatchRoutingServiceImpl.class);

    private final SchedulerService schedulerService;
    private final LightningNodeClient nodeClient;
    private final MatchRegistry matchRegistry;

    /**
     * Creates a new MatchRoutingServiceImpl.
     *
     * @param schedulerService the scheduler service for node selection
     * @param nodeClient       the client for communicating with nodes
     * @param matchRegistry    the match registry
     */
    public MatchRoutingServiceImpl(
            SchedulerService schedulerService,
            LightningNodeClient nodeClient,
            MatchRegistry matchRegistry
    ) {
        this.schedulerService = schedulerService;
        this.nodeClient = nodeClient;
        this.matchRegistry = matchRegistry;
    }

    @Override
    public MatchRegistryEntry createMatch(List<String> moduleNames, NodeId preferredNodeId, int playerLimit) {
        // 1. Select a node
        Node selectedNode = schedulerService.selectNodeForMatch(moduleNames, preferredNodeId);
        log.info("Scheduler selected node {} for new match with modules {}",
                selectedNode.nodeId(), moduleNames);

        // 2. Create container on the node
        long containerId = nodeClient.createContainer(selectedNode, moduleNames);

        // 3. Create match in the container
        long nodeMatchId = nodeClient.createMatch(selectedNode, containerId, moduleNames);

        // 4. Generate a cluster-unique match ID
        ClusterMatchId clusterMatchId = ClusterMatchId.of(selectedNode.nodeId(), containerId, nodeMatchId);

        // 5. Create and save registry entry
        MatchRegistryEntry entry = MatchRegistryEntry.creating(
                clusterMatchId,
                selectedNode.nodeId(),
                containerId,
                moduleNames,
                selectedNode.advertiseAddress(),
                playerLimit
        ).running();

        matchRegistry.save(entry);

        log.info("Created match {} on node {} in container {} with playerLimit {}",
                clusterMatchId, selectedNode.nodeId(), containerId, playerLimit);

        return entry;
    }

    @Override
    public Optional<MatchRegistryEntry> findById(ClusterMatchId matchId) {
        return matchRegistry.findById(matchId);
    }

    @Override
    public List<MatchRegistryEntry> findAll() {
        return matchRegistry.findAll();
    }

    @Override
    public List<MatchRegistryEntry> findByStatus(MatchStatus status) {
        return matchRegistry.findByStatus(status);
    }

    @Override
    public void deleteMatch(ClusterMatchId matchId) {
        MatchRegistryEntry entry = matchRegistry.findById(matchId)
                .orElseThrow(() -> new MatchNotFoundException(matchId));

        // Get the node match ID from the cluster match ID
        long nodeMatchId = matchId.localMatchId();

        // Find the node
        // Decision: We store the match info in registry, so we can get node address from entry
        try {
            // Create a minimal node for the client call
            Node node = createNodeForClient(entry);
            nodeClient.deleteMatch(node, entry.containerId(), nodeMatchId);
        } catch (Exception e) {
            log.warn("Failed to delete match {} from node: {}", matchId, e.getMessage());
            // Continue with registry deletion even if node deletion fails
        }

        matchRegistry.deleteById(matchId);
        log.info("Deleted match {}", matchId);
    }

    @Override
    public void updatePlayerCount(ClusterMatchId matchId, int playerCount) {
        MatchRegistryEntry entry = matchRegistry.findById(matchId)
                .orElseThrow(() -> new MatchNotFoundException(matchId));

        MatchRegistryEntry updated = entry.withPlayerCount(playerCount);
        matchRegistry.save(updated);

        log.debug("Updated player count for match {} to {}", matchId, playerCount);
    }

    @Override
    public void finishMatch(ClusterMatchId matchId) {
        MatchRegistryEntry entry = matchRegistry.findById(matchId)
                .orElseThrow(() -> new MatchNotFoundException(matchId));

        MatchRegistryEntry finished = entry.finished();
        matchRegistry.save(finished);

        log.info("Marked match {} as finished", matchId);
    }

    /**
     * Creates a minimal Node object for client calls using registry entry data.
     */
    private Node createNodeForClient(MatchRegistryEntry entry) {
        return Node.register(
                entry.nodeId(),
                entry.advertiseAddress(),
                NodeCapacity.defaultCapacity()
        );
    }
}
