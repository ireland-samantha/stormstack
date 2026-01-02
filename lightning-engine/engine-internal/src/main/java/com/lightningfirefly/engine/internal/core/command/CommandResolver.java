package com.lightningfirefly.engine.internal.core.command;

import com.lightningfirefly.engine.core.command.EngineCommand;

import java.util.List;
import java.util.Map;

public interface CommandResolver {
    EngineCommand resolveByName(String name);
    List<EngineCommand> getAll();

    /**
     * Get commands grouped by module name.
     *
     * @return map of module name to list of commands from that module
     */
    default Map<String, List<EngineCommand>> getGroupedByModule() {
        return Map.of();
    }
}

