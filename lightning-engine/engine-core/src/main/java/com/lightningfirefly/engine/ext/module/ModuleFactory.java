package com.lightningfirefly.engine.ext.module;

/**
 * Factory interface for creating engine modules.
 *
 * <p>Each module implementation should provide a factory that implements this interface.
 * The factory is responsible for creating configured instances of the module.
 */
public interface ModuleFactory {

    /**
     * Create an instance of the engine module.
     *
     * @param context the module context for dependency injection
     * @return the created module instance
     */
    EngineModule create(ModuleContext context);
}
