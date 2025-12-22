package com.lightningfirefly.engine.internal.ext.module;

import com.lightningfirefly.engine.ext.module.ModuleFactory;
import com.lightningfirefly.engine.internal.ext.jar.JarFactoryLoader;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Helper class to load JAR files and find ModuleFactory implementations.
 *
 * <p>This class scans JAR files for classes implementing {@link ModuleFactory}
 * and provides methods to instantiate them. It delegates to the generic
 * {@link JarFactoryLoader} for the actual JAR loading logic.
 */
@Slf4j
public class ModuleFactoryFileLoader {

    private final JarFactoryLoader<ModuleFactory> jarLoader;

    public ModuleFactoryFileLoader() {
        this.jarLoader = new JarFactoryLoader<>(ModuleFactory.class, "ModuleFactory");
    }

    /**
     * Load a JAR file and find all ModuleFactory implementations.
     *
     * @param jarFile the JAR file to load
     * @return list of ModuleFactory instances found in the JAR
     * @throws IOException if the JAR file cannot be read
     */
    public List<ModuleFactory> loadModuleFactories(File jarFile) throws IOException {
        return jarLoader.loadFactories(jarFile);
    }

    /**
     * Get the name of a module from its factory.
     * Uses the simple class name without "Factory" suffix.
     *
     * @param factory the module factory
     * @return the derived module name
     */
    public String getModuleName(ModuleFactory factory) {
        return jarLoader.getFactoryName(factory);
    }
}
