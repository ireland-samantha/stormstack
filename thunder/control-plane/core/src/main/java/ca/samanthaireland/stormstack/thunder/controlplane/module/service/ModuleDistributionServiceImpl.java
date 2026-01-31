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

import ca.samanthaireland.stormstack.thunder.controlplane.client.LightningNodeClient;
import ca.samanthaireland.stormstack.thunder.controlplane.module.exception.ModuleDistributionException;
import ca.samanthaireland.stormstack.thunder.controlplane.module.exception.ModuleNotFoundException;
import ca.samanthaireland.stormstack.thunder.controlplane.module.model.ModuleMetadata;
import ca.samanthaireland.stormstack.thunder.controlplane.module.repository.ModuleRepository;
import ca.samanthaireland.stormstack.thunder.controlplane.node.exception.NodeNotFoundException;
import ca.samanthaireland.stormstack.thunder.controlplane.node.model.Node;
import ca.samanthaireland.stormstack.thunder.controlplane.node.model.NodeId;
import ca.samanthaireland.stormstack.thunder.controlplane.node.model.NodeStatus;
import ca.samanthaireland.stormstack.thunder.controlplane.node.service.NodeRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Implementation of ModuleDistributionService.
 *
 * <p>Handles distributing modules to engine nodes.
 */
public class ModuleDistributionServiceImpl implements ModuleDistributionService {

    private static final Logger log = LoggerFactory.getLogger(ModuleDistributionServiceImpl.class);

    private final ModuleRepository moduleRepository;
    private final NodeRegistryService nodeRegistryService;
    private final LightningNodeClient nodeClient;

    public ModuleDistributionServiceImpl(
            ModuleRepository moduleRepository,
            NodeRegistryService nodeRegistryService,
            LightningNodeClient nodeClient
    ) {
        this.moduleRepository = moduleRepository;
        this.nodeRegistryService = nodeRegistryService;
        this.nodeClient = nodeClient;
    }

    @Override
    public void distributeToNode(String name, String version, NodeId nodeId) {
        // Get module metadata and JAR
        ModuleMetadata metadata = moduleRepository.findByNameAndVersion(name, version)
                .orElseThrow(() -> new ModuleNotFoundException(name, version));

        byte[] jarData;
        try (InputStream is = moduleRepository.getJarFile(name, version)
                .orElseThrow(() -> new ModuleNotFoundException(name, version))) {
            jarData = is.readAllBytes();
        } catch (IOException e) {
            throw new ModuleDistributionException(name, nodeId, e);
        }

        // Get node
        Node node = nodeRegistryService.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));

        // Send to node
        sendModuleToNode(node, metadata, jarData);
    }

    @Override
    public int distributeToAllNodes(String name, String version) {
        // Get module metadata and JAR
        ModuleMetadata metadata = moduleRepository.findByNameAndVersion(name, version)
                .orElseThrow(() -> new ModuleNotFoundException(name, version));

        byte[] jarData;
        try (InputStream is = moduleRepository.getJarFile(name, version)
                .orElseThrow(() -> new ModuleNotFoundException(name, version))) {
            jarData = is.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read module JAR", e);
        }

        // Get all healthy nodes
        List<Node> healthyNodes = nodeRegistryService.findAll().stream()
                .filter(n -> n.status() == NodeStatus.HEALTHY)
                .toList();

        int successCount = 0;
        for (Node node : healthyNodes) {
            try {
                sendModuleToNode(node, metadata, jarData);
                successCount++;
            } catch (Exception e) {
                log.warn("Failed to distribute {}:{} to node {}: {}",
                        name, version, node.nodeId(), e.getMessage());
            }
        }

        log.info("Distributed {}:{} to {}/{} nodes", name, version, successCount, healthyNodes.size());
        return successCount;
    }

    private void sendModuleToNode(Node node, ModuleMetadata metadata, byte[] jarData) {
        try {
            nodeClient.uploadModule(node, metadata.name(), metadata.version(), metadata.fileName(), jarData);
            log.debug("Distributed {}:{} to node {}", metadata.name(), metadata.version(), node.nodeId());
        } catch (Exception e) {
            throw new ModuleDistributionException(metadata.name(), node.nodeId(), e);
        }
    }
}
