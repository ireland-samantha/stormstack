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

import ca.samanthaireland.engine.core.container.ContainerConfig;
import ca.samanthaireland.engine.core.store.EntityComponentStore;
import ca.samanthaireland.engine.core.store.PermissionRegistry;
import ca.samanthaireland.engine.core.resources.ResourceManager;
import ca.samanthaireland.engine.core.command.CommandExecutor;
import ca.samanthaireland.engine.ext.module.ModuleResolver;
import ca.samanthaireland.engine.internal.GameLoop;
import ca.samanthaireland.engine.internal.core.command.CommandResolver;
import ca.samanthaireland.engine.internal.core.command.InMemoryCommandQueueManager;
import ca.samanthaireland.engine.internal.core.command.ModuleCommandResolver;
import ca.samanthaireland.engine.internal.core.command.CommandExecutorFromResolver;
import ca.samanthaireland.engine.internal.core.match.InMemoryMatchRepository;
import ca.samanthaireland.engine.internal.core.match.InMemoryMatchService;
import ca.samanthaireland.engine.internal.core.resource.OnDiskResourceManager;
import ca.samanthaireland.engine.internal.core.snapshot.SnapshotProvider;
import ca.samanthaireland.engine.internal.core.snapshot.SnapshotProviderImpl;
import ca.samanthaireland.engine.internal.core.store.ArrayEntityComponentStore;
import ca.samanthaireland.engine.internal.core.store.EcsProperties;
import ca.samanthaireland.engine.internal.core.store.LockingEntityComponentStore;
import ca.samanthaireland.engine.internal.core.store.SimplePermissionRegistry;
import ca.samanthaireland.engine.internal.ext.ai.AIFactoryFileLoader;
import ca.samanthaireland.engine.internal.ext.ai.AIManager;
import ca.samanthaireland.engine.internal.ext.ai.OnDiskAIManager;
import ca.samanthaireland.engine.internal.ext.module.DefaultInjector;
import ca.samanthaireland.engine.internal.ext.module.ModuleManager;
import ca.samanthaireland.engine.internal.ext.module.OnDiskModuleManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Handles initialization of all container components.
 *
 * <p>Extracted from InMemoryExecutionContainer to follow SRP.
 * Each container creates a ContainerComponentInitializer to set up its
 * isolated runtime environment.
 */
@Slf4j
@Getter
public class ContainerComponentInitializer {

    private final long containerId;
    private final ContainerConfig config;

    // Initialized components
    private ContainerClassLoader containerClassLoader;
    private EntityComponentStore entityStore;
    private PermissionRegistry permissionRegistry;
    private ModuleManager moduleManager;
    private AIManager aiManager;
    private OnDiskResourceManager resourceManager;
    private InMemoryMatchService matchService;
    private InMemoryCommandQueueManager commandQueueManager;
    private CommandResolver commandResolver;
    private GameLoop gameLoop;
    private DefaultInjector injector;

    /**
     * Creates a new initializer for the specified container.
     *
     * @param containerId the container ID
     * @param config the container configuration
     */
    public ContainerComponentInitializer(long containerId, ContainerConfig config) {
        this.containerId = containerId;
        this.config = config;
    }

    /**
     * Initializes all container components.
     *
     * <p>This method should be called once during container startup.
     * After calling this method, all component getters will return
     * the initialized instances.
     */
    public void initialize() {
        initializeClassLoaderAndStore();
        initializeInjector();
        initializeModuleManager();
        initializeCommandInfrastructure();
        initializeResourceAndAIManagers();
        initializeGameLoop();
        loadModules();

        log.debug("Container {} initialized: {} modules loaded", containerId, moduleManager.getAvailableModules().size());
    }

    private void initializeClassLoaderAndStore() {
        containerClassLoader = new ContainerClassLoader(containerId, getClass().getClassLoader());

        EcsProperties ecsProperties = new EcsProperties(config.maxEntities(), config.maxComponents());
        ArrayEntityComponentStore rawStore = new ArrayEntityComponentStore(ecsProperties);
        entityStore = LockingEntityComponentStore.wrap(rawStore);

        permissionRegistry = new SimplePermissionRegistry();
    }

    private void initializeInjector() {
        injector = new DefaultInjector();
        injector.addClass(EntityComponentStore.class, entityStore);
        injector.addClass(PermissionRegistry.class, permissionRegistry);

        InMemoryMatchRepository matchRepository = new InMemoryMatchRepository();
        matchService = new InMemoryMatchService(matchRepository, null);
        injector.addClass(ca.samanthaireland.engine.core.match.MatchService.class, matchService);
    }

    private void initializeModuleManager() {
        Path modulePath = config.moduleScanDirectory() != null
                ? config.moduleScanDirectory()
                : Path.of("modules");

        moduleManager = new OnDiskModuleManager(
                injector,
                permissionRegistry,
                modulePath.toString(),
                containerClassLoader
        );

        matchService.setModuleResolver(moduleManager);
        injector.addClass(ModuleManager.class, moduleManager);
        injector.addClass(ModuleResolver.class, moduleManager);
    }

    private void initializeCommandInfrastructure() {
        commandQueueManager = new InMemoryCommandQueueManager();
        commandResolver = new ModuleCommandResolver(moduleManager);
    }

    private void initializeResourceAndAIManagers() {
        Path resourcePath = Path.of("resources/container_" + containerId);
        resourceManager = new OnDiskResourceManager(resourcePath);
        injector.addClass(ResourceManager.class, resourceManager);

        CommandExecutor commandExecutor = new CommandExecutorFromResolver(commandResolver);
        injector.addClass(CommandExecutor.class, commandExecutor);

        aiManager = new OnDiskAIManager(
                Path.of("ai"),
                new AIFactoryFileLoader(),
                injector,
                commandExecutor,
                resourceManager
        );
        injector.addClass(AIManager.class, aiManager);

        SnapshotProvider snapshotProvider = new SnapshotProviderImpl(entityStore, moduleManager);
        injector.addClass(SnapshotProvider.class, snapshotProvider);
    }

    private void initializeGameLoop() {
        gameLoop = new GameLoop(moduleManager, commandQueueManager, config.maxCommandsPerTick());
    }

    private void loadModules() {
        for (String jarPath : config.moduleJarPaths()) {
            try {
                moduleManager.installModule(Path.of(jarPath));
            } catch (IOException e) {
                log.error("Failed to install module JAR {}: {}", jarPath, e.getMessage());
            }
        }

        try {
            moduleManager.reloadInstalled();
        } catch (IOException e) {
            log.error("Failed to reload modules: {}", e.getMessage());
        }
    }
}
