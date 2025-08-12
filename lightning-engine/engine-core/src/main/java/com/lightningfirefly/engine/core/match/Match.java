package com.lightningfirefly.engine.core.match;

import java.util.List;

public record Match(long id, List<String> enabledModules, List<String> enabledGameMasters) {
}
