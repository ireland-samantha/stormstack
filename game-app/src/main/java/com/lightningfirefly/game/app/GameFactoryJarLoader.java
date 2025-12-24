package com.lightningfirefly.game.app;

import com.lightningfirefly.game.backend.installation.GameFactory;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ServiceLoader;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Loads GameFactory implementations from JAR files.
 *
 * <p>Supports two loading mechanisms:
 * <ol>
 *   <li>Manifest attribute: JAR contains "Game-Factory-Class" attribute pointing to the class</li>
 *   <li>ServiceLoader: JAR provides META-INF/services/com.lightningfirefly.game.backend.installation.GameFactory</li>
 * </ol>
 *
 * <p>Example manifest:
 * <pre>
 * Manifest-Version: 1.0
 * Game-Factory-Class: com.example.mygame.MyGameFactory
 * </pre>
 */
@Slf4j
public class GameFactoryJarLoader {

    public static final String MANIFEST_ATTRIBUTE = "Game-Factory-Class";

    /**
     * Load a GameFactory from a JAR file.
     *
     * @param jarPath path to the JAR file
     * @return the loaded GameFactory instance
     * @throws Exception if loading fails
     */
    public GameFactory loadFromJar(Path jarPath) throws Exception {
        // Read manifest to get the Game-Factory-Class
        String factoryClassName = null;
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Manifest manifest = jarFile.getManifest();
            if (manifest != null) {
                factoryClassName = manifest.getMainAttributes().getValue(MANIFEST_ATTRIBUTE);
                log.debug("Found manifest attribute {}: {}", MANIFEST_ATTRIBUTE, factoryClassName);
            }
        }

        // Create class loader for the JAR
        URL jarUrl = jarPath.toUri().toURL();
        URLClassLoader classLoader = new URLClassLoader(
                new URL[]{jarUrl},
                GameFactoryJarLoader.class.getClassLoader()
        );

        // Try manifest-specified class first
        if (factoryClassName != null) {
            log.info("Loading GameFactory from manifest: {}", factoryClassName);
            try {
                Class<?> clazz = classLoader.loadClass(factoryClassName);
                if (GameFactory.class.isAssignableFrom(clazz)) {
                    return (GameFactory) clazz.getDeclaredConstructor().newInstance();
                } else {
                    log.warn("Class {} does not implement GameFactory", factoryClassName);
                }
            } catch (ClassNotFoundException e) {
                log.error("Class not found: {}", factoryClassName, e);
                throw e;
            }
        }

        // Try ServiceLoader as fallback
        log.debug("Trying ServiceLoader fallback");
        ServiceLoader<GameFactory> loader = ServiceLoader.load(GameFactory.class, classLoader);
        for (GameFactory factory : loader) {
            log.info("Loaded GameFactory via ServiceLoader: {}", factory.getClass().getName());
            return factory;
        }

        throw new IllegalArgumentException("No GameFactory found in JAR: " + jarPath);
    }
}
