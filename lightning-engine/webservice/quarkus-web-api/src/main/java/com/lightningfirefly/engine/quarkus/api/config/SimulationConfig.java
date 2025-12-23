package com.lightningfirefly.engine.quarkus.api.config;

import com.lightningfirefly.engine.core.GameSimulation;
import com.lightningfirefly.engine.core.command.CommandExecutor;
import com.lightningfirefly.engine.core.command.CommandQueue;
import com.lightningfirefly.engine.core.match.MatchService;
import com.lightningfirefly.engine.core.match.PlayerMatchService;
import com.lightningfirefly.engine.core.match.PlayerService;
import com.lightningfirefly.engine.core.store.EntityComponentStore;
import com.lightningfirefly.engine.ext.module.ModuleContext;
import com.lightningfirefly.engine.ext.module.ModuleResolver;
import com.lightningfirefly.engine.internal.GameLoop;
import com.lightningfirefly.engine.internal.core.command.CommandQueueExecutor;
import com.lightningfirefly.engine.internal.core.command.CommandResolver;
import com.lightningfirefly.engine.internal.core.command.InMemoryCommandQueueManager;
import com.lightningfirefly.engine.internal.core.command.ModuleCommandResolver;
import com.lightningfirefly.engine.internal.core.command.CommandExecutorFromResolver;
import com.lightningfirefly.engine.internal.core.match.InMemoryGameSimulation;
import com.lightningfirefly.engine.internal.core.match.InMemoryMatchRepository;
import com.lightningfirefly.engine.internal.core.match.InMemoryMatchService;
import com.lightningfirefly.engine.internal.core.match.InMemoryPlayerMatchRepository;
import com.lightningfirefly.engine.internal.core.match.InMemoryPlayerMatchService;
import com.lightningfirefly.engine.internal.core.match.InMemoryPlayerRepository;
import com.lightningfirefly.engine.internal.core.match.InMemoryPlayerService;
import com.lightningfirefly.engine.internal.core.snapshot.SnapshotProvider;
import com.lightningfirefly.engine.internal.core.snapshot.SnapshotProviderImpl;
import com.lightningfirefly.engine.internal.core.store.ArrayEntityComponentStore;
import com.lightningfirefly.engine.internal.core.store.EcsProperties;
import com.lightningfirefly.engine.internal.core.resource.OnDiskResourceManager;
import com.lightningfirefly.engine.internal.ext.gamemaster.GameMasterFactoryFileLoader;
import com.lightningfirefly.engine.internal.ext.gamemaster.GameMasterManager;
import com.lightningfirefly.engine.internal.ext.gamemaster.GameMasterTickService;
import com.lightningfirefly.engine.internal.ext.gamemaster.OnDiskGameMasterManager;
import com.lightningfirefly.engine.internal.ext.module.DefaultInjector;
import com.lightningfirefly.engine.internal.ext.module.ModuleFactoryFileLoader;
import com.lightningfirefly.engine.internal.ext.module.ModuleManager;
import com.lightningfirefly.engine.internal.ext.module.OnDiskModuleManager;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Path;

/**
 * CDI configuration for simulation beans.
 */
@ApplicationScoped
public class SimulationConfig {

    @ConfigProperty(name = "ecs.max-entities", defaultValue = "1000000")
    int maxEntities;

    @ConfigProperty(name = "ecs.max-components", defaultValue = "100")
    int maxComponents;

    @ConfigProperty(name = "gameloop.max-commands-per-tick", defaultValue = "10000")
    int maxCommandsPerTick;

    @ConfigProperty(name = "storage.modules-path", defaultValue = "modules")
    String modulesPath;

    @ConfigProperty(name = "storage.gamemasters-path", defaultValue = "gamemasters")
    String gamemastersPath;

    @ConfigProperty(name = "storage.resources-path", defaultValue = "resources")
    String resourcesPath;

    // ---------- Core infrastructure ----------

    @Produces
    @ApplicationScoped
    public ModuleContext moduleDependencyInjector() {
        return new DefaultInjector();
    }

