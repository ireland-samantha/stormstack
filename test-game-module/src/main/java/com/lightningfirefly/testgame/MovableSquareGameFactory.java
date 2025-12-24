package com.lightningfirefly.testgame;

import com.lightningfirefly.game.domain.ControlSystem;
import com.lightningfirefly.game.backend.installation.GameFactory;
import com.lightningfirefly.game.domain.GameScene;
import com.lightningfirefly.game.domain.Sprite;

import java.util.List;

/**
 * A simple test game factory with a single movable square sprite.
 *
 * <p>Controls:
 * <ul>
 *   <li>W or UP arrow: Move up</li>
 *   <li>A or LEFT arrow: Move left</li>
 *   <li>S or DOWN arrow: Move down</li>
 *   <li>D or RIGHT arrow: Move right</li>
 *   <li>Left click: Move sprite to clicked position</li>
 * </ul>
 */
public class MovableSquareGameFactory implements GameFactory {

    private static final float MOVE_SPEED = 5.0f;
    private static final float SPRITE_SIZE = 64.0f;
    private static final float INITIAL_X = 400.0f;
    private static final float INITIAL_Y = 300.0f;

    private final Sprite playerSprite;
    private final MovableSquareControlSystem controlSystem;

    public MovableSquareGameFactory() {
        this.playerSprite = new Sprite(1);
        this.playerSprite.setPosition(INITIAL_X, INITIAL_Y);
        this.playerSprite.setSize(SPRITE_SIZE, SPRITE_SIZE);
        this.playerSprite.setTexturePath("textures/red-checker.png");
        this.controlSystem = new MovableSquareControlSystem(playerSprite, MOVE_SPEED);
    }

    @Override
    public void attachScene(GameScene scene) {
        scene.attachSprite(List.of(playerSprite));
        scene.attachControlSystem(controlSystem);
    }

    @Override
    public List<String> getRequiredModules() {
        // We don't need server-side modules for this simple client-side demo
        return List.of();
    }

    @Override
    public String getGameMasterName() {
        return null; // No game master needed
    }

    /**
     * Get the player sprite for testing.
     */
    public Sprite getPlayerSprite() {
        return playerSprite;
    }

    /**
     * Get the control system for testing.
     */
    public ControlSystem getControlSystem() {
        return controlSystem;
    }

    /**
     * Control system that handles keyboard and mouse input to move the sprite.
     */
    public static class MovableSquareControlSystem implements ControlSystem {
        private final Sprite sprite;
        private final float moveSpeed;

        public MovableSquareControlSystem(Sprite sprite, float moveSpeed) {
            this.sprite = sprite;
            this.moveSpeed = moveSpeed;
        }

        @Override
        public void onUpdate(KeyStates keyStates) {
            // Handle continuous movement while keys are held
            if (keyStates.isPressed(KeyCodes.W) || keyStates.isPressed(KeyCodes.UP)) {
                sprite.setY(sprite.getY() - moveSpeed);
            }
            if (keyStates.isPressed(KeyCodes.S) || keyStates.isPressed(KeyCodes.DOWN)) {
                sprite.setY(sprite.getY() + moveSpeed);
            }
            if (keyStates.isPressed(KeyCodes.A) || keyStates.isPressed(KeyCodes.LEFT)) {
                sprite.setX(sprite.getX() - moveSpeed);
            }
            if (keyStates.isPressed(KeyCodes.D) || keyStates.isPressed(KeyCodes.RIGHT)) {
                sprite.setX(sprite.getX() + moveSpeed);
            }

            // Clamp to window bounds (assuming 800x600 window)
            sprite.setX(Math.max(0, Math.min(800 - sprite.getWidth(), sprite.getX())));
            sprite.setY(Math.max(0, Math.min(600 - sprite.getHeight(), sprite.getY())));
        }

        @Override
        public void onMouseClicked(float x, float y, int button) {
            if (button == MouseButton.LEFT) {
                // Move sprite to clicked position (center the sprite on click)
                float newX = x - sprite.getWidth() / 2;
                float newY = y - sprite.getHeight() / 2;
                sprite.setPosition(newX, newY);
            }
        }

        @Override
        public void onSpriteClicked(Sprite clickedSprite, int button) {
            if (button == MouseButton.RIGHT && clickedSprite == sprite) {
                // Right-click on sprite: reset to center
                sprite.setPosition(INITIAL_X, INITIAL_Y);
            }
        }

        private static final float INITIAL_X = 400.0f;
        private static final float INITIAL_Y = 300.0f;
    }
}
