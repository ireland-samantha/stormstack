package com.lightningfirefly.engine.core.command;

import java.util.Map;

public interface EngineCommand {
    /**
     * Get the unique name of this command.
     *
     * @return the command name
     */
    String getName();

    Map<String, Class<?>> schema();

    /**
     * Execute the command with the given payload.
     *
     * @param payload the command payload
     */
    void executeCommand(CommandPayload payload);
}
