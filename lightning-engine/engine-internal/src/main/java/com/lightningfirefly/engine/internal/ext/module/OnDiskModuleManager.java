package com.lightningfirefly.engine.internal.ext.module;

import com.lightningfirefly.engine.ext.module.EngineModule;
import com.lightningfirefly.engine.ext.module.ModuleContext;
import com.lightningfirefly.engine.ext.module.ModuleFactory;
import com.lightningfirefly.engine.ext.module.ModuleResolver;
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
 * Manages modules located in a server directory.
 */
@Slf4j
public class OnDiskModuleManager implements ModuleManager {

    private final Path scanDirectory;
    private final ModuleFactoryFileLoader moduleFactoryFileLoader;
    private volatile ModuleContext moduleContext;

    private final Map<String, ModuleFactory> factoryCache = new ConcurrentHashMap<>();
    private final Map<String, EngineModule> moduleCache = new ConcurrentHashMap<>();

    private volatile boolean scanned = false;

    /**
     * Create a JarModuleResolver with a custom directory and JarLoader.
     *
     * @param scanDirectory the directory to scan for JAR files
     * @param moduleFactoryFileLoader the JarLoader to use for loading JAR files
     * @param moduleContext the module context for dependency injection
     */
    public OnDiskModuleManager(Path scanDirectory, ModuleFactoryFileLoader moduleFactoryFileLoader, ModuleContext moduleContext) {
        this.scanDirectory = scanDirectory;
        this.moduleFactoryFileLoader = moduleFactoryFileLoader;
        this.moduleContext = moduleContext;
    }

    /**
     * Scan the JAR directory for ModuleFactory implementations.
     *
     * <p>This method scans all JAR files in the configured directory and
     * registers any ModuleFactory implementations found.
     *
     * @throws IOException if the directory cannot be read
     */
    public void reloadInstalled() throws IOException {
        log.info("Scanning for modules in: {}", scanDirectory.toAbsolutePath());

        if (!Files.exists(scanDirectory)) {
            log.warn("JAR directory does not exist: {}", scanDirectory);
            Files.createDirectories(scanDirectory);
            scanned = true;
            return;
        }

        if (!Files.isDirectory(scanDirectory)) {
            log.error("JAR path is not a directory: {}", scanDirectory);
            scanned = true;
            return;
        }

        try (Stream<Path> paths = Files.list(scanDirectory)) {
            paths.filter(path -> path.toString().endsWith(".jar"))
                    .forEach(this::loadJarFile);
        }

        scanned = true;
        log.info("Module scan complete. Found {} modules: {}", factoryCache.size(), factoryCache.keySet());
    }

    /**
     * Load a single JAR file and register its ModuleFactories.
     *
     * @param jarPath the path to the JAR file
     */
    private void loadJarFile(Path jarPath) {
        try {
            File jarFile = jarPath.toFile();
            List<ModuleFactory> factories = moduleFactoryFileLoader.loadModuleFactories(jarFile);

            for (ModuleFactory factory : factories) {
                initializeAndCacheModule(factory, jarFile.getName());
            }
        } catch (IOException e) {
            log.error("Failed to load JAR file: {}", jarPath, e);
        }
    }

    /**
     * Initialize a module from its factory and cache both.
     *
     * <p>This method eagerly initializes modules during scanning/registration
     * rather than lazily on first access, preventing potential server stutters.
     *
     * @param factory the module factory
     * @param source description of where the factory came from (for logging)
     */
    private void initializeAndCacheModule(ModuleFactory factory, String source) {
        try {
            EngineModule module = factory.create(moduleContext);
            String moduleName = module.getName();

            if (moduleCache.containsKey(moduleName)) {
                log.warn("Duplicate module name '{}' found. Overwriting with module from {}",
                        moduleName, source);
            }

            factoryCache.put(moduleName, factory);
            moduleCache.put(moduleName, module);
            log.info("Registered and initialized module: {} from {}", moduleName, source);
        } catch (Exception e) {
            String factoryName = moduleFactoryFileLoader.getModuleName(factory);
            log.error("Failed to initialize module from factory '{}' (source: {}): {}",
                    factoryName, source, e.getMessage(), e);
        }
    }

