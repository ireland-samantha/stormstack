package com.lightningfirefly.game.orchestrator;

import com.lightningfirefly.game.domain.Sprite;

import java.util.List;

/**
 * Functional interface for mapping snapshots to sprites.
 */
@FunctionalInterface
public
interface SpriteMapper {
    /**
     * Convert a snapshot to a list of sprites.
     *
     * @param snapshot the ECS snapshot
     * @return list of sprites to render
     */
    List<Sprite> map(Object snapshot);
}
