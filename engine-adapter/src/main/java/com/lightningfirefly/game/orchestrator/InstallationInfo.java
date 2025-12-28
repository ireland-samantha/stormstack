package com.lightningfirefly.game.orchestrator;

import java.util.Map;

/**
 * Record tracking resources installed for a game module.
 *
 * @param uploadedResourceIds maps texture paths to server resource IDs
 * @param installedGameMaster the game master name if we installed it, null otherwise
 */
public record InstallationInfo(
        Map<String, Long> uploadedResourceIds,
        String installedGameMaster
) {
}