    @Override
    public void installModule(Class<? extends ModuleFactory> moduleFactoryClass) {
        try {
            ModuleFactory factory = moduleFactoryClass.getDeclaredConstructor().newInstance();
            initializeAndCacheModule(factory, "class " + moduleFactoryClass.getName());
        } catch (Exception e) {
            log.error("Failed to instantiate module factory: {}", moduleFactoryClass.getName(), e);
        }
    }

    /**
     * Install a module from a JAR file by copying it to the scan directory and rescanning.
     *
     * <p>This method copies the specified JAR file to the configured scan directory,
     * then triggers a full rescan to load the new module.
     *
     * @param jarFile the path to the JAR file to install
     * @throws IOException if the file cannot be copied or the scan fails
     */
    @Override
    public void installModule(Path jarFile) throws IOException {
        if (!Files.exists(jarFile)) {
            throw new IOException("JAR file does not exist: " + jarFile);
        }

        if (!jarFile.toString().endsWith(".jar")) {
            throw new IOException("File is not a JAR file: " + jarFile);
        }

        // Ensure scan directory exists
        if (!Files.exists(scanDirectory)) {
            Files.createDirectories(scanDirectory);
        }

        // Copy JAR to scan directory
        Path targetPath = scanDirectory.resolve(jarFile.getFileName());
        Files.copy(jarFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("Copied JAR file to scan directory: {} -> {}", jarFile, targetPath);

        // Reset and rescan to pick up the new module
        reset();
        reloadInstalled();
    }

    /**
     * Ensure the directory has been scanned before resolving modules.
     */
    private void ensureScanned() {
        if (!scanned) {
            try {
                reloadInstalled();
            } catch (IOException e) {
                log.error("Failed to scan JAR directory", e);
            }
        }
    }

    @Override
    public EngineModule resolveModule(String moduleName) {
        ensureScanned();

        EngineModule module = moduleCache.get(moduleName);
        if (module == null) {
            log.warn("No module found for: {}", moduleName);
        }
        return module;
    }

    @Override
    public List<String> getAvailableModules() {
        ensureScanned();
        return new ArrayList<>(factoryCache.keySet());
    }

    @Override
    public List<EngineModule> resolveAllModules() {
        ensureScanned();
        return new ArrayList<>(moduleCache.values());
    }

    /**
     * Clear all caches and reset the scanned state.
     * Useful for hot-reloading modules.
     */
    public void reset() {
        factoryCache.clear();
        moduleCache.clear();
        scanned = false;
        log.info("Module resolver reset");
    }

    /**
     * Get the configured JAR directory path.
     *
     * @return the JAR directory path
     */
    public Path getScanDirectory() {
        return scanDirectory;
    }

    /**
     * Check if a module with the given name is available.
     *
     * @param moduleName the module name to check
     * @return true if the module is available
     */
    public boolean hasModule(String moduleName) {
        ensureScanned();
        return factoryCache.containsKey(moduleName);
    }

    /**
     * Uninstall a module by name.
     *
     * <p>This removes the module from the cache. Note that this does not
     * delete the JAR file from disk - it will be reloaded on next restart
     * unless the file is manually deleted.
     *
     * @param moduleName the name of the module to uninstall
     * @return true if the module was found and uninstalled
     */
    @Override
    public boolean uninstallModule(String moduleName) {
        ensureScanned();

        ModuleFactory removed = factoryCache.remove(moduleName);
        moduleCache.remove(moduleName);

        if (removed != null) {
            log.info("Uninstalled module: {}", moduleName);
            return true;
        }

        log.warn("Module not found for uninstall: {}", moduleName);
        return false;
    }
}
