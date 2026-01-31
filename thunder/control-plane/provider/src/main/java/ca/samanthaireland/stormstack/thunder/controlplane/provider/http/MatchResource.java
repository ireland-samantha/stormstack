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
import ca.samanthaireland.stormstack.thunder.controlplane.auth.AuthClient;
import ca.samanthaireland.stormstack.thunder.controlplane.auth.IssueMatchTokenRequest;
import ca.samanthaireland.stormstack.thunder.controlplane.auth.MatchTokenResponse;
import ca.samanthaireland.stormstack.thunder.controlplane.match.exception.MatchFullException;
import ca.samanthaireland.stormstack.thunder.controlplane.match.model.ClusterMatchId;
import ca.samanthaireland.stormstack.thunder.controlplane.match.model.MatchRegistryEntry;
import ca.samanthaireland.stormstack.thunder.controlplane.match.model.MatchStatus;
import ca.samanthaireland.stormstack.thunder.controlplane.match.service.MatchRoutingService;
import ca.samanthaireland.stormstack.thunder.controlplane.node.model.NodeId;
import ca.samanthaireland.stormstack.thunder.controlplane.provider.dto.CreateMatchRequest;
import ca.samanthaireland.stormstack.thunder.controlplane.provider.dto.JoinMatchRequest;
import ca.samanthaireland.stormstack.thunder.controlplane.provider.dto.JoinMatchResponse;
import ca.samanthaireland.stormstack.thunder.controlplane.provider.dto.MatchResponse;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import static ca.samanthaireland.stormstack.thunder.controlplane.provider.http.MediaTypes.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.Set;

/**
 * REST resource for match routing operations.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/matches/create - Create a new match (scheduler picks node)</li>
 *   <li>GET /api/matches/{matchId} - Get match details with connection info</li>
 *   <li>GET /api/matches - List all matches</li>
 *   <li>DELETE /api/matches/{matchId} - Delete a match</li>
 *   <li>POST /api/matches/{matchId}/finish - Mark match as finished</li>
 *   <li>POST /api/matches/{matchId}/join - Join a match as a player</li>
 * </ul>
 */
@Path("/api/matches")
@Produces({V1_JSON, JSON})
@Consumes({V1_JSON, JSON})
public class MatchResource {
    private static final Logger log = LoggerFactory.getLogger(MatchResource.class);

    private final MatchRoutingService matchRoutingService;
    private final AuthClient authClient;

    @Inject
    public MatchResource(MatchRoutingService matchRoutingService, AuthClient authClient) {
        this.matchRoutingService = matchRoutingService;
        this.authClient = authClient;
    }

    /**
     * Creates a new match in the cluster.
     * The scheduler selects the best available node based on load and capacity.
     *
     * @param request the match creation request
     * @return 201 Created with match details and connection info
     */
    @POST
    @Path("/create")
    @Scopes("control-plane.match.create")
    public Response create(@Valid CreateMatchRequest request) {
        log.info("Create match request: modules={}, preferredNode={}, playerLimit={}",
                request.moduleNames(), request.preferredNodeId(), request.playerLimit());

        NodeId preferredNodeId = request.preferredNodeId() != null && !request.preferredNodeId().isBlank()
                ? NodeId.of(request.preferredNodeId())
                : null;

        MatchRegistryEntry entry = matchRoutingService.createMatch(
                request.moduleNames(),
                preferredNodeId,
                request.playerLimitOrDefault()
        );

        log.info("Match created: matchId={}, nodeId={}, websocketUrl={}, playerLimit={}",
                entry.matchId(), entry.nodeId(), entry.websocketUrl(), entry.playerLimit());

        return Response.created(URI.create("/api/matches/" + entry.matchId()))
                .entity(MatchResponse.from(entry))
                .build();
    }

    /**
     * Gets match details by ID, including connection information.
     *
     * @param matchId the cluster-unique match ID
     * @return the match details
     */
    @GET
    @Path("/{matchId}")
    @Scopes("control-plane.match.read")
    public MatchResponse getById(@PathParam("matchId") String matchId) {
        ClusterMatchId id = ClusterMatchId.fromString(matchId);
        MatchRegistryEntry entry = matchRoutingService.findById(id)
                .orElseThrow(() -> new ca.samanthaireland.stormstack.thunder.controlplane.match.exception.MatchNotFoundException(id));

        return MatchResponse.from(entry);
    }

    /**
     * Lists all matches in the cluster.
     *
     * @param status optional status filter
     * @return list of all matches
     */
    @GET
    @Scopes("control-plane.match.read")
    public List<MatchResponse> list(@QueryParam("status") MatchStatus status) {
        List<MatchRegistryEntry> entries;

        if (status != null) {
            entries = matchRoutingService.findByStatus(status);
        } else {
            entries = matchRoutingService.findAll();
        }

        return entries.stream()
                .map(MatchResponse::from)
                .toList();
    }

