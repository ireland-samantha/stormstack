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

package ca.samanthaireland.stormstack.thunder.controlplane.module.service;

import ca.samanthaireland.stormstack.thunder.controlplane.node.model.NodeId;

/**
 * Service for distributing modules to engine nodes.
 *
 * <p>This interface follows the Interface Segregation Principle (ISP) by
 * separating module distribution from module registry operations.
 */
public interface ModuleDistributionService {

    /**
     * Distributes a module to a specific node.
     *
     * @param name    module name
     * @param version module version
     * @param nodeId  target node ID
     */
    void distributeToNode(String name, String version, NodeId nodeId);

    /**
     * Distributes a module to all healthy nodes.
     *
     * @param name    module name
     * @param version module version
     * @return number of nodes the module was distributed to
     */
    int distributeToAllNodes(String name, String version);
}
