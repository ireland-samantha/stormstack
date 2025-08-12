package com.lightningfirefly.engine.quarkus.api.dto;

/**
 * Response DTO for module information.
 *
 * @param name              the module name
 * @param flagComponentName the name of the module's flag component (for isolation)
 * @param enabledMatches    number of matches this module is enabled in
 */
public record ModuleResponse(String name, String flagComponentName, int enabledMatches) {
}
