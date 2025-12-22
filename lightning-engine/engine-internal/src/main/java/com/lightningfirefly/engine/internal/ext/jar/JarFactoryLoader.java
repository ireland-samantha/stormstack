package com.lightningfirefly.engine.internal.ext.jar;

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
 * Generic JAR file loader for factory implementations.
 *
 * <p>This class scans JAR files for classes implementing a specific factory interface
 * and provides methods to instantiate them. It abstracts the common JAR loading logic
 * used by both modules and game masters.
 *
 * @param <F> the factory interface type to load
 */
@Slf4j
public class JarFactoryLoader<F> {

    private final Class<F> factoryInterface;
    private final String factoryTypeName;

    /**
     * Create a new JAR factory loader.
     *
     * @param factoryInterface the factory interface class to search for
     * @param factoryTypeName human-readable name for logging (e.g., "ModuleFactory", "GameMasterFactory")
     */
    public JarFactoryLoader(Class<F> factoryInterface, String factoryTypeName) {
        this.factoryInterface = factoryInterface;
        this.factoryTypeName = factoryTypeName;
    }

    /**
     * Load a JAR file and find all factory implementations.
     *
     * @param jarFile the JAR file to load
     * @return list of factory instances found in the JAR
     * @throws IOException if the JAR file cannot be read
     */
    public List<F> loadFactories(File jarFile) throws IOException {
        List<F> factories = new ArrayList<>();

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
                    Optional<F> factory = tryLoadFactory(classLoader, className);
                    factory.ifPresent(factories::add);
                }
            }
        }

        log.info("Loaded {} {} implementations from {}", factories.size(), factoryTypeName, jarFile.getName());
        return factories;
    }

    /**
     * Try to load a class and instantiate it as a factory.
     *
     * @param classLoader the class loader to use
     * @param className the fully qualified class name
     * @return Optional containing the factory if successful
     */
    private Optional<F> tryLoadFactory(ClassLoader classLoader, String className) {
        try {
            Class<?> clazz = classLoader.loadClass(className);

            if (factoryInterface.isAssignableFrom(clazz) && !clazz.isInterface()) {
                @SuppressWarnings("unchecked")
                F factory = (F) clazz.getDeclaredConstructor().newInstance();
                log.debug("Found {}: {}", factoryTypeName, className);
                return Optional.of(factory);
            }
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            log.trace("Class not found: {}", className);
        } catch (NoSuchMethodException e) {
            log.warn("{} {} does not have a no-arg constructor", factoryTypeName, className);
        } catch (Exception e) {
            log.warn("Failed to instantiate {} {}: {}", factoryTypeName, className, e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Get the name derived from a factory class name.
     *
     * <p>Uses the simple class name without "Factory" suffix.
     *
     * @param factory the factory instance
     * @return the derived name
     */
    public String getFactoryName(F factory) {
        String className = factory.getClass().getSimpleName();
        if (className.endsWith("Factory")) {
            return className.substring(0, className.length() - "Factory".length());
        }
        return className;
    }
}
