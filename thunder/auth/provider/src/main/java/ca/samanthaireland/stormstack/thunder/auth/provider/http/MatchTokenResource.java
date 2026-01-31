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

package ca.samanthaireland.stormstack.thunder.auth.provider.http;

import ca.samanthaireland.stormstack.thunder.auth.exception.AuthException;
import ca.samanthaireland.stormstack.thunder.auth.model.MatchToken;
import ca.samanthaireland.stormstack.thunder.auth.model.MatchTokenId;
import ca.samanthaireland.stormstack.thunder.auth.model.UserId;
import ca.samanthaireland.stormstack.thunder.auth.provider.dto.*;
import ca.samanthaireland.stormstack.thunder.auth.quarkus.annotation.Scopes;
import ca.samanthaireland.stormstack.thunder.auth.service.MatchTokenService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import static ca.samanthaireland.stormstack.thunder.auth.provider.http.MediaTypes.*;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST resource for match token management.
 *
 * <p>Match tokens authorize players to connect to specific matches and
 * perform match-specific operations like submitting commands and viewing snapshots.
 */
@Path("/api/match-tokens")
@Produces({V1_JSON, JSON})
@Consumes({V1_JSON, JSON})
public class MatchTokenResource {

    @Inject
    MatchTokenService matchTokenService;

    /**
     * Issue a new match token for a player.
     *
     * <p>The response includes the JWT token which should be given to the player.
     * This is the only time the JWT is returned.
     *
     * <p>Requires either:
     * <ul>
     *   <li>{@code auth.match-token.issue} - for authenticated admin users</li>
     *   <li>{@code service.match-token.issue} - for service-to-service calls (control plane, etc.)</li>
     * </ul>
     */
    @POST
    @Scopes({"auth.match-token.issue", "service.match-token.issue"})
    public Response issueToken(@Valid IssueMatchTokenRequest request) {
        UserId userId = request.userId() != null ? UserId.fromString(request.userId()) : null;

        var coreRequest = new ca.samanthaireland.stormstack.thunder.auth.service.dto.IssueMatchTokenRequest(
                request.matchId(),
                request.containerId(),
                request.playerId(),
                userId,
                request.playerName(),
                request.scopes(),
                Duration.ofHours(request.validForHours())
        );

        MatchToken token = matchTokenService.issueToken(coreRequest);

        return Response.created(URI.create("/api/match-tokens/" + token.id()))
                .entity(IssueMatchTokenResponse.from(token))
                .build();
    }

    /**
     * Validate a match token.
     *
     * <p>This endpoint is for service-to-service validation. It validates
     * the JWT and optionally checks that it's valid for a specific match/container.
     */
    @POST
    @Path("/validate")
    public MatchTokenResponse validateToken(@Valid ValidateMatchTokenRequest request) {
        MatchToken token;

        if (request.matchId() != null && request.containerId() != null) {
            token = matchTokenService.validateTokenForMatchAndContainer(
                    request.token(), request.matchId(), request.containerId());
        } else if (request.matchId() != null) {
            token = matchTokenService.validateTokenForMatch(request.token(), request.matchId());
        } else {
            token = matchTokenService.validateToken(request.token());
        }

        return MatchTokenResponse.from(token);
    }

    /**
     * Get a match token by ID.
     */
    @GET
    @Path("/{id}")
    @Scopes("auth.match-token.read")
    public MatchTokenResponse getToken(@PathParam("id") String id) {
        MatchTokenId tokenId = MatchTokenId.fromString(id);
        return matchTokenService.findById(tokenId)
                .map(MatchTokenResponse::from)
                .orElseThrow(() -> AuthException.matchTokenNotFound(tokenId));
    }

    /**
     * List all tokens for a match.
     */
    @GET
    @Path("/match/{matchId}")
    @Scopes("auth.match-token.read")
    public List<MatchTokenResponse> listTokensForMatch(@PathParam("matchId") String matchId) {
        return matchTokenService.findByMatchId(matchId).stream()
                .map(MatchTokenResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * List active tokens for a match.
     */
    @GET
    @Path("/match/{matchId}/active")
    @Scopes("auth.match-token.read")
    public List<MatchTokenResponse> listActiveTokensForMatch(@PathParam("matchId") String matchId) {
        return matchTokenService.findActiveByMatchId(matchId).stream()
                .map(MatchTokenResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Get active token for a player in a match.
     */
    @GET
    @Path("/match/{matchId}/player/{playerId}")
    @Scopes("auth.match-token.read")
    public MatchTokenResponse getActiveTokenForPlayer(
            @PathParam("matchId") String matchId,
            @PathParam("playerId") String playerId) {
        return matchTokenService.findActiveByMatchAndPlayer(matchId, playerId)
                .map(MatchTokenResponse::from)
                .orElseThrow(() -> new NotFoundException("No active token for player in match"));
    }

    /**
     * Revoke a match token.
     */
    @POST
    @Path("/{id}/revoke")
    @Consumes("*/*")
    @Scopes("auth.match-token.revoke")
    public Response revokeToken(@PathParam("id") String id) {
        MatchTokenId tokenId = MatchTokenId.fromString(id);
        matchTokenService.revokeToken(tokenId);
        return Response.ok().entity(new RevokeCountResponse(1)).build();
    }

    /**
     * Revoke all tokens for a player in a match.
     */
    @POST
    @Path("/match/{matchId}/player/{playerId}/revoke")
    @Consumes("*/*")
    @Scopes("auth.match-token.revoke")
    public Response revokeTokensForPlayer(
            @PathParam("matchId") String matchId,
            @PathParam("playerId") String playerId) {
        long count = matchTokenService.revokeTokensForPlayer(matchId, playerId);
        return Response.ok().entity(new RevokeCountResponse(count)).build();
    }

    /**
     * Revoke all tokens for a match.
     */
    @POST
    @Path("/match/{matchId}/revoke")
    @Consumes("*/*")
    @Scopes("auth.match-token.revoke")
    public Response revokeTokensForMatch(@PathParam("matchId") String matchId) {
        long count = matchTokenService.revokeTokensForMatch(matchId);
        return Response.ok().entity(new RevokeCountResponse(count)).build();
    }

    /**
     * Get count of active tokens for a match.
     */
    @GET
    @Path("/match/{matchId}/count")
    @Scopes("auth.match-token.read")
    public ActiveCountResponse countActiveTokens(@PathParam("matchId") String matchId) {
        long count = matchTokenService.countActiveByMatchId(matchId);
        return new ActiveCountResponse(matchId, count);
    }

    /**
     * Response for revoke operations.
     */
    public record RevokeCountResponse(long revokedCount) {}

    /**
     * Response for count operations.
     */
    public record ActiveCountResponse(String matchId, long activeCount) {}
}
