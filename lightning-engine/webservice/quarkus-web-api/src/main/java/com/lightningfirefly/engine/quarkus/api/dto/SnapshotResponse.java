package com.lightningfirefly.engine.quarkus.api.dto;

import java.util.List;
import java.util.Map;

public record SnapshotResponse(
        long matchId,
        long tick,
        Map<String, Map<String, List<Float>>> data
) {
}
