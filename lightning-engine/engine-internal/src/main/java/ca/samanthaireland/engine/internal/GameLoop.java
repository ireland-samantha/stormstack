/*
 * Copyright (c) 2026 Samantha Ireland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */


package ca.samanthaireland.engine.internal;

import ca.samanthaireland.engine.core.system.EngineSystem;
import ca.samanthaireland.engine.ext.module.EngineModule;
import ca.samanthaireland.engine.ext.module.ModuleResolver;
import ca.samanthaireland.engine.internal.core.command.CommandQueueExecutor;
import ca.samanthaireland.engine.internal.ext.ai.AITickService;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private final AITickService aiTickService;
    private final int maxCommandsPerTick;

    // Cached list of systems for performance - volatile for thread visibility
    private volatile List<EngineSystem> cachedSystems;

    // Tick listeners for post-tick notifications (thread-safe for concurrent modification)
    private final List<TickListener> tickListeners = new CopyOnWriteArrayList<>();

    // Thread pool for async tick listener notification (fire and forget)
    private final ExecutorService tickListenerExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "TickListener-Worker");
        t.setDaemon(true);
        return t;
    });

    /**
     * Create a new game loop with command execution support.
     *
     * @param moduleResolver resolver for loaded modules (must not be null)
     * @param commandQueueExecutor executor for processing commands (may be null to disable commands)
     * @throws NullPointerException if moduleResolver is null
     */
    public GameLoop(ModuleResolver moduleResolver, CommandQueueExecutor commandQueueExecutor) {
        this(moduleResolver, commandQueueExecutor, null, DEFAULT_MAX_COMMANDS_PER_TICK);
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
        this(moduleResolver, commandQueueExecutor, null, maxCommandsPerTick);
    }

    /**
     * Create a new game loop with game master support.
     *
     * @param moduleResolver resolver for loaded modules (must not be null)
     * @param commandQueueExecutor executor for processing commands (may be null to disable commands)
     * @param aiTickService service for executing game masters (may be null to disable game masters)
     * @param maxCommandsPerTick maximum number of commands to execute per tick
     * @throws NullPointerException if moduleResolver is null
     * @throws IllegalArgumentException if maxCommandsPerTick is not positive
     */
    public GameLoop(ModuleResolver moduleResolver, CommandQueueExecutor commandQueueExecutor,
                    AITickService aiTickService, int maxCommandsPerTick) {
        this.moduleResolver = Objects.requireNonNull(moduleResolver, "moduleResolver must not be null");
        this.commandQueueExecutor = commandQueueExecutor;
        this.aiTickService = aiTickService;
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
        this(moduleResolver, null, null, DEFAULT_MAX_COMMANDS_PER_TICK);
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
        log.info("Advancing tick: {}", tick);

        // Execute commands scheduled for this tick
        executeCommands();
        log.info("Commands executed");

        // Run all systems
        List<EngineSystem> systems = getOrBuildSystems();
        int systemsRun = runSystems(systems);

        log.info("Systems executed");

        // Run AI
        executeAIs(tick);

        log.info("AI executed");

        // Notify tick listeners
        notifyTickListeners(tick);
        log.info("Listeners notified");

        log.info("Tick {} complete, {} systems executed", tick, systemsRun);
    }

    /**
     * Execute AI for all active matches.
     */
    private void executeAIs(long tick) {
        if (aiTickService != null) {
            aiTickService.onTick(tick);
        }
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

    /**
     * Register a tick listener to be notified after each tick completes.
     *
     * @param listener the listener to register (must not be null)
     * @throws NullPointerException if listener is null
     */
    public void addTickListener(TickListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        tickListeners.add(listener);
        log.debug("Registered tick listener: {}", listener.getClass().getSimpleName());
    }

    /**
     * Remove a previously registered tick listener.
     *
     * @param listener the listener to remove
     * @return true if the listener was removed, false if it was not registered
     */
    public boolean removeTickListener(TickListener listener) {
        boolean removed = tickListeners.remove(listener);
        if (removed) {
            log.debug("Removed tick listener: {}", listener.getClass().getSimpleName());
        }
        return removed;
    }

    /**
     * Notify all registered tick listeners that a tick has completed.
     *
     * <p>Notifications are sent asynchronously (fire and forget) to avoid
     * blocking the game loop. Each listener is invoked in a separate task
     * to prevent slow listeners from affecting others.
     *
     * @param tick the tick number that just completed
     */
    private void notifyTickListeners(long tick) {
        for (TickListener listener : tickListeners) {
            tickListenerExecutor.submit(() -> {
                try {
                    listener.onTickComplete(tick);
                } catch (Exception e) {
                    log.error("Error in tick listener: {}", listener.getClass().getSimpleName(), e);
                }
            });
        }
    }

    /**
     * Shutdown the tick listener executor.
     *
     * <p>Call this when the game loop is no longer needed to clean up resources.
     */
    public void shutdown() {
        tickListenerExecutor.shutdown();
        log.info("GameLoop tick listener executor shutdown initiated");
    }
}
