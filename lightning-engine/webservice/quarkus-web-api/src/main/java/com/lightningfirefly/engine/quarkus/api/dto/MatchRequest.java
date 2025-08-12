package com.lightningfirefly.engine.quarkus.api.dto;

import java.util.List;

public record MatchRequest(long id, List<String> enabledModuleNames) {
}