    @Produces
    @ApplicationScoped
    public EntityComponentStore entityComponentStore(ModuleContext context) {
        ArrayEntityComponentStore store =
                new ArrayEntityComponentStore(new EcsProperties(maxEntities, maxComponents));
        return store;
    }

    @Produces
    @ApplicationScoped
    public ModuleManager moduleManager(ModuleContext context) {
        OnDiskModuleManager manager =
                new OnDiskModuleManager(Path.of(modulesPath),
                        new ModuleFactoryFileLoader(),
                        context);
        return manager;
    }

    @Produces
    @ApplicationScoped
    public GameMasterManager gameMasterManager(ModuleContext context,
                                                CommandExecutor commandExecutor,
                                                OnDiskResourceManager resourceManager) {
        OnDiskGameMasterManager manager =
                new OnDiskGameMasterManager(Path.of(gamemastersPath),
                        new GameMasterFactoryFileLoader(),
                        context,
                        commandExecutor,
                        resourceManager);
        return manager;
    }

    @Produces
    @ApplicationScoped
    public GameMasterTickService gameMasterTickService(GameMasterManager gameMasterManager,
                                                        MatchService matchService) {
        return new GameMasterTickService(gameMasterManager, matchService);
    }

    // ---------- Command queue ----------

    /**
     * Produce the concrete implementation ONCE.
     * CDI will expose it as all implemented interfaces.
     */
    @Produces
    @ApplicationScoped
    public InMemoryCommandQueueManager commandQueueManager() {
        return new InMemoryCommandQueueManager();
    }

    // ---------- Resolvers ----------

    @Produces
    @ApplicationScoped
    public CommandResolver commandResolver(ModuleManager moduleManager) {
        return new ModuleCommandResolver(moduleManager);
    }

    @Produces
    @ApplicationScoped
    public CommandExecutor commandExecutor(CommandResolver commandResolver) {
        return new CommandExecutorFromResolver(commandResolver);
    }

    // ---------- Snapshot ----------

    @Produces
    @ApplicationScoped
    public SnapshotProvider snapshotProvider(EntityComponentStore store,
                                             ModuleManager moduleManager) {
        return new SnapshotProviderImpl(store, moduleManager);
    }

    // ---------- Match / Player services ----------

    @Produces
    @ApplicationScoped
    public MatchService matchService(ModuleResolver resolver) {
        InMemoryMatchService service =
                new InMemoryMatchService(new InMemoryMatchRepository(), resolver);
        return service;
    }

    @Produces
    @ApplicationScoped
    public PlayerService playerService() {
        return new InMemoryPlayerService(new InMemoryPlayerRepository());
    }

    @Produces
    @ApplicationScoped
    public PlayerMatchService playerMatchService(PlayerService playerService, MatchService matchService) {
        return new InMemoryPlayerMatchService(
                new InMemoryPlayerMatchRepository(),
                playerService,
                matchService);
    }

    // ---------- Resource management ----------

    @Produces
    @ApplicationScoped
    public OnDiskResourceManager resourceManager() {
        return new OnDiskResourceManager(Path.of(resourcesPath));
    }

    // ---------- Game loop & simulation ----------

    @Produces
    @ApplicationScoped
    public GameLoop gameLoop(ModuleResolver resolver,
                             CommandQueueExecutor executor,
                             GameMasterTickService gameMasterTickService) {
        return new GameLoop(resolver, executor, gameMasterTickService, maxCommandsPerTick);
    }

    @Produces
    @ApplicationScoped
    public GameSimulation gameSimulation(
            MatchService matchService,
            PlayerService playerService,
            PlayerMatchService playerMatchService,
            ModuleManager moduleManager,
            CommandResolver commandResolver,
            CommandQueue commandQueue,
            SnapshotProvider snapshotProvider,
            GameLoop gameLoop) {

        return new InMemoryGameSimulation(
                matchService,
                playerService,
                playerMatchService,
                moduleManager,
                commandResolver,
                commandQueue,
                snapshotProvider,
                gameLoop);
    }
}
