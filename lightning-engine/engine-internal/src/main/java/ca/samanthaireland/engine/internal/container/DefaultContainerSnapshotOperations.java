/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

package ca.samanthaireland.engine.internal.container;

import ca.samanthaireland.engine.core.container.ContainerSnapshotOperations;
import ca.samanthaireland.engine.core.snapshot.Snapshot;
import ca.samanthaireland.engine.internal.core.snapshot.SnapshotProvider;

/**
 * Default implementation of container-scoped snapshot operations.
 *
 * <p>Uses the container's injected SnapshotProvider to create snapshots
 * that are properly scoped to this container.
 */
public class DefaultContainerSnapshotOperations implements ContainerSnapshotOperations {

    private final SnapshotProvider snapshotProvider;

    public DefaultContainerSnapshotOperations(SnapshotProvider snapshotProvider) {
        this.snapshotProvider = snapshotProvider;
    }

    @Override
    public Snapshot forMatch(long matchId) {
        return snapshotProvider.createForMatch(matchId);
    }

    @Override
    public Snapshot forMatchAndPlayer(long matchId, long playerId) {
        return snapshotProvider.createForMatchAndPlayer(matchId, playerId);
    }
}
