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

package ca.samanthaireland.lightning.controlplane.provider.http;

import ca.samanthaireland.lightning.auth.quarkus.annotation.Scopes;
import ca.samanthaireland.lightning.controlplane.node.model.Node;
import ca.samanthaireland.lightning.controlplane.node.model.NodeId;
import ca.samanthaireland.lightning.controlplane.node.service.NodeRegistryService;
import ca.samanthaireland.lightning.controlplane.provider.dto.HeartbeatRequest;
import ca.samanthaireland.lightning.controlplane.provider.dto.NodeRegistrationRequest;
import ca.samanthaireland.lightning.controlplane.provider.dto.NodeResponse;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import static ca.samanthaireland.lightning.controlplane.provider.http.MediaTypes.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * REST resource for node registration and heartbeat operations.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/nodes/register - Register a new node</li>
 *   <li>PUT /api/nodes/{nodeId}/heartbeat - Send heartbeat</li>
 *   <li>POST /api/nodes/{nodeId}/drain - Mark node as draining</li>
 *   <li>DELETE /api/nodes/{nodeId} - Deregister node</li>
 * </ul>
 */
@Path("/api/nodes")
@Produces({V1_JSON, JSON})
@Consumes({V1_JSON, JSON})
public class NodeResource {
    private static final Logger log = LoggerFactory.getLogger(NodeResource.class);

    private final NodeRegistryService nodeRegistryService;

    @Inject
    public NodeResource(NodeRegistryService nodeRegistryService) {
        this.nodeRegistryService = nodeRegistryService;
    }

    /**
     * Register a new node or re-register an existing node.
     * This operation is idempotent.
     */
    @POST
    @Path("/register")
    @Scopes("control-plane.node.register")
    public Response register(@Valid NodeRegistrationRequest request) {
        log.info("Node registration request: nodeId={}, address={}",
                request.nodeId(), request.advertiseAddress());

        Node node = nodeRegistryService.register(
                NodeId.of(request.nodeId()),
                request.advertiseAddress(),
                request.capacity().toModel()
        );

        return Response.created(URI.create("/api/cluster/nodes/" + node.nodeId()))
                .entity(NodeResponse.from(node))
                .build();
    }

    /**
     * Process a heartbeat from a node, refreshing its TTL and updating metrics.
     */
    @PUT
    @Path("/{nodeId}/heartbeat")
    @Scopes("control-plane.node.register")
    public Response heartbeat(
            @PathParam("nodeId") String nodeId,
            @Valid HeartbeatRequest request
    ) {
        Node node = nodeRegistryService.heartbeat(NodeId.of(nodeId), request.metrics().toModel());

        return Response.ok(NodeResponse.from(node)).build();
    }

    /**
     * Mark a node as draining. It will no longer accept new containers.
     */
    @POST
    @Path("/{nodeId}/drain")
    @Scopes("control-plane.node.manage")
    public Response drain(@PathParam("nodeId") String nodeId) {
        log.info("Drain request for node: {}", nodeId);

        Node node = nodeRegistryService.drain(NodeId.of(nodeId));

        return Response.ok(NodeResponse.from(node)).build();
    }

    /**
     * Deregister a node from the cluster.
     */
    @DELETE
    @Path("/{nodeId}")
    @Scopes("control-plane.node.manage")
    public Response deregister(@PathParam("nodeId") String nodeId) {
        log.info("Deregister request for node: {}", nodeId);

        nodeRegistryService.deregister(NodeId.of(nodeId));

        return Response.noContent().build();
    }
}
