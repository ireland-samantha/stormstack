package com.lightningfirefly.engine.internal.ext.gamemaster;

import com.lightningfirefly.engine.core.command.CommandExecutor;
import com.lightningfirefly.engine.core.entity.EntityFactory;
import com.lightningfirefly.engine.core.resources.ResourceManager;
import com.lightningfirefly.engine.core.store.EntityComponentStore;
import com.lightningfirefly.engine.ext.module.ModuleContext;
import com.lightningfirefly.game.gm.GameMaster;
import com.lightningfirefly.game.engine.orchestrator.gm.GameMasterFactory;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Manages game masters located in a server directory.
 *
 * <p>Provides per-match isolation by creating new game master instances for each match.
 */
@Slf4j
public class OnDiskGameMasterManager implements GameMasterManager {

    private final Path scanDirectory;
    private final GameMasterFactoryFileLoader factoryFileLoader;
    private final ModuleContext moduleContext;
    private final CommandExecutor commandExecutor;
    private final ResourceManager resourceManager;

    private final Map<String, GameMasterFactory> factoryCache = new ConcurrentHashMap<>();
    private volatile boolean scanned = false;

    /**
     * Create a game master manager with a custom directory.
     *
     * @param scanDirectory the directory to scan for JAR files
     * @param factoryFileLoader the loader for JAR files
     * @param moduleContext the module context for dependency injection
     * @param commandExecutor the command executor for game master commands
     * @param resourceManager the resource manager for resource lookup
     */
    public OnDiskGameMasterManager(Path scanDirectory,
                                    GameMasterFactoryFileLoader factoryFileLoader,
                                    ModuleContext moduleContext,
                                    CommandExecutor commandExecutor,
                                    ResourceManager resourceManager) {
        this.scanDirectory = scanDirectory;
        this.factoryFileLoader = factoryFileLoader;
        this.moduleContext = moduleContext;
        this.commandExecutor = commandExecutor;
        this.resourceManager = resourceManager;
    }

    @Override
    public void reloadInstalled() throws IOException {
        log.info("Scanning for game masters in: {}", scanDirectory.toAbsolutePath());

        if (!Files.exists(scanDirectory)) {
            log.warn("Game master directory does not exist: {}", scanDirectory);
            Files.createDirectories(scanDirectory);
            scanned = true;
            return;
        }

        if (!Files.isDirectory(scanDirectory)) {
            log.error("Game master path is not a directory: {}", scanDirectory);
            scanned = true;
            return;
        }

        try (Stream<Path> paths = Files.list(scanDirectory)) {
            paths.filter(path -> path.toString().endsWith(".jar"))
                    .forEach(this::loadJarFile);
        }

        scanned = true;
        log.info("Game master scan complete. Found {} game masters: {}", factoryCache.size(), factoryCache.keySet());
    }

    private void loadJarFile(Path jarPath) {
        try {
            File jarFile = jarPath.toFile();
            List<GameMasterFactory> factories = factoryFileLoader.loadGameMasterFactories(jarFile);

            for (GameMasterFactory factory : factories) {
                registerFactory(factory, jarFile.getName());
            }
        } catch (IOException e) {
            log.error("Failed to load JAR file: {}", jarPath, e);
        }
    }

    private void registerFactory(GameMasterFactory factory, String source) {
        String name = factory.getName();

        if (factoryCache.containsKey(name)) {
            log.warn("Duplicate game master name '{}' found. Overwriting with game master from {}",
                    name, source);
        }

        factoryCache.put(name, factory);
        log.info("Registered game master: {} from {}", name, source);
    }

    @Override
    public void installGameMaster(Class<? extends GameMasterFactory> gameMasterFactory) {
        try {
            GameMasterFactory factory = gameMasterFactory.getDeclaredConstructor().newInstance();
            registerFactory(factory, "class " + gameMasterFactory.getName());
        } catch (Exception e) {
            log.error("Failed to instantiate game master factory: {}", gameMasterFactory.getName(), e);
        }
    }

    @Override
    public void installGameMaster(Path jarFile) throws IOException {
        if (!Files.exists(jarFile)) {
            throw new IOException("JAR file does not exist: " + jarFile);
        }

        if (!jarFile.toString().endsWith(".jar")) {
            throw new IOException("File is not a JAR file: " + jarFile);
        }

        if (!Files.exists(scanDirectory)) {
            Files.createDirectories(scanDirectory);
        }

        Path targetPath = scanDirectory.resolve(jarFile.getFileName());
        Files.copy(jarFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("Copied JAR file to scan directory: {} -> {}", jarFile, targetPath);

        reset();
        reloadInstalled();
    }

    private void ensureScanned() {
        if (!scanned) {
            try {
                reloadInstalled();
            } catch (IOException e) {
                log.error("Failed to scan game master directory", e);
            }
        }
    }

    @Override
    public GameMasterFactory getFactory(String gameMasterName) {
        ensureScanned();
        return factoryCache.get(gameMasterName);
    }

    @Override
    public GameMaster createForMatch(String gameMasterName, long matchId) {
        ensureScanned();

        GameMasterFactory factory = factoryCache.get(gameMasterName);
        if (factory == null) {
            log.warn("No game master found for: {}", gameMasterName);
            return null;
        }

        DefaultGameMasterContext context = new DefaultGameMasterContext(matchId);
        context.addDependency(EntityComponentStore.class, moduleContext.getEntityComponentStore());
        context.addDependency(EntityFactory.class, moduleContext.getEntityFactory());
        context.setCommandExecutor(commandExecutor);
        context.setResourceManager(resourceManager);

        try {
            return factory.create(context);
        } catch (Exception e) {
            log.error("Failed to create game master '{}' for match {}: {}", gameMasterName, matchId, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public List<String> getAvailableGameMasters() {
        ensureScanned();
        return new ArrayList<>(factoryCache.keySet());
    }

    @Override
    public boolean hasGameMaster(String gameMasterName) {
        ensureScanned();
        return factoryCache.containsKey(gameMasterName);
    }

    @Override
    public boolean uninstallGameMaster(String gameMasterName) {
        ensureScanned();

        GameMasterFactory removed = factoryCache.remove(gameMasterName);

        if (removed != null) {
            log.info("Uninstalled game master: {}", gameMasterName);
            return true;
        }

        log.warn("Game master not found for uninstall: {}", gameMasterName);
        return false;
    }

    @Override
    public void reset() {
        factoryCache.clear();
        scanned = false;
        log.info("Game master manager reset");
    }

    /**
     * Get the configured scan directory path.
     *
     * @return the scan directory path
     */
    public Path getScanDirectory() {
        return scanDirectory;
    }
}
