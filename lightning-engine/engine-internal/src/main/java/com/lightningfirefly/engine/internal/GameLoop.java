package com.lightningfirefly.engine.internal;

import com.lightningfirefly.engine.core.system.EngineSystem;
import com.lightningfirefly.engine.ext.module.EngineModule;
import com.lightningfirefly.engine.ext.module.ModuleResolver;
import com.lightningfirefly.engine.internal.core.command.CommandQueueExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Main game loop that processes simulation ticks.
 *
 * <p>Each tick consists of:
 * <ol>
 *   <li>Execute queued commands up to the current tick</li>
 *   <li>Run all systems from all loaded modules</li>
 * </ol>
 *
 * <p>Follows SOLID principles:
 * <ul>
 *   <li>SRP: Only orchestrates tick execution, delegates to CommandQueueExecutor and EngineSystems</li>
 *   <li>DIP: Depends on abstractions (ModuleResolver, CommandQueueExecutor)</li>
 * </ul>
 *
 * <p>Thread Safety: This class caches the system list using volatile for visibility.
 * The cache is invalidated when modules are added/removed.
 */
@Slf4j
public class GameLoop {

    private static final int DEFAULT_MAX_COMMANDS_PER_TICK = 10000;

    private final ModuleResolver moduleResolver;
    private final CommandQueueExecutor commandQueueExecutor;
    private final int maxCommandsPerTick;

    // Cached list of systems for performance - volatile for thread visibility
    private volatile List<EngineSystem> cachedSystems;

    /**
     * Create a new game loop with command execution support.
     *
     * @param moduleResolver resolver for loaded modules (must not be null)
     * @param commandQueueExecutor executor for processing commands (may be null to disable commands)
     * @throws NullPointerException if moduleResolver is null
     */
    public GameLoop(ModuleResolver moduleResolver, CommandQueueExecutor commandQueueExecutor) {
        this(moduleResolver, commandQueueExecutor, DEFAULT_MAX_COMMANDS_PER_TICK);
    }

    /**
     * Create a new game loop with configurable command execution.
     *
     * @param moduleResolver resolver for loaded modules (must not be null)
     * @param commandQueueExecutor executor for processing commands (may be null to disable commands)
     * @param maxCommandsPerTick maximum number of commands to execute per tick
     * @throws NullPointerException if moduleResolver is null
     * @throws IllegalArgumentException if maxCommandsPerTick is not positive
     */
    public GameLoop(ModuleResolver moduleResolver, CommandQueueExecutor commandQueueExecutor, int maxCommandsPerTick) {
        this.moduleResolver = Objects.requireNonNull(moduleResolver, "moduleResolver must not be null");
        this.commandQueueExecutor = commandQueueExecutor;
        if (maxCommandsPerTick <= 0) {
            throw new IllegalArgumentException("maxCommandsPerTick must be positive, got: " + maxCommandsPerTick);
        }
        this.maxCommandsPerTick = maxCommandsPerTick;
    }

    /**
     * Create a game loop without command execution.
     *
     * @param moduleResolver resolver for loaded modules (must not be null)
     * @throws NullPointerException if moduleResolver is null
     */
    public GameLoop(ModuleResolver moduleResolver) {
        this(moduleResolver, null, DEFAULT_MAX_COMMANDS_PER_TICK);
    }

    /**
     * Advance the simulation by one tick.
     *
     * <p>This method:
     * <ol>
     *   <li>Executes all commands scheduled for this tick (up to maxCommandsPerTick)</li>
     *   <li>Runs all systems from loaded modules</li>
     * </ol>
     *
     * <p>System execution continues even if one system throws an exception.
     * Exceptions are logged but do not stop the tick.
     *
     * @param tick the current tick number
     */
    public void advanceTick(long tick) {
        log.trace("Advancing tick: {}", tick);

        // Execute commands scheduled for this tick
        executeCommands();

        // Run all systems
        List<EngineSystem> systems = getOrBuildSystems();
        int systemsRun = runSystems(systems);

        log.trace("Tick {} complete, {} systems executed", tick, systemsRun);
    }

    /**
     * Execute queued commands up to the configured limit.
     */
    private void executeCommands() {
        if (commandQueueExecutor != null) {
            commandQueueExecutor.executeCommands(maxCommandsPerTick);
        }
    }

    /**
     * Run all systems, handling exceptions gracefully.
     *
     * @param systems the systems to run
     * @return the number of systems that ran successfully
     */
    private int runSystems(List<EngineSystem> systems) {
        int successCount = 0;
        for (EngineSystem system : systems) {
            try {
                system.updateEntities();
                successCount++;
            } catch (Exception e) {
                log.error("Error executing system: {}", system.getClass().getSimpleName(), e);
            }
        }
        return successCount;
    }

    /**
     * Invalidate the cached systems list.
     *
     * <p>Call this when modules are added or removed.
     */
    public void invalidateCache() {
        cachedSystems = null;
        log.debug("Game loop cache invalidated");
    }

    /**
     * Get or build the list of all systems from loaded modules.
     *
     * @return list of all systems
     */
    private List<EngineSystem> getOrBuildSystems() {
        List<EngineSystem> systems = cachedSystems;
        if (systems == null) {
            systems = buildSystemsList();
            cachedSystems = systems;
        }
        return systems;
    }

    /**
     * Build the list of all systems from all modules.
     *
     * @return list of all systems
     */
    private List<EngineSystem> buildSystemsList() {
        List<EngineSystem> allSystems = new ArrayList<>();
        List<EngineModule> modules = moduleResolver.resolveAllModules();

        for (EngineModule module : modules) {
            List<EngineSystem> moduleSystems = module.createSystems();
            if (moduleSystems != null) {
                allSystems.addAll(moduleSystems);
            }
        }

        log.debug("Built systems list with {} systems from {} modules",
                allSystems.size(), modules.size());
        return allSystems;
    }
}
