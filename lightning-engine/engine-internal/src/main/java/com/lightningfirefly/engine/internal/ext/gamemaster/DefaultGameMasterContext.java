package com.lightningfirefly.engine.internal.ext.gamemaster;

import com.lightningfirefly.game.engine.orchestrator.gm.GameMasterCommand;
import com.lightningfirefly.game.gm.GameMasterContext;
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
        // command, payload = toEngineCommand(gameMasterCommand);
        // CommandExecutor.execute(command, payload);
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
