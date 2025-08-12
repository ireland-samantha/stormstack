package com.lightningfirefly.engine.quarkus.api.websocket;

import com.lightningfirefly.engine.quarkus.api.dto.TickResponse;
import com.lightningfirefly.engine.core.GameSimulation;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import jakarta.inject.Inject;

/**
 * WebSocket endpoint for simulation tick operations.
 *
 * <p>Clients connect to /ws/simulation and can:
 * - Send "tick" to advance the simulation
 * - Send "status" to get the current tick
 */
@WebSocket(path = "/ws/simulation")
public class SimulationWebSocket {

    @Inject
    GameSimulation gameSimulation;

    @OnOpen
    public TickResponse onOpen() {
        return new TickResponse(gameSimulation.getCurrentTick());
    }

    @OnTextMessage
    public TickResponse onMessage(String message) {
        return switch (message.trim().toLowerCase()) {
            case "tick" -> new TickResponse(gameSimulation.advanceTick());
            case "status" -> new TickResponse(gameSimulation.getCurrentTick());
            default -> new TickResponse(gameSimulation.getCurrentTick());
        };
    }
}
