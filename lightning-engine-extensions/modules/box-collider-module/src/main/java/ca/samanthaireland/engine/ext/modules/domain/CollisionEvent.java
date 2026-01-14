package ca.samanthaireland.engine.ext.modules.domain;

/**
 * Collision event record containing all collision information.
 */
public record CollisionEvent(
        long selfEntity,
        long otherEntity,
        CollisionInfo info,
        float param1,
        float param2,
        int handlerType,
        long tick
) {}
