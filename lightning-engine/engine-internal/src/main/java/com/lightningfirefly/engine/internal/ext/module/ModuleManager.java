package com.lightningfirefly.engine.internal.ext.module;

import com.lightningfirefly.engine.ext.module.EngineModule;
import com.lightningfirefly.engine.ext.module.ModuleFactory;
import com.lightningfirefly.engine.ext.module.ModuleResolver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Manages modules from external sources (e.g., JAR files).
 *
 * <p>Provides functionality to scan for modules, install new modules,
 * and resolve modules by name.
 */
public interface ModuleManager extends ModuleResolver {

    /**
     * Scan for available modules.
     *
     * <p>This method scans the configured location for module implementations
     * and registers them for later resolution.
     *
     * @throws IOException if the scan fails
     */
    void reloadInstalled() throws IOException;

    /**
     * Install a module from a file.
     *
     * <p>This method installs the module from the specified file,
     * making it available for resolution.
     *
     * @param moduleFile the path to the module file to install
     * @throws IOException if the installation fails
     */
    void installModule(Path moduleFile) throws IOException;

    /**
     * Install a module from a factory class.
     *
     * <p>This method instantiates the factory class and registers the module
     * for later resolution. Useful for programmatic module registration
     * without requiring JAR files.
     *
     * @param moduleFactory the module factory class to install
     */
    void installModule(Class<? extends ModuleFactory> moduleFactory);

    /**
     * Clear all caches and reset the scanned state.
     *
     * <p>Useful for hot-reloading modules.
     */
    void reset();

    /**
     * Resolve a specific module by name.
     *
     * @param moduleName the name of the module to resolve
     * @return the resolved module, or null if not found
     */
    EngineModule resolveModule(String moduleName);

    /**
     * Get all available module names.
     *
     * @return list of available module names
     */
    List<String> getAvailableModules();

    /**
     * Resolve all available modules.
     *
     * @return list of all resolved modules
     */
    List<EngineModule> resolveAllModules();

    /**
     * Check if a module with the given name is available.
     *
     * @param moduleName the module name to check
     * @return true if the module is available
     */
    boolean hasModule(String moduleName);

    /**
     * Uninstall a module by name.
     *
     * <p>This removes the module from the cache and optionally deletes
     * the associated JAR file from the scan directory.
     *
     * @param moduleName the name of the module to uninstall
     * @return true if the module was found and uninstalled
     */
    boolean uninstallModule(String moduleName);
}
