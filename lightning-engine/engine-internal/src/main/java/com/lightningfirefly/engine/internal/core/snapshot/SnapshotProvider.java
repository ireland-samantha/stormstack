package com.lightningfirefly.engine.internal.core.snapshot;

import com.lightningfirefly.engine.core.snapshot.Snapshot;
import com.lightningfirefly.engine.core.snapshot.SnapshotFilter;

/**
 * Provides snapshots of the simulation state.
 *
 * <p>Snapshots capture the current state of entities and their components,
 * optionally filtered by match or player.
 */
public interface SnapshotProvider {

    /**
     * Create a snapshot with the given filter criteria.
     *
     * @return the snapshot containing filtered entity component data
     */
    Snapshot createForMatch(long matchId);

}
