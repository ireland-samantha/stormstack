package com.lightningfirefly.engine.quarkus.api.dto;

import java.util.List;

public record MatchResponse(long id, List<String> enabledModuleNames) {
}
