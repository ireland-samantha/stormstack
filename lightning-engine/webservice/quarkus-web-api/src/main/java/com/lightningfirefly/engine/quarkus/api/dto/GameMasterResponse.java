package com.lightningfirefly.engine.quarkus.api.dto;

/**
 * Response DTO for game master information.
 *
 * @param name           the game master name
 * @param enabledMatches number of matches this game master is enabled in
 */
public record GameMasterResponse(String name, int enabledMatches) {
}
