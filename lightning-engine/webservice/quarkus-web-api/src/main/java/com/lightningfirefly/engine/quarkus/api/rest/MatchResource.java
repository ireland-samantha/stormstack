package com.lightningfirefly.engine.quarkus.api.rest;

import com.lightningfirefly.engine.core.GameSimulation;
import com.lightningfirefly.engine.core.match.Match;
import com.lightningfirefly.engine.quarkus.api.dto.MatchRequest;
import com.lightningfirefly.engine.quarkus.api.dto.MatchResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * REST resource for match operations.
 */
@Path("/api/matches")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
public class MatchResource {

    @Inject
    GameSimulation gameSimulation;

    @POST
    public Response createMatch(MatchRequest request) {
        // Use provided ID or generate one if 0
        Match match = new Match(request.id(), request.enabledModuleNames());
        log.info("Creating match with id {} and modules: {}", request.id(), request.enabledModuleNames());
        Match createdMatch = gameSimulation.createMatch(match);
        log.info("Created match with ID: {}", createdMatch.id());
        return Response.status(Response.Status.CREATED)
                .entity(toResponse(createdMatch))
                .build();
    }

    @GET
    @Path("/{matchId}")
    public Response getMatch(@PathParam("matchId") long matchId) {
        return gameSimulation.getMatch(matchId)
                .map(match -> Response.ok(toResponse(match)).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    public List<MatchResponse> getAllMatches() {
        return gameSimulation.getAllMatches().stream()
                .map(this::toResponse)
                .toList();
    }

    @DELETE
    @Path("/{matchId}")
    public Response deleteMatch(@PathParam("matchId") long matchId) {
        if (gameSimulation.getMatch(matchId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        gameSimulation.deleteMatch(matchId);
        return Response.noContent().build();
    }

    private MatchResponse toResponse(Match match) {
        List<String> moduleNames = match.enabledModules() != null
                ? match.enabledModules()
                : List.of();
        return new MatchResponse(match.id(), moduleNames);
    }
}
