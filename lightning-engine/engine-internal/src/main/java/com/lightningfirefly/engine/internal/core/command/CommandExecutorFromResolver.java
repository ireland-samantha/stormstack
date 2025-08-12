package com.lightningfirefly.engine.internal.core.command;

import com.lightningfirefly.engine.core.command.CommandExecutor;
import com.lightningfirefly.engine.core.command.CommandPayload;
import com.lightningfirefly.engine.core.command.EngineCommand;
import lombok.extern.slf4j.Slf4j;

/**
 * CommandExecutor implementation that uses a CommandResolver to find commands.
 *
 * <p>This allows commands to be executed by name without requiring the command
 * map to be pre-built at construction time. Commands are resolved on-demand
 * from the loaded modules.
 */
@Slf4j
public class CommandExecutorFromResolver implements CommandExecutor {

    private final CommandResolver commandResolver;

    public CommandExecutorFromResolver(CommandResolver commandResolver) {
        this.commandResolver = commandResolver;
    }

    @Override
    public void executeCommand(String commandName, CommandPayload payload) {
        EngineCommand command = commandResolver.resolveByName(commandName);
        if (command == null) {
            log.warn("Command not found: {}", commandName);
            return;
        }

        try {
            command.executeCommand(payload);
        } catch (Exception e) {
            log.error("Error executing command '{}': {}", commandName, e.getMessage(), e);
        }
    }
}
