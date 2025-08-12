package com.lightningfirefly.engine.quarkus.api.rest;

import com.lightningfirefly.engine.core.GameSimulation;
import com.lightningfirefly.engine.quarkus.api.dto.PlayStatusResponse;
import com.lightningfirefly.engine.quarkus.api.dto.TickResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * REST resource for simulation tick operations.
 */
@Path("/api/simulation")
@Produces(MediaType.APPLICATION_JSON)
public class SimulationResource {

    @Inject
    GameSimulation gameSimulation;

    @GET
    @Path("/tick")
    public TickResponse getCurrentTick() {
        return new TickResponse(gameSimulation.getCurrentTick());
    }

    @POST
    @Path("/tick")
    public TickResponse advanceTick() {
        long newTick = gameSimulation.advanceTick();
        return new TickResponse(newTick);
    }

    /**
     * Start auto-advancing ticks at the specified interval.
     *
     * @param intervalMs the interval between ticks in milliseconds (default: 10ms)
     * @return the play status response
     */
    @POST
    @Path("/play")
    public PlayStatusResponse play(@QueryParam("intervalMs") @DefaultValue("10") long intervalMs) {
        gameSimulation.startAutoAdvance(intervalMs);
        return new PlayStatusResponse(true, gameSimulation.getCurrentTick(), intervalMs);
    }

    /**
     * Stop auto-advancing ticks.
     *
     * @return the play status response
     */
    @POST
    @Path("/stop")
    public PlayStatusResponse stop() {
        gameSimulation.stopAutoAdvance();
        return new PlayStatusResponse(false, gameSimulation.getCurrentTick(), 0);
    }

    /**
     * Get the current play status.
     *
     * @return the play status response
     */
    @GET
    @Path("/status")
    public PlayStatusResponse getStatus() {
        return new PlayStatusResponse(
                gameSimulation.isAutoAdvancing(),
                gameSimulation.getCurrentTick(),
                0
        );
    }
}
