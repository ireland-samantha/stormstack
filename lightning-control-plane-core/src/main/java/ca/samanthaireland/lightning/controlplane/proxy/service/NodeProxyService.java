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

package ca.samanthaireland.lightning.controlplane.proxy.service;

import ca.samanthaireland.lightning.controlplane.node.model.NodeId;
import ca.samanthaireland.lightning.controlplane.proxy.exception.ProxyDisabledException;
import ca.samanthaireland.lightning.controlplane.proxy.exception.ProxyException;
import ca.samanthaireland.lightning.controlplane.node.exception.NodeNotFoundException;

import java.util.Map;

/**
 * Service for proxying HTTP requests to Lightning Engine nodes.
 *
 * <p>This service allows clients that cannot directly reach nodes (e.g., due to
 * Docker networking) to route requests through the Control Plane, which is on
 * the same network as the nodes.
 *
 * <p>Typical use case:
 * <pre>
 * CLI -> Control Plane -> Node (internal Docker address)
 * </pre>
 */
public interface NodeProxyService {

    /**
     * Proxies an HTTP request to a node.
     *
     * @param nodeId      the target node ID (must be registered)
     * @param method      the HTTP method (GET, POST, PUT, DELETE, PATCH)
     * @param path        the request path (e.g., "/api/containers/1/tick")
     * @param queryParams query parameters to forward
     * @param headers     headers to forward (Authorization, Content-Type, etc.)
     * @param body        the request body (may be null for GET/DELETE)
     * @return the response from the upstream node
     * @throws NodeNotFoundException   if the node is not registered
     * @throws ProxyDisabledException  if proxy functionality is disabled
     * @throws ProxyException          if the request fails (timeout, connection refused, etc.)
     */
    ProxyResponse proxy(
            NodeId nodeId,
            String method,
            String path,
            Map<String, String> queryParams,
            Map<String, String> headers,
            byte[] body
    );

    /**
     * Checks if proxy functionality is currently enabled.
     *
     * @return true if proxy is enabled
     */
    boolean isEnabled();

    /**
     * Enables or disables proxy functionality.
     *
     * @param enabled true to enable, false to disable
     */
    void setEnabled(boolean enabled);
}
