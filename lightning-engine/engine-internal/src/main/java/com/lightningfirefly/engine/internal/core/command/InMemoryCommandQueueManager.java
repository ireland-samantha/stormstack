package com.lightningfirefly.engine.internal.core.command;

import com.lightningfirefly.engine.core.command.CommandPayload;
import com.lightningfirefly.engine.core.command.CommandQueue;
import com.lightningfirefly.engine.core.command.EngineCommand;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe in-memory implementation of {@link CommandQueue} and {@link CommandQueueExecutor}.
 *
 * <p>Commands are enqueued when scheduled and dequeued when executed.
 */
@Slf4j
public class InMemoryCommandQueueManager implements CommandQueue, CommandQueueExecutor {

    private final ConcurrentLinkedQueue<ScheduledCommand> commandQueue = new ConcurrentLinkedQueue<>();

    @Override
    public void enqueue(EngineCommand command, CommandPayload payload) {
        if (command == null) {
            log.warn("Attempted to schedule null command");
            return;
        }
        commandQueue.add(new ScheduledCommand(command, payload));
        log.debug("Scheduled command: {}", command.getName());
    }

    @Override
    public void executeCommands(int amount) {
        log.info("Execute {} commands.", amount);
        if (amount <= 0) {
            return;
        }

        int executed = 0;
        while (executed < amount) {
            ScheduledCommand scheduled = commandQueue.poll();
            if (scheduled == null) {
                break;
            }

            try {
                scheduled.command().executeCommand(scheduled.payload());
                log.debug("Executed command: {}", scheduled.command().getName());
                executed++;
            } catch (Exception e) {
                log.error("Failed to execute command: {}", scheduled.command().getName(), e);
            }
        }

        log.debug("Executed {} commands", executed);
    }

    /**
     * Get the number of commands currently in the queue.
     *
     * @return the queue size
     */
    public int getQueueSize() {
        return commandQueue.size();
    }

    /**
     * Clear all pending commands from the queue.
     */
    public void clear() {
        commandQueue.clear();
        log.debug("Command queue cleared");
    }

    /**
     * A scheduled command with its payload.
     */
    private record ScheduledCommand(EngineCommand command, CommandPayload payload) {
    }
}
