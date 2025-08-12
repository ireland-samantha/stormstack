package com.lightningfirefly.engine.quarkus.api.rest;

import com.lightningfirefly.engine.core.GameSimulation;
import com.lightningfirefly.engine.core.match.Player;
import com.lightningfirefly.engine.quarkus.api.dto.PlayerRequest;
import com.lightningfirefly.engine.quarkus.api.dto.PlayerResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * REST resource for player operations.
 */
@Path("/api/players")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PlayerResource {

    @Inject
    GameSimulation gameSimulation;

    @POST
    public Response createPlayer(PlayerRequest request) {
        Player player = new Player(request.id());
        gameSimulation.createPlayer(player);
        return Response.status(Response.Status.CREATED)
                .entity(toResponse(player))
                .build();
    }

    @GET
    @Path("/{playerId}")
    public Response getPlayer(@PathParam("playerId") long playerId) {
        return gameSimulation.getPlayer(playerId)
                .map(player -> Response.ok(toResponse(player)).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    public List<PlayerResponse> getAllPlayers() {
        return gameSimulation.getAllPlayers().stream()
                .map(this::toResponse)
                .toList();
    }

    @DELETE
    @Path("/{playerId}")
    public Response deletePlayer(@PathParam("playerId") long playerId) {
        if (gameSimulation.getPlayer(playerId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        gameSimulation.deletePlayer(playerId);
        return Response.noContent().build();
    }

    private PlayerResponse toResponse(Player player) {
        return new PlayerResponse(player.id());
    }
}
