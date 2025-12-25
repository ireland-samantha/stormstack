package com.lightningfirefly.game.orchestrator;

import com.lightningfirefly.game.domain.Sprite;

import java.util.List;

/**
 * Functional interface for mapping snapshots to sprites.
 */
@FunctionalInterface
public
interface SpriteSnapshotMapper {
    /**
     * Convert a components to a list of sprites.
     *
     * @param snapshot the ECS components
     * @return list of sprites to render
     */
    List<Sprite> spritesFromSnapshot(Object snapshot);
}
