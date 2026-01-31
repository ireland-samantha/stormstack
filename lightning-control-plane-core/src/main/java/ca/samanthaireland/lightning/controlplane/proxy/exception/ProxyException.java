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

package ca.samanthaireland.lightning.controlplane.proxy.exception;

import ca.samanthaireland.lightning.controlplane.exception.ControlPlaneException;
import ca.samanthaireland.lightning.controlplane.node.model.NodeId;

/**
 * Exception thrown when a proxy request to a node fails.
 * This typically indicates a network error, timeout, or the node being unreachable.
 */
public class ProxyException extends ControlPlaneException {

    private final NodeId nodeId;
    private final String path;

    /**
     * Creates a new proxy exception.
     *
     * @param nodeId  the target node ID
     * @param path    the path being proxied
     * @param message the error message
     */
    public ProxyException(NodeId nodeId, String path, String message) {
        super("PROXY_FAILED", String.format("Failed to proxy request to node %s path %s: %s",
                nodeId, path, message));
        this.nodeId = nodeId;
        this.path = path;
    }

    /**
     * Creates a new proxy exception with a cause.
     *
     * @param nodeId  the target node ID
     * @param path    the path being proxied
     * @param message the error message
     * @param cause   the underlying cause
     */
    public ProxyException(NodeId nodeId, String path, String message, Throwable cause) {
        super("PROXY_FAILED", String.format("Failed to proxy request to node %s path %s: %s",
                nodeId, path, message), cause);
        this.nodeId = nodeId;
        this.path = path;
    }

    /**
     * Gets the target node ID.
     *
     * @return the node ID
     */
    public NodeId getNodeId() {
        return nodeId;
    }

    /**
     * Gets the path that was being proxied.
     *
     * @return the path
     */
    public String getPath() {
        return path;
    }
}
