package com.lightningfirefly.engine.core.command;

public interface CommandExecutor {
    /**
     * executes a command in the same tick.
     * @param commandName
     * @param payload
     */
    void executeCommand(String commandName, CommandPayload payload);
}
