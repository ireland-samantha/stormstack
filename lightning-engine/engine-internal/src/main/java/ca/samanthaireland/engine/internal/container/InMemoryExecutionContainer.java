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


package ca.samanthaireland.engine.internal.container;

import ca.samanthaireland.engine.core.command.CommandPayload;
import ca.samanthaireland.engine.core.command.EngineCommand;
import ca.samanthaireland.engine.core.exception.EntityNotFoundException;
import ca.samanthaireland.engine.core.match.Match;
import ca.samanthaireland.engine.core.container.ExecutionContainer;
import ca.samanthaireland.engine.core.container.ContainerConfig;
import ca.samanthaireland.engine.core.container.ContainerStatus;
import ca.samanthaireland.engine.core.container.ContainerModuleOperations;
import ca.samanthaireland.engine.core.container.ContainerAIOperations;
import ca.samanthaireland.engine.core.container.ContainerResourceOperations;
import ca.samanthaireland.engine.core.container.ContainerSnapshotOperations;
import ca.samanthaireland.engine.core.container.ContainerLifecycleOperations;
import ca.samanthaireland.engine.core.container.ContainerTickOperations;
import ca.samanthaireland.engine.core.container.ContainerCommandOperations;
import ca.samanthaireland.engine.core.container.ContainerMatchOperations;
import ca.samanthaireland.engine.core.store.EntityComponentStore;
import ca.samanthaireland.engine.core.store.PermissionRegistry;
import ca.samanthaireland.engine.ext.module.ModuleFactory;
import ca.samanthaireland.engine.ext.module.ModuleResolver;
import ca.samanthaireland.engine.internal.GameLoop;
import ca.samanthaireland.engine.internal.core.command.CommandQueueExecutor;
import ca.samanthaireland.engine.internal.core.command.CommandResolver;
import ca.samanthaireland.engine.internal.core.command.InMemoryCommandQueueManager;
import ca.samanthaireland.engine.internal.core.command.ModuleCommandResolver;
import ca.samanthaireland.engine.internal.core.match.InMemoryMatchRepository;
import ca.samanthaireland.engine.internal.core.match.InMemoryMatchService;
import ca.samanthaireland.engine.internal.core.store.ArrayEntityComponentStore;
import ca.samanthaireland.engine.internal.core.store.EcsProperties;
import ca.samanthaireland.engine.internal.core.store.LockingEntityComponentStore;
import ca.samanthaireland.engine.internal.core.store.SimplePermissionRegistry;
import ca.samanthaireland.engine.internal.ext.module.DefaultInjector;
import ca.samanthaireland.engine.internal.ext.module.ModuleManager;
import ca.samanthaireland.engine.internal.ext.module.OnDiskModuleManager;
import ca.samanthaireland.engine.internal.ext.ai.AIManager;
import ca.samanthaireland.engine.internal.ext.ai.AIFactoryFileLoader;
import ca.samanthaireland.engine.internal.ext.ai.OnDiskAIManager;
import ca.samanthaireland.engine.internal.core.command.CommandExecutorFromResolver;
import ca.samanthaireland.engine.internal.core.snapshot.SnapshotProvider;
import ca.samanthaireland.engine.internal.core.snapshot.SnapshotProviderImpl;
import ca.samanthaireland.engine.internal.core.resource.OnDiskResourceManager;
import ca.samanthaireland.engine.core.command.CommandExecutor;
import ca.samanthaireland.engine.core.resources.ResourceManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Full implementation of ExecutionContainer with complete runtime isolation.
 *
 * <p>Each container has:
 * <ul>
 *     <li>Isolated classloader for module JARs</li>
 *     <li>Separate EntityComponentStore</li>
 *     <li>Independent GameLoop with its own tick thread</li>
 *     <li>Container-scoped command queue</li>
 * </ul>
 */
@Slf4j
public class InMemoryExecutionContainer implements ExecutionContainer, Closeable {

    private final long id;
    private final ContainerConfig config;
    private final AtomicReference<ContainerStatus> status;
    private final AtomicLong currentTick;
    private final AtomicLong autoAdvanceInterval;

    // Isolated components (created during start())
    private ContainerClassLoader containerClassLoader;
    
    @Getter
    private EntityComponentStore entityStore;
    private PermissionRegistry permissionRegistry;
    private ModuleManager moduleManager;
    private AIManager aiManager;
    private OnDiskResourceManager resourceManager;
    private InMemoryMatchService matchService;
    private InMemoryCommandQueueManager commandQueueManager;
    private CommandResolver commandResolver;

