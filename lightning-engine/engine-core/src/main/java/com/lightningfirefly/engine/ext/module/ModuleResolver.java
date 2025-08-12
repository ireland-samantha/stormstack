package com.lightningfirefly.engine.ext.module;

import java.util.List;

public interface ModuleResolver {
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
}
