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

package ca.samanthaireland.lightning.controlplane.match.model;

import ca.samanthaireland.lightning.controlplane.node.model.NodeId;

import java.util.Objects;

/**
 * Strongly-typed identifier for a match in the cluster.
 *
 * <p>Cluster match IDs are composite identifiers in the format:
 * {@code nodeId-containerId-matchId}. This format ensures global uniqueness
 * across the cluster by combining the node identifier, container identifier,
 * and local match identifier.
 *
 * @param value the composite match ID string
 */
public record ClusterMatchId(String value) {

    public ClusterMatchId {
        Objects.requireNonNull(value, "ClusterMatchId value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ClusterMatchId value cannot be blank");
        }
    }

    /**
     * Creates a ClusterMatchId from its component parts.
     *
     * @param nodeId      the node hosting the match
     * @param containerId the container ID on the node
     * @param matchId     the local match ID within the container
     * @return the composite ClusterMatchId
     */
    public static ClusterMatchId of(NodeId nodeId, long containerId, long matchId) {
        Objects.requireNonNull(nodeId, "nodeId cannot be null");
        return new ClusterMatchId(nodeId.value() + "-" + containerId + "-" + matchId);
    }

    /**
     * Creates a ClusterMatchId from its component parts using string nodeId.
     *
     * @param nodeId      the node ID string
     * @param containerId the container ID on the node
     * @param matchId     the local match ID within the container
     * @return the composite ClusterMatchId
     */
    public static ClusterMatchId of(String nodeId, long containerId, long matchId) {
        Objects.requireNonNull(nodeId, "nodeId cannot be null");
        if (nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId cannot be blank");
        }
        return new ClusterMatchId(nodeId + "-" + containerId + "-" + matchId);
    }

    /**
     * Creates a ClusterMatchId from a string value.
     *
     * @param value the composite match ID string
     * @return the ClusterMatchId
     * @throws IllegalArgumentException if the string is null or blank
     */
    public static ClusterMatchId fromString(String value) {
        return new ClusterMatchId(value);
    }

    /**
     * Extracts the node ID component from this cluster match ID.
     *
     * <p>The format is {@code nodeId-containerId-matchId}, where nodeId can contain
     * hyphens. This method parses from the end to correctly handle such node IDs.
     *
     * @return the node ID, or null if the format is invalid
     */
    public NodeId nodeId() {
        String[] parts = value.split("-");
        // Format: nodeId-containerId-matchId
        // nodeId can contain hyphens, so we parse from the end
        // The last two parts are matchId and containerId (numeric)
        // Everything before is the nodeId
        if (parts.length < 3) {
            return null;
        }
        // Reconstruct nodeId from all parts except the last two
        StringBuilder nodeIdBuilder = new StringBuilder();
        for (int i = 0; i < parts.length - 2; i++) {
            if (i > 0) {
                nodeIdBuilder.append("-");
            }
            nodeIdBuilder.append(parts[i]);
        }
        return NodeId.of(nodeIdBuilder.toString());
    }

    /**
     * Extracts the container ID component from this cluster match ID.
     *
     * <p>The format is {@code nodeId-containerId-matchId}. Container ID is the
     * second-to-last numeric component.
     *
     * @return the container ID, or -1 if the format is invalid
     */
    public long containerId() {
        String[] parts = value.split("-");
        // Container ID is the second-to-last part
        if (parts.length < 3) {
            return -1;
        }
        try {
            return Long.parseLong(parts[parts.length - 2]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Extracts the local match ID component from this cluster match ID.
     *
     * <p>The format is {@code nodeId-containerId-matchId}. Match ID is the
     * last numeric component.
     *
     * @return the local match ID, or -1 if the format is invalid
     */
    public long localMatchId() {
        String[] parts = value.split("-");
        // Match ID is the last part
        if (parts.length < 3) {
            return -1;
        }
        try {
            return Long.parseLong(parts[parts.length - 1]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
