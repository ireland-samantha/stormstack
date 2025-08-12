package com.lightningfirefly.engine.internal.core.command;

import com.lightningfirefly.engine.core.command.CommandExecutor;
import com.lightningfirefly.engine.core.command.CommandPayload;
import com.lightningfirefly.engine.core.command.EngineCommand;

import java.util.Map;

public class CommandExecutorImpl implements CommandExecutor {

    private final Map<String, EngineCommand> commands;

    public CommandExecutorImpl(Map<String, EngineCommand> commands) {
        this.commands = commands;
    }

    @Override
    public void executeCommand(String commandName, CommandPayload payload) {
        EngineCommand command = commands.get(commandName);
        if (command == null) {
            throw new IllegalArgumentException("Command not found: " + commandName);
        }
        command.executeCommand(payload);
    }
}
