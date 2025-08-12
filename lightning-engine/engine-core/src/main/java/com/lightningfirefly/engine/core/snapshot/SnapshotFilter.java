package com.lightningfirefly.engine.core.snapshot;

import java.util.List;

public record SnapshotFilter(List<Long> matchFilter, List<Long> playerFilter) {
}