    /**
     * Deletes a match from the cluster.
     * This will also delete the match from the hosting node.
     *
     * @param matchId the match ID to delete
     * @return 204 No Content on success
     */
    @DELETE
    @Path("/{matchId}")
    @Scopes("control-plane.match.delete")
    public Response delete(@PathParam("matchId") String matchId) {
        log.info("Delete match request: matchId={}", matchId);

        matchRoutingService.deleteMatch(ClusterMatchId.fromString(matchId));

        return Response.noContent().build();
    }

    /**
     * Marks a match as finished.
     * Finished matches remain in the registry but are no longer active.
     *
     * @param matchId the match ID to finish
     * @return the updated match
     */
    @POST
    @Path("/{matchId}/finish")
    @Scopes("control-plane.match.update")
    public MatchResponse finish(@PathParam("matchId") String matchId) {
        log.info("Finish match request: matchId={}", matchId);

        ClusterMatchId id = ClusterMatchId.fromString(matchId);
        matchRoutingService.finishMatch(id);

        return matchRoutingService.findById(id)
                .map(MatchResponse::from)
                .orElseThrow(() -> new ca.samanthaireland.stormstack.thunder.controlplane.match.exception.MatchNotFoundException(id));
    }

    /**
     * Updates the player count for a match.
     *
     * @param matchId     the match ID
     * @param playerCount the new player count
     * @return the updated match
     */
    @PUT
    @Path("/{matchId}/players")
    @Scopes("control-plane.match.update")
    public MatchResponse updatePlayerCount(
            @PathParam("matchId") String matchId,
            @QueryParam("count") int playerCount
    ) {
        log.debug("Update player count: matchId={}, count={}", matchId, playerCount);

        ClusterMatchId id = ClusterMatchId.fromString(matchId);
        matchRoutingService.updatePlayerCount(id, playerCount);

        return matchRoutingService.findById(id)
                .map(MatchResponse::from)
                .orElseThrow(() -> new ca.samanthaireland.stormstack.thunder.controlplane.match.exception.MatchNotFoundException(id));
    }

    /**
     * Joins a match as a player.
     * <p>
     * This endpoint:
     * <ol>
     *   <li>Validates the match exists and can accept more players</li>
     *   <li>Issues a match token via the auth service</li>
     *   <li>Increments the player count</li>
     *   <li>Returns connection URLs with the token</li>
     * </ol>
     *
     * @param matchId the match ID to join
     * @param request the join request with player info
     * @return join response with match token and WebSocket URLs
     */
    @POST
    @Path("/{matchId}/join")
    @Scopes("control-plane.match.join")
    public JoinMatchResponse joinMatch(
            @PathParam("matchId") String matchId,
            @Valid JoinMatchRequest request
    ) {
        log.info("Join match request: matchId={}, playerId={}, playerName={}",
                matchId, request.playerId(), request.playerName());

        ClusterMatchId id = ClusterMatchId.fromString(matchId);

        // 1. Get match from registry
        MatchRegistryEntry entry = matchRoutingService.findById(id)
                .orElseThrow(() -> new ca.samanthaireland.stormstack.thunder.controlplane.match.exception.MatchNotFoundException(id));

        // 2. Validate match can accept player
        if (!entry.canAcceptPlayer()) {
            throw new MatchFullException(id, entry.playerLimit(), entry.playerCount());
        }

        // 3. Check match is in a joinable state
        if (entry.status() != MatchStatus.RUNNING) {
            throw new BadRequestException("Match " + matchId + " is not in RUNNING state");
        }

        // 4. Issue match token via auth service
        IssueMatchTokenRequest tokenRequest = new IssueMatchTokenRequest(
                matchId,
                String.valueOf(entry.containerId()),
                request.playerId(),
                request.playerName(),
                Set.of("match.command.send", "match.snapshot.read"),
                8 // 8 hours validity
        );

        MatchTokenResponse tokenResponse = authClient.issueMatchToken(tokenRequest);

        // 5. Increment player count in registry
        matchRoutingService.updatePlayerCount(id, entry.playerCount() + 1);

        // 6. Build WebSocket URLs
        String commandWsUrl = buildCommandWebSocketUrl(entry);
        String snapshotWsUrl = entry.websocketUrl();

        log.info("Player {} joined match {}, issued token {}",
                request.playerId(), matchId, tokenResponse.tokenId());

        // 7. Return join response
        return JoinMatchResponse.builder()
                .matchId(matchId)
                .playerId(request.playerId())
                .playerName(request.playerName())
                .matchToken(tokenResponse.token())
                .commandWebSocketUrl(commandWsUrl)
                .snapshotWebSocketUrl(snapshotWsUrl)
                .tokenExpiresAt(tokenResponse.expiresAt())
                .build();
    }

    /**
     * Builds the command WebSocket URL from match entry.
     */
    private String buildCommandWebSocketUrl(MatchRegistryEntry entry) {
        String wsAddress = entry.advertiseAddress()
                .replace("https://", "wss://")
                .replace("http://", "ws://");
        return wsAddress + "/containers/" + entry.containerId() + "/commands";
    }
}
