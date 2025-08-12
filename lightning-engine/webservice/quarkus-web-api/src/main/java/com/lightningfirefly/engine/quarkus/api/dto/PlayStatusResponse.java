package com.lightningfirefly.engine.quarkus.api.dto;

/**
 * Response for play/stop simulation status.
 *
 * @param playing whether the simulation is auto-advancing
 * @param tick the current tick value
 * @param intervalMs the interval between ticks when playing (0 when stopped)
 */
public record PlayStatusResponse(boolean playing, long tick, long intervalMs) {
}
