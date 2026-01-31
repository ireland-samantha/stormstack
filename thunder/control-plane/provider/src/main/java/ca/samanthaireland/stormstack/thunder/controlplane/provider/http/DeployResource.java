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

package ca.samanthaireland.stormstack.thunder.controlplane.provider.http;

import ca.samanthaireland.stormstack.thunder.auth.quarkus.annotation.Scopes;
import ca.samanthaireland.stormstack.thunder.controlplane.match.model.ClusterMatchId;
import ca.samanthaireland.stormstack.thunder.controlplane.match.model.MatchRegistryEntry;
import ca.samanthaireland.stormstack.thunder.controlplane.match.service.MatchRoutingService;
import ca.samanthaireland.stormstack.thunder.controlplane.node.model.NodeId;
import ca.samanthaireland.stormstack.thunder.controlplane.provider.auth.AuthServiceClient;
import ca.samanthaireland.stormstack.thunder.controlplane.provider.dto.DeployRequest;
import ca.samanthaireland.stormstack.thunder.controlplane.provider.dto.DeployResponse;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import static ca.samanthaireland.stormstack.thunder.controlplane.provider.http.MediaTypes.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Instant;
import java.util.Set;

/**
 * REST resource for the deployment API.
 * This is the primary CLI-facing endpoint for deploying games to the cluster.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/deploy - Deploy a new game match</li>
 *   <li>GET /api/deploy/{matchId} - Get deployment status</li>
 *   <li>DELETE /api/deploy/{matchId} - Undeploy a match</li>
 * </ul>
 *
 * <p>Uses media type versioning via Accept header: application/vnd.lightning.v1+json
 */
@Path("/api/deploy")
@Produces({V1_JSON, JSON})
@Consumes({V1_JSON, JSON})
public class DeployResource {
    private static final Logger log = LoggerFactory.getLogger(DeployResource.class);

    private static final Set<String> DEFAULT_MATCH_SCOPES = Set.of(
            "match.command.submit",
            "match.snapshot.view",
            "match.player.read"
    );

    private final MatchRoutingService matchRoutingService;
    private final AuthServiceClient authServiceClient;

    @Inject
    public DeployResource(MatchRoutingService matchRoutingService, AuthServiceClient authServiceClient) {
        this.matchRoutingService = matchRoutingService;
        this.authServiceClient = authServiceClient;
    }

    /**
     * Deploys a new game match to the cluster.
     * <p>
     * The deployment process:
     * <ol>
     *   <li>Selects the best available node based on load and capacity</li>
     *   <li>Creates a container on the selected node</li>
     *   <li>Starts the container (if autoStart is true)</li>
     *   <li>Creates a match with the specified modules</li>
     *   <li>Issues a match token via the auth service</li>
     *   <li>Returns connection endpoints with the match token</li>
     * </ol>
     *
     * @param request the deployment request
     * @return 201 Created with deployment details, connection endpoints, and match token
     */
    @POST
    @Scopes("control-plane.deploy.create")
    public Response deploy(@Valid DeployRequest request) {
        log.info("Deploy request: modules={}, preferredNode={}, autoStart={}",
                request.modules(), request.preferredNodeId(), request.isAutoStart());

        NodeId preferredNodeId = request.preferredNodeId() != null && !request.preferredNodeId().isBlank()
                ? NodeId.of(request.preferredNodeId())
                : null;

        MatchRegistryEntry entry = matchRoutingService.createMatch(
                request.modules(),
                preferredNodeId
        );

        // Issue match token via auth service
        String matchToken = null;
        Instant tokenExpiresAt = null;

        if (authServiceClient.isRemoteValidationEnabled()) {
            var tokenResult = authServiceClient.issueMatchToken(
                    entry.matchId().value(),
                    entry.containerId(),
                    "system",
                    "Deploy Token",
                    DEFAULT_MATCH_SCOPES
            );

            if (tokenResult instanceof AuthServiceClient.MatchTokenResult.Success success) {
                matchToken = success.token();
                tokenExpiresAt = success.expiresAt();
                log.info("Issued match token for matchId={}, expires={}", entry.matchId(), tokenExpiresAt);
            } else if (tokenResult instanceof AuthServiceClient.MatchTokenResult.Failure failure) {
                log.warn("Failed to issue match token for matchId={}: {}", entry.matchId(), failure.message());
                // Continue without token - deployment still succeeds
            }
        } else {
            log.debug("Auth service not configured, skipping match token issuance");
        }

        DeployResponse response = DeployResponse.from(entry, matchToken, tokenExpiresAt);

        log.info("Deployment successful: matchId={}, nodeId={}, websocket={}, hasToken={}",
                entry.matchId(), entry.nodeId(), response.endpoints().websocket(), matchToken != null);

        return Response.created(URI.create("/api/deploy/" + entry.matchId()))
                .entity(response)
                .build();
    }

    /**
     * Gets the status of a deployed match.
     *
     * @param matchId the match ID
     * @return the deployment status
     */
    @GET
    @Path("/{matchId}")
    @Scopes("control-plane.deploy.read")
    public DeployResponse getStatus(@PathParam("matchId") String matchId) {
        log.debug("Get deployment status: matchId={}", matchId);

        ClusterMatchId id = ClusterMatchId.fromString(matchId);
        MatchRegistryEntry entry = matchRoutingService.findById(id)
                .orElseThrow(() -> new ca.samanthaireland.stormstack.thunder.controlplane.match.exception.MatchNotFoundException(id));

        return DeployResponse.from(entry);
    }

    /**
     * Undeploys a match from the cluster.
     * This stops the container and removes the match.
     *
     * @param matchId the match ID to undeploy
     * @return 204 No Content on success
     */
    @DELETE
    @Path("/{matchId}")
    @Scopes("control-plane.deploy.delete")
    public Response undeploy(@PathParam("matchId") String matchId) {
        log.info("Undeploy request: matchId={}", matchId);

        matchRoutingService.deleteMatch(ClusterMatchId.fromString(matchId));

        return Response.noContent().build();
    }
}