    @Getter
    private GameLoop gameLoop;
    private DefaultInjector injector;

    // Fluent API operations
    private ContainerModuleOperations moduleOperations;
    private ContainerAIOperations aiOperations;
    private ContainerResourceOperations resourceOperations;
    private ContainerLifecycleOperations lifecycleOperations;
    private ContainerTickOperations tickOperations;
    private ContainerCommandOperations commandOperations;
    private ContainerMatchOperations matchOperations;
    private ContainerSnapshotOperations snapshotOperations;

    // Tick execution
    private final ScheduledExecutorService tickExecutor;
    private volatile ScheduledFuture<?> autoAdvanceTask;

    /**
     * Creates a new execution container.
     *
     * @param id     the unique container ID
     * @param config the container configuration
     */
    public InMemoryExecutionContainer(long id, ContainerConfig config) {
        this.id = id;
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.status = new AtomicReference<>(ContainerStatus.CREATED);
        this.currentTick = new AtomicLong(0);
        this.autoAdvanceInterval = new AtomicLong(0);

        // Create tick executor for this container
        this.tickExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "container-" + id + "-tick");
            t.setDaemon(true);
            return t;
        });

        log.info("Created container {} with name '{}'", id, config.name());
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public String getName() {
        return config.name();
    }

    @Override
    public ContainerConfig getConfig() {
        return config;
    }

    @Override
    public ContainerStatus getStatus() {
        return status.get();
    }

    // =========================================================================
    // LIFECYCLE (Internal methods - use lifecycle() fluent API)
    // =========================================================================

    /**
     * Internal implementation of start. Use {@link #lifecycle()}.start() instead.
     */
    void startInternal() {
        if (!status.compareAndSet(ContainerStatus.CREATED, ContainerStatus.STARTING)) {
            throw new IllegalStateException("Container can only be started from CREATED state, current: " + status.get());
        }

        try {
            log.info("Starting container {} '{}'", id, config.name());
            initializeComponents();
            status.set(ContainerStatus.RUNNING);
            log.info("Container {} '{}' started successfully", id, config.name());
        } catch (Exception e) {
            status.set(ContainerStatus.STOPPED);
            log.error("Failed to start container {} '{}': {}", id, config.name(), e.getMessage(), e);
            throw new RuntimeException("Failed to start container: " + e.getMessage(), e);
        }
    }

    private void initializeComponents() {
        // Create isolated classloader
        containerClassLoader = new ContainerClassLoader(id, getClass().getClassLoader());

        // Create ECS store with container config
        EcsProperties ecsProperties = new EcsProperties(config.maxEntities(), config.maxComponents());
        ArrayEntityComponentStore rawStore = new ArrayEntityComponentStore(ecsProperties);
        entityStore = LockingEntityComponentStore.wrap(rawStore);

        // Create permission registry
        permissionRegistry = new SimplePermissionRegistry();

        // Create injector for dependency injection
        injector = new DefaultInjector();
        injector.addClass(EntityComponentStore.class, entityStore);
        injector.addClass(PermissionRegistry.class, permissionRegistry);

        // Create match service
        InMemoryMatchRepository matchRepository = new InMemoryMatchRepository();
        matchService = new InMemoryMatchService(matchRepository, null); // ModuleManager set later
        injector.addClass(ca.samanthaireland.engine.core.match.MatchService.class, matchService);

        // Create module manager with container classloader
        Path modulePath = config.moduleScanDirectory();
        if (modulePath == null) {
            modulePath = Path.of("modules"); // Default
        }
        moduleManager = new OnDiskModuleManager(
                injector,
                permissionRegistry,
                modulePath.toString(),
                containerClassLoader
        );
        matchService.setModuleResolver(moduleManager);
        injector.addClass(ModuleManager.class, moduleManager);
        injector.addClass(ModuleResolver.class, moduleManager);

        // Create command queue and resolver
        commandQueueManager = new InMemoryCommandQueueManager();
        commandResolver = new ModuleCommandResolver(moduleManager);

        // InMemoryCommandQueueManager implements both CommandQueue and CommandQueueExecutor
        // Commands are already resolved when enqueued, so executeCommands just runs them

        // Create resource manager for this container
        Path resourcePath = Path.of("resources/container_" + id);
        resourceManager = new OnDiskResourceManager(resourcePath);
        injector.addClass(ResourceManager.class, resourceManager);

        // Create command executor for AI
        CommandExecutor commandExecutor = new CommandExecutorFromResolver(commandResolver);
        injector.addClass(CommandExecutor.class, commandExecutor);

        // Create AI manager for this container
        Path aiPath = Path.of("ai");
        aiManager = new OnDiskAIManager(
                aiPath,
                new AIFactoryFileLoader(),
                injector,  // injector implements ModuleContext
                commandExecutor,
                resourceManager
        );
        injector.addClass(AIManager.class, aiManager);

        // Create snapshot provider for this container
        SnapshotProvider containerSnapshotProvider = new SnapshotProviderImpl(entityStore, moduleManager);
        injector.addClass(SnapshotProvider.class, containerSnapshotProvider);

        // Create game loop - commandQueueManager implements CommandQueueExecutor
        gameLoop = new GameLoop(moduleManager, commandQueueManager, config.maxCommandsPerTick());

        // Load pre-configured module JARs
        for (String jarPath : config.moduleJarPaths()) {
            try {
                moduleManager.installModule(Path.of(jarPath));
            } catch (IOException e) {
                log.error("Failed to install module JAR {}: {}", jarPath, e.getMessage());
            }
        }

        // Scan module directory
        try {
            moduleManager.reloadInstalled();
        } catch (IOException e) {
            log.error("Failed to reload modules: {}", e.getMessage());
        }

        log.debug("Container {} components initialized: {} modules loaded", id, moduleManager.getAvailableModules().size());
    }

    /**
     * Internal implementation of stop. Use {@link #lifecycle()}.stop() instead.
     */
    void stopInternal() {
        ContainerStatus current = status.get();
        if (current == ContainerStatus.STOPPED || current == ContainerStatus.STOPPING) {
            return; // Already stopped/stopping
        }

        if (!status.compareAndSet(current, ContainerStatus.STOPPING)) {
            return; // Another thread is stopping
        }

        log.info("Stopping container {} '{}'", id, config.name());

        try {
            stopAutoAdvanceInternal();
            tickExecutor.shutdown();
            if (!tickExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                tickExecutor.shutdownNow();
            }

            if (gameLoop != null) {
                gameLoop.shutdown();
            }

            if (containerClassLoader != null) {
                containerClassLoader.close();
            }

            status.set(ContainerStatus.STOPPED);
            log.info("Container {} '{}' stopped", id, config.name());
        } catch (Exception e) {
            log.error("Error stopping container {} '{}': {}", id, config.name(), e.getMessage(), e);
            status.set(ContainerStatus.STOPPED);
        }
    }

    /**
     * Internal implementation of pause. Use {@link #lifecycle()}.pause() instead.
     */
    void pauseInternal() {
        if (!status.compareAndSet(ContainerStatus.RUNNING, ContainerStatus.PAUSED)) {
            throw new IllegalStateException("Container can only be paused from RUNNING state");
        }
        stopAutoAdvanceInternal();
        log.info("Container {} '{}' paused", id, config.name());
    }

    /**
     * Internal implementation of resume. Use {@link #lifecycle()}.resume() instead.
     */
    void resumeInternal() {
        if (!status.compareAndSet(ContainerStatus.PAUSED, ContainerStatus.RUNNING)) {
            throw new IllegalStateException("Container can only be resumed from PAUSED state");
        }
        log.info("Container {} '{}' resumed", id, config.name());
    }

    @Override
    public void close() throws IOException {
        stopInternal();
    }

    // =========================================================================
    // TICK CONTROL (Internal methods - use ticks() fluent API)
    // =========================================================================

    /**
     * Internal implementation of getCurrentTick. Use {@link #ticks()}.current() instead.
     */
    long getCurrentTickInternal() {
        return currentTick.get();
    }

    /**
     * Internal implementation of advanceTick. Use {@link #ticks()}.advance() instead.
     */
    long advanceTickInternal() {
        checkRunning();
        long tick = currentTick.incrementAndGet();
        tickExecutor.execute(() -> gameLoop.advanceTick(tick));
        return tick;
    }

    /**
     * Internal implementation of startAutoAdvance. Use {@link #ticks()}.play() instead.
     */
    void startAutoAdvanceInternal(long intervalMs) {
        checkRunning();
        if (intervalMs <= 0) {
            throw new IllegalArgumentException("intervalMs must be positive");
        }

        stopAutoAdvanceInternal(); // Stop any existing auto-advance

        autoAdvanceInterval.set(intervalMs);
        autoAdvanceTask = tickExecutor.scheduleAtFixedRate(
                this::advanceTickSafe,
                0,
                intervalMs,
                TimeUnit.MILLISECONDS
        );

        log.info("Container {} '{}' auto-advance started at {} ms interval", id, config.name(), intervalMs);
    }

    private void advanceTickSafe() {
        try {
            if (status.get() == ContainerStatus.RUNNING) {
                advanceTickInternal();
            }
        } catch (Exception e) {
            log.error("Error during auto-advance tick in container {}: {}", id, e.getMessage(), e);
        }
    }

    /**
     * Internal implementation of stopAutoAdvance. Use {@link #ticks()}.stop() instead.
     */
    void stopAutoAdvanceInternal() {
        if (autoAdvanceTask != null) {
            autoAdvanceTask.cancel(false);
            autoAdvanceTask = null;
            autoAdvanceInterval.set(0);
            log.debug("Container {} auto-advance stopped", id);
        }
    }

    /**
     * Internal implementation of isAutoAdvancing. Use {@link #ticks()}.isPlaying() instead.
     */
    boolean isAutoAdvancingInternal() {
        return autoAdvanceTask != null && !autoAdvanceTask.isCancelled();
    }

    /**
     * Internal implementation of getAutoAdvanceInterval. Use {@link #ticks()}.interval() instead.
     */
    long getAutoAdvanceIntervalInternal() {
        return autoAdvanceInterval.get();
    }

    // =========================================================================
    // MATCH MANAGEMENT (Internal methods - use matches() fluent API)
    // =========================================================================

    /**
     * Internal implementation of createMatch. Use {@link #matches()}.create() instead.
     */
    Match createMatchInternal(Match match) {
        checkRunning();
        Match containerMatch = match.withContainerId(id);
        return matchService.createMatch(containerMatch);
    }

    /**
     * Internal implementation of getMatch. Use {@link #matches()}.get() instead.
     */
    Optional<Match> getMatchInternal(long matchId) {
        return matchService != null ? matchService.getMatch(matchId) : Optional.empty();
    }

    /**
     * Internal implementation of getAllMatches. Use {@link #matches()}.all() instead.
     */
    List<Match> getAllMatchesInternal() {
        return matchService != null ? matchService.getAllMatches() : List.of();
    }

    /**
     * Internal implementation of deleteMatch. Use {@link #matches()}.delete() instead.
     */
    void deleteMatchInternal(long matchId) {
        matchService.deleteMatch(matchId);
    }

    @Override
    public List<ExecutionContainer.CommandInfo> getAvailableCommands() {
        if (commandResolver == null) {
            return List.of();
        }

        return commandResolver.getAll().stream()
                .map(cmd -> {
                    List<ExecutionContainer.ParameterInfo> params;
                    if (cmd.getParameters() != null) {
                        // Use explicit parameter definitions if available
                        params = cmd.getParameters().stream()
                                .map(p -> new ExecutionContainer.ParameterInfo(
                                        p.name(),
                                        p.type(),
                                        p.required(),
                                        p.description()))
                                .toList();
                    } else {
                        // Fall back to schema() for parameter info
                        params = cmd.schema().entrySet().stream()
                                .map(entry -> new ExecutionContainer.ParameterInfo(
                                        entry.getKey(),
                                        getSimpleTypeName(entry.getValue()),
                                        true,  // Assume required
                                        null))
                                .toList();
                    }
                    return new ExecutionContainer.CommandInfo(
                            cmd.getName(),
                            cmd.getDescription(),
                            cmd.getModuleName(),
                            params
                    );
                })
                .toList();
    }

    /**
     * Get a simple type name for display (e.g., "long" instead of "class java.lang.Long").
     */
    private String getSimpleTypeName(Class<?> type) {
        if (type == null) return "unknown";
        if (type == Long.class || type == long.class) return "long";
        if (type == Integer.class || type == int.class) return "int";
        if (type == Double.class || type == double.class) return "double";
        if (type == Float.class || type == float.class) return "float";
        if (type == Boolean.class || type == boolean.class) return "boolean";
        if (type == String.class) return "String";
        return type.getSimpleName();
    }

    /**
     * Internal implementation of enqueueCommand. Use {@link #commands()}.named(commandName).execute() instead.
     */
    void enqueueCommandInternal(String commandName, CommandPayload payload) {
        checkRunning();

        EngineCommand command = commandResolver.resolveByName(commandName);
        if (command == null) {
            throw new EntityNotFoundException("Command not found: " + commandName);
        }

        commandQueueManager.enqueue(command, payload);
    }

    /**
     * Internal implementation of enqueueCommand with match context.
     * Use {@link #commands()}.named(commandName).forMatch(matchId).execute() instead.
     */
    void enqueueCommandInternal(long matchId, String commandName, CommandPayload payload) {
        checkRunning();

        EngineCommand command = commandResolver.resolveByName(commandName);
        if (command == null) {
            throw new EntityNotFoundException("Command not found: " + commandName);
        }

        // Note: matchId context is typically handled by the payload itself
        // when the command is invoked
        commandQueueManager.enqueue(command, payload);
    }

    @Override
    public ContainerStats getStats() {
        Runtime runtime = Runtime.getRuntime();

        int entityCount = entityStore != null ? entityStore.getEntityCount() : 0;
        int maxEntities = config.maxEntities();

        // Estimate memory used by the ECS store (entities * maxComponents * 4 bytes per float)
        long usedMemoryBytes = (long) entityCount * config.maxComponents() * Float.BYTES;

        // Max memory from config (convert MB to bytes)
        long maxMemoryBytes = config.maxMemoryMb() > 0
                ? config.maxMemoryMb() * 1024 * 1024
                : 0;

        // JVM memory stats
        long jvmMaxMemoryBytes = runtime.maxMemory();
        long jvmUsedMemoryBytes = runtime.totalMemory() - runtime.freeMemory();

        int matchCount = matchService != null ? matchService.getAllMatches().size() : 0;
        int moduleCount = moduleManager != null ? moduleManager.getAvailableModules().size() : 0;

        return new ContainerStats(
                entityCount,
                maxEntities,
                usedMemoryBytes,
                maxMemoryBytes,
                jvmMaxMemoryBytes,
                jvmUsedMemoryBytes,
                matchCount,
                moduleCount
        );
    }

    private void checkRunning() {
        if (status.get() != ContainerStatus.RUNNING) {
            throw new IllegalStateException("Container must be in RUNNING state, current: " + status.get());
        }
    }

    private void checkNotStopped() {
        ContainerStatus current = status.get();
        if (current == ContainerStatus.STOPPED || current == ContainerStatus.STOPPING) {
            throw new IllegalStateException("Container is stopped");
        }
    }

    // =========================================================================
    // FLUENT API
    // =========================================================================

    @Override
    public ContainerModuleOperations modules() {
        if (moduleOperations == null || moduleManager == null) {
            // Create with current moduleManager (may be null before start)
            moduleOperations = new DefaultContainerModuleOperations(this, moduleManager);
        }
        return moduleOperations;
    }

    @Override
    public ContainerAIOperations ai() {
        if (aiOperations == null && aiManager != null) {
            aiOperations = new DefaultContainerAIOperations(this, aiManager);
        }
        return aiOperations;
    }

    @Override
    public ContainerResourceOperations resources() {
        if (resourceOperations == null && resourceManager != null) {
            resourceOperations = new DefaultContainerResourceOperations(this, resourceManager);
        }
        return resourceOperations;
    }

    @Override
    public ContainerLifecycleOperations lifecycle() {
        if (lifecycleOperations == null) {
            lifecycleOperations = new DefaultContainerLifecycleOperations(this);
        }
        return lifecycleOperations;
    }

    @Override
    public ContainerTickOperations ticks() {
        if (tickOperations == null) {
            tickOperations = new DefaultContainerTickOperations(this);
        }
        return tickOperations;
    }

    @Override
    public ContainerCommandOperations commands() {
        if (commandOperations == null) {
            commandOperations = new DefaultContainerCommandOperations(this);
        }
        return commandOperations;
    }

    @Override
    public ContainerMatchOperations matches() {
        if (matchOperations == null) {
            matchOperations = new DefaultContainerMatchOperations(this);
        }
        return matchOperations;
    }

    @Override
    public ContainerSnapshotOperations snapshots() {
        if (snapshotOperations == null && injector != null) {
            SnapshotProvider provider = injector.getClass(SnapshotProvider.class);
            if (provider != null) {
                snapshotOperations = new DefaultContainerSnapshotOperations(provider);
            }
        }
        return snapshotOperations;
    }
}
