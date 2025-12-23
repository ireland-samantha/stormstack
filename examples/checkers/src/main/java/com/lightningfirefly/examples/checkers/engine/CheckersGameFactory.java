package com.lightningfirefly.examples.checkers.engine;

import com.lightningfirefly.game.engine.ControlSystem;
import com.lightningfirefly.game.engine.GameFactory;
import com.lightningfirefly.game.engine.GameScene;
import com.lightningfirefly.game.engine.Sprite;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;

/**
 * GameFactory for checkers - provides resources and controls only.
 *
 * <p>This factory provides:
 * <ul>
 *   <li>Texture resources (red/black checkers)</li>
 *   <li>Required ECS modules</li>
 *   <li>Game master name</li>
 *   <li>Control system for user input</li>
 * </ul>
 *
 * <p>Sprite management and game state are handled by the orchestrator
 * reacting to server snapshots, not by this factory.
 */
@Slf4j
public class CheckersGameFactory implements GameFactory {

    private static final String RED_CHECKER_TEXTURE = "textures/red-checker.png";
    private static final String BLACK_CHECKER_TEXTURE = "textures/black-checker.png";

    private final CheckersControlSystem controlSystem;

    public CheckersGameFactory() {
        this.controlSystem = new CheckersControlSystem();
    }

    @Override
    public void attachScene(GameScene scene) {
        scene.attachControlSystem(controlSystem);
        // Sprites are created by the orchestrator based on server snapshots
    }

    @Override
    public List<GameResource> getResources() {
        List<GameResource> resources = new ArrayList<>();

        byte[] redTexture = loadResource(RED_CHECKER_TEXTURE);
        if (redTexture != null) {
            resources.add(new GameResource("red-checker", "TEXTURE", redTexture, RED_CHECKER_TEXTURE));
        }

        byte[] blackTexture = loadResource(BLACK_CHECKER_TEXTURE);
        if (blackTexture != null) {
            resources.add(new GameResource("black-checker", "TEXTURE", blackTexture, BLACK_CHECKER_TEXTURE));
        }

        return resources;
    }

    @Override
    public List<String> getRequiredModules() {
        return List.of("CheckersModule", "RenderModule");
    }

    @Override
    public String getGameMasterName() {
        return CheckersGameMasterFactory.NAME;
    }

    @Override
    public byte[] getGameMasterJar() {
        // Load the pre-packaged GameMaster JAR from resources
        // This JAR is created by the maven-jar-plugin with classifier 'gamemaster'
        return loadResource("checkers-gamemaster.jar");
    }

    @Override
    public java.util.Map<String, byte[]> getModuleJars() {
        java.util.Map<String, byte[]> modules = new java.util.HashMap<>();

        // Load the pre-packaged CheckersModule JAR from resources
        byte[] checkersModuleJar = loadResource("checkers-engine-module.jar");
        if (checkersModuleJar != null) {
            modules.put("CheckersModule", checkersModuleJar);
        }

        return modules;
    }

    /**
     * Get the control system for attaching callbacks.
     */
    public CheckersControlSystem getControlSystem() {
        return controlSystem;
    }

    private byte[] loadResource(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is != null) {
                return is.readAllBytes();
            }
        } catch (IOException e) {
            log.warn("Failed to load resource: {}", path, e);
        }
        return null;
    }

    /**
     * Control system for handling checkers input.
     *
     * <p>Captures piece selection and move target clicks, then invokes
     * registered callbacks for the orchestrator to process. Passes raw
     * screen coordinates - coordinate conversion is handled by the
     * infrastructure layer (CheckersInputHandler).
     */
    @Slf4j
    public static class CheckersControlSystem implements ControlSystem {
        @Getter
        private Sprite selectedSprite = null;

        // Callback when user requests a move with raw screen coordinates
        private Consumer<MoveRequest> onMoveRequested;

        public CheckersControlSystem() {}

        /**
         * Set callback for move requests.
         */
        public void setOnMoveRequested(Consumer<MoveRequest> callback) {
            this.onMoveRequested = callback;
        }

        @Override
        public void onSpriteClicked(Sprite sprite, int button) {
            if (button != MouseButton.LEFT) {
                return;
            }
            selectedSprite = sprite;
            log.info("Selected piece {}", sprite.getEntityId());
        }

        @Override
        public void onMouseClicked(float x, float y, int button) {
            if (button != MouseButton.LEFT || selectedSprite == null) {
                return;
            }

            // Pass raw screen coordinates to callback
            if (onMoveRequested != null) {
                log.info("Requesting move for piece {} to screen ({},{})",
                        selectedSprite.getEntityId(), x, y);
                onMoveRequested.accept(new MoveRequest(
                        selectedSprite.getEntityId(), x, y));
            }

            selectedSprite = null;
        }

        @Override
        public void onKeyPressed(int key) {
            if (key == KeyCodes.ESCAPE) {
                selectedSprite = null;
                log.debug("Selection cleared");
            }
        }

        public void clearSelection() {
            selectedSprite = null;
        }
    }

    /**
     * Move request from user input with raw screen coordinates.
     * Coordinate conversion is handled by the infrastructure layer.
     */
    public record MoveRequest(long entityId, float screenX, float screenY) {}
}
