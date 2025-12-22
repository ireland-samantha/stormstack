package com.lightningfirefly.engine.internal.ext.gamemaster;

import com.lightningfirefly.engine.internal.ext.jar.JarFactoryLoader;
import com.lightningfirefly.game.engine.orchestrator.gm.GameMasterFactory;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Helper class to load JAR files and find GameMasterFactory implementations.
 *
 * <p>This class scans JAR files for classes implementing {@link GameMasterFactory}
 * and provides methods to instantiate them. It delegates to the generic
 * {@link JarFactoryLoader} for the actual JAR loading logic.
 */
@Slf4j
public class GameMasterFactoryFileLoader {

    private final JarFactoryLoader<GameMasterFactory> jarLoader;

    public GameMasterFactoryFileLoader() {
        this.jarLoader = new JarFactoryLoader<>(GameMasterFactory.class, "GameMasterFactory");
    }

    /**
     * Load a JAR file and find all GameMasterFactory implementations.
     *
     * @param jarFile the JAR file to load
     * @return list of GameMasterFactory instances found in the JAR
     * @throws IOException if the JAR file cannot be read
     */
    public List<GameMasterFactory> loadGameMasterFactories(File jarFile) throws IOException {
        return jarLoader.loadFactories(jarFile);
    }

    /**
     * Get the name of a game master from its factory.
     *
     * @param factory the game master factory
     * @return the game master name from the factory's getName() method
     */
    public String getGameMasterName(GameMasterFactory factory) {
        return factory.getName();
    }
}
