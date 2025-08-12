package com.lightningfirefly.engine.internal.ext.module;

import com.lightningfirefly.engine.ext.module.ModuleFactory;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Helper class to load JAR files and find ModuleFactory implementations.
 *
 * <p>This class scans JAR files for classes implementing {@link ModuleFactory}
 * and provides methods to instantiate them.
 */
@Slf4j
public class ModuleFactoryFileLoader {

    /**
     * Load a JAR file and find all ModuleFactory implementations.
     *
     * @param jarFile the JAR file to load
     * @return list of ModuleFactory instances found in the JAR
     * @throws IOException if the JAR file cannot be read
     */
    public List<ModuleFactory> loadModuleFactories(File jarFile) throws IOException {
        List<ModuleFactory> factories = new ArrayList<>();

        if (!jarFile.exists() || !jarFile.isFile() || !jarFile.getName().endsWith(".jar")) {
            log.warn("Invalid JAR file: {}", jarFile);
            return factories;
        }

        URL jarUrl = jarFile.toURI().toURL();

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{jarUrl}, getClass().getClassLoader());
             JarFile jar = new JarFile(jarFile)) {

            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.endsWith(".class") && !name.contains("$")) {
                    String className = name.replace('/', '.').replace(".class", "");
                    Optional<ModuleFactory> factory = tryLoadModuleFactory(classLoader, className);
                    factory.ifPresent(factories::add);
                }
            }
        }

        log.info("Loaded {} ModuleFactory implementations from {}", factories.size(), jarFile.getName());
        return factories;
    }

    /**
     * Try to load a class and instantiate it as a ModuleFactory.
     *
     * @param classLoader the class loader to use
     * @param className the fully qualified class name
     * @return Optional containing the ModuleFactory if successful
     */
    private Optional<ModuleFactory> tryLoadModuleFactory(ClassLoader classLoader, String className) {
        try {
            Class<?> clazz = classLoader.loadClass(className);

            if (ModuleFactory.class.isAssignableFrom(clazz) && !clazz.isInterface()) {
                ModuleFactory factory = (ModuleFactory) clazz.getDeclaredConstructor().newInstance();
                log.debug("Found ModuleFactory: {}", className);
                return Optional.of(factory);
            }
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            log.trace("Class not found: {}", className);
        } catch (NoSuchMethodException e) {
            log.warn("ModuleFactory {} does not have a no-arg constructor", className);
        } catch (Exception e) {
            log.warn("Failed to instantiate ModuleFactory {}: {}", className, e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Get the name of a module from its factory.
     * Uses the simple class name without "Factory" suffix.
     *
     * @param factory the module factory
     * @return the derived module name
     */
    public String getModuleName(ModuleFactory factory) {
        String className = factory.getClass().getSimpleName();
        if (className.endsWith("Factory")) {
            return className.substring(0, className.length() - "Factory".length());
        }
        return className;
    }
}
