package com.lightningfirefly.engine.internal.core.command;

import com.lightningfirefly.engine.core.command.EngineCommand;

import java.util.List;

public interface CommandResolver {
    EngineCommand resolveByName(String name);
    List<EngineCommand> getAll();
}

