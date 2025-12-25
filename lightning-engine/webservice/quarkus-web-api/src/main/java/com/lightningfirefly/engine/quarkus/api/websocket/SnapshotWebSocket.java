package com.lightningfirefly.engine.quarkus.api.websocket;

import com.lightningfirefly.engine.quarkus.api.dto.SnapshotResponse;
import com.lightningfirefly.engine.core.GameSimulation;
import com.lightningfirefly.engine.core.snapshot.Snapshot;
import com.lightningfirefly.engine.core.snapshot.SnapshotFilter;
import com.lightningfirefly.engine.internal.core.snapshot.SnapshotProvider;
import io.quarkus.websockets.next.*;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.List;

/**
 * WebSocket endpoint for streaming match snapshots.
 *
 * <p>Clients connect to /ws/snapshots/{matchId} and receive periodic
 * components updates for the specified match.
 */
@WebSocket(path = "/ws/snapshots/{matchId}")
public class SnapshotWebSocket {

    @Inject
    GameSimulation gameSimulation;

    @Inject
    SnapshotProvider snapshotProvider;

    @ConfigProperty(name = "simulation.snapshot.broadcast-interval-ms", defaultValue = "100")
    long broadcastIntervalMs;

    @OnOpen
    public Multi<SnapshotResponse> onOpen(@PathParam String matchId) {
        long id = Long.parseLong(matchId);
        // Broadcast snapshots at configured interval
        return Multi.createFrom().ticks().every(Duration.ofMillis(broadcastIntervalMs))
                .map(tick -> createSnapshotResponse(id));
    }

    @OnTextMessage
    public SnapshotResponse onMessage(String message, @PathParam String matchId) {
        // Any message triggers an immediate components response
        long id = Long.parseLong(matchId);
        return createSnapshotResponse(id);
    }

    private SnapshotResponse createSnapshotResponse(long matchId) {
        Snapshot snapshot = snapshotProvider.createForMatch(matchId);
        return new SnapshotResponse(
                matchId,
                gameSimulation.getCurrentTick(),
                snapshot.snapshot()
        );
    }
}
