package com.lightningfirefly.engine.internal.ext.gamemaster;

import com.lightningfirefly.engine.core.command.CommandExecutor;
import com.lightningfirefly.engine.core.command.CommandPayload;
import com.lightningfirefly.engine.core.resources.ResourceManager;
import com.lightningfirefly.game.backend.adapter.GameMasterCommand;
import com.lightningfirefly.game.domain.GameMasterContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default implementation of {@link GameMasterContext}.
 *
 * <p>Provides dependency injection and match-scoped context for game masters.
 */
@Slf4j
public class DefaultGameMasterContext implements GameMasterContext {

    private final long matchId;
    private final AtomicLong currentTick;
    private final Map<Class<?>, Object> dependencies;
    private CommandExecutor commandExecutor;
    private ResourceManager resourceManager;

    /**
     * Create a new game master context.
     *
     * @param matchId the match ID this context is bound to
     */
    public DefaultGameMasterContext(long matchId) {
        this.matchId = matchId;
        this.currentTick = new AtomicLong(0);
        this.dependencies = new ConcurrentHashMap<>();
    }

    @Override
    public long getMatchId() {
        return matchId;
    }

    @Override
    public long getCurrentTick() {
        return currentTick.get();
    }

    @Override
    public void executeCommand(GameMasterCommand gameMasterCommand) {
        if (commandExecutor == null) {
            log.warn("CommandExecutor not set, cannot execute command: {}", gameMasterCommand.commandName());
            return;
        }

        // Convert GameMasterCommand to CommandPayload
        CommandPayload payload = new GameMasterCommandPayload(gameMasterCommand.payload());

        // Execute the command
        try {
            commandExecutor.executeCommand(gameMasterCommand.commandName(), payload);
        } catch (Exception e) {
            log.warn("Failed to execute command '{}': {}", gameMasterCommand.commandName(), e.getMessage());
        }
    }

    /**
     * Set the command executor for this context.
     *
     * @param commandExecutor the command executor
     */
    public void setCommandExecutor(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    /**
     * Set the resource manager for this context.
     *
     * @param resourceManager the resource manager
     */
    public void setResourceManager(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    @Override
    public long getResourceIdByName(String resourceName) {
        if (resourceManager == null) {
            log.warn("ResourceManager not set, cannot look up resource: {}", resourceName);
            return -1;
        }
        return resourceManager.getResourceIdByName(resourceName);
    }

    /**
     * Internal payload wrapper for GameMasterCommand payloads.
     */
    private record GameMasterCommandPayload(Map<String, Object> payload) implements CommandPayload {
        @Override
        public Map<String, Object> getPayload() {
            return payload;
        }
    }

    /**
     * Update the current tick number.
     *
     * @param tick the new tick number
     */
    public void setCurrentTick(long tick) {
        currentTick.set(tick);
    }

    /**
     * Register a dependency for lookup.
     *
     * @param type the class type to register
     * @param instance the instance to associate with the type
     */
    public void addDependency(Class<?> type, Object instance) {
        dependencies.put(type, instance);
    }

    /**
     * Copy dependencies from another context or dependency source.
     *
     * @param deps the dependencies to copy
     */
    public void copyDependencies(Map<Class<?>, Object> deps) {
        dependencies.putAll(deps);
    }
}
