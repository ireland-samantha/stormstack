package com.lightningfirefly.engine.quarkus.api.rest;

import com.lightningfirefly.engine.core.GameSimulation;
import com.lightningfirefly.engine.core.snapshot.Snapshot;
import com.lightningfirefly.engine.core.snapshot.SnapshotFilter;
import com.lightningfirefly.engine.internal.core.snapshot.SnapshotProvider;
import com.lightningfirefly.engine.quarkus.api.dto.SnapshotResponse;
import com.lightningfirefly.engine.core.match.Match;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.List;

/**
 * REST resource for components operations.
 */
@Path("/api/snapshots")
@Produces(MediaType.APPLICATION_JSON)
public class SnapshotResource {

    @Inject
    GameSimulation gameSimulation;

    @Inject
    SnapshotProvider snapshotProvider;

    /**
     * Get snapshots for ALL matches.
     * Returns a list of snapshots, one per match, with their modules and entities.
     */
    @GET
    public List<SnapshotResponse> getAllSnapshots() {
        List<Match> matches = gameSimulation.getAllMatches();
        long currentTick = gameSimulation.getCurrentTick();
        List<SnapshotResponse> responses = new ArrayList<>();

        for (Match match : matches) {
            Snapshot snapshot = snapshotProvider.createForMatch(match.id());
            responses.add(new SnapshotResponse(
                    match.id(),
                    currentTick,
                    snapshot.snapshot()
            ));
        }

        return responses;
    }

    /**
     * Get components for a specific match.
     */
    @GET
    @Path("/match/{matchId}")
    public Response getSnapshot(@PathParam("matchId") long matchId) {
        if (gameSimulation.getMatch(matchId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        SnapshotFilter filter = new SnapshotFilter(List.of(matchId), null);
        Snapshot snapshot = snapshotProvider.createForMatch(matchId);
        SnapshotResponse response = new SnapshotResponse(
                matchId,
                gameSimulation.getCurrentTick(),
                snapshot.snapshot()
        );
        return Response.ok(response).build();
    }
}
