package com.lightningfirefly.engine.quarkus.api.rest;

import com.lightningfirefly.engine.core.GameSimulation;
import com.lightningfirefly.engine.core.match.PlayerMatch;
import com.lightningfirefly.engine.quarkus.api.dto.PlayerMatchRequest;
import com.lightningfirefly.engine.quarkus.api.dto.PlayerMatchResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * REST resource for player-match operations (joining/leaving matches).
 */
@Path("/api/player-matches")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PlayerMatchResource {

    @Inject
    GameSimulation gameSimulation;

    @POST
    public Response joinMatch(PlayerMatchRequest request) {
        PlayerMatch playerMatch = new PlayerMatch(request.playerId(), request.matchId());
        gameSimulation.joinMatch(playerMatch);
        return Response.status(Response.Status.CREATED)
                .entity(toResponse(playerMatch))
                .build();
    }

    @GET
    @Path("/player/{playerId}/match/{matchId}")
    public Response getPlayerMatch(
            @PathParam("playerId") long playerId,
            @PathParam("matchId") long matchId) {
        return gameSimulation.getPlayerMatch(playerId, matchId)
                .map(pm -> Response.ok(toResponse(pm)).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    @Path("/match/{matchId}")
    public List<PlayerMatchResponse> getPlayerMatchesByMatch(@PathParam("matchId") long matchId) {
        return gameSimulation.getPlayerMatchesByMatch(matchId).stream()
                .map(this::toResponse)
                .toList();
    }

    @GET
    @Path("/player/{playerId}")
    public List<PlayerMatchResponse> getPlayerMatchesByPlayer(@PathParam("playerId") long playerId) {
        return gameSimulation.getPlayerMatchesByPlayer(playerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @DELETE
    @Path("/player/{playerId}/match/{matchId}")
    public Response leaveMatch(
            @PathParam("playerId") long playerId,
            @PathParam("matchId") long matchId) {
        if (gameSimulation.getPlayerMatch(playerId, matchId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        PlayerMatch playerMatch = new PlayerMatch(playerId, matchId);
        gameSimulation.leaveMatch(playerMatch);
        return Response.noContent().build();
    }

    private PlayerMatchResponse toResponse(PlayerMatch playerMatch) {
        return new PlayerMatchResponse(playerMatch.playerId(), playerMatch.matchId());
    }
}
