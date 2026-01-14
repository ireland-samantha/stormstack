/*
 * Copyright (c) 2026 Samantha Ireland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */


package ca.samanthaireland.game.domain;

/**
 * Interface for handling game input events.
 * Provides a clean game-level API for keyboard and mouse input.
 *
 * <p>The control system is attached to a game module and receives
 * input events during game execution. Games implement this interface
 * to handle player input.
 *
 * <p>Example usage:
 * <pre>{@code
 * ControlSystem controls = new ControlSystem() {
 *     @Override
 *     public void onKeyPressed(int key) {
 *         if (key == KeyCodes.SPACE) {
 *             player.jump();
 *         }
 *     }
 *
 *     @Override
 *     public void onSpriteClicked(Sprite sprite, int button) {
 *         if (button == MouseButton.LEFT) {
 *             selectEntity(sprite.getEntityId());
 *         }
 *     }
 * };
 * gameModule.attachControlSystem(controls);
 * }</pre>
 */
public interface ControlSystem {

    /**
     * Called when a key is pressed.
     *
     * @param key the key code (use KeyCodes constants)
     */
    default void onKeyPressed(int key) {
    }

    /**
     * Called when a key is released.
     *
     * @param key the key code (use KeyCodes constants)
     */
    default void onKeyReleased(int key) {
    }

    /**
     * Called when a key is held and repeating.
     *
     * @param key the key code
     */
    default void onKeyRepeat(int key) {
    }

    /**
     * Called when the mouse is clicked anywhere in the game window.
     *
     * @param x      mouse x coordinate
     * @param y      mouse y coordinate
     * @param button mouse button (0=left, 1=right, 2=middle)
     */
    default void onMouseClicked(float x, float y, int button) {
    }

    /**
     * Called when a mouse button is released.
     *
     * @param x      mouse x coordinate
     * @param y      mouse y coordinate
     * @param button mouse button
     */
    default void onMouseReleased(float x, float y, int button) {
    }

    /**
     * Called when the mouse moves.
     *
     * @param x mouse x coordinate
     * @param y mouse y coordinate
     */
    default void onMouseMoved(float x, float y) {
    }

    /**
     * Called when the mouse wheel scrolls.
     *
     * @param deltaX horizontal scroll amount
     * @param deltaY vertical scroll amount
     */
    default void onMouseScrolled(float deltaX, float deltaY) {
    }

    /**
     * Called when a sprite is clicked.
     *
     * @param sprite the clicked sprite
     * @param button mouse button (0=left, 1=right, 2=middle)
     */
    default void onSpriteClicked(Sprite sprite, int button) {
    }

    /**
     * Called when a sprite is released (mouse button up).
     *
     * @param sprite the sprite
     * @param button mouse button
     */
    default void onSpriteReleased(Sprite sprite, int button) {
    }

    /**
     * Called when the mouse enters a sprite's bounds.
     *
     * @param sprite the sprite being entered
     */
    default void onSpriteEntered(Sprite sprite) {
    }

    /**
     * Called when the mouse exits a sprite's bounds.
     *
     * @param sprite the sprite being exited
     */
    default void onSpriteExited(Sprite sprite) {
    }

    /**
     * Called every frame with the current state of pressed keys.
     * Useful for continuous movement while a key is held.
     *
     * @param keyStates an object to query current key states
     */
    default void onUpdate(KeyStates keyStates) {
    }

    /**
     * Interface to query current keyboard state.
     */
    interface KeyStates {
        /**
         * Check if a key is currently pressed.
         *
         * @param key the key code
         * @return true if the key is pressed
         */
        boolean isPressed(int key);
    }

    /**
     * Common mouse button constants.
     */
    interface MouseButton {
        int LEFT = 0;
        int RIGHT = 1;
        int MIDDLE = 2;
    }

    /**
     * Common key code constants.
     * These match GLFW key codes.
     */
    interface KeyCodes {
        int SPACE = 32;
        int APOSTROPHE = 39;
        int COMMA = 44;
        int MINUS = 45;
        int PERIOD = 46;
        int SLASH = 47;

        int KEY_0 = 48;
        int KEY_1 = 49;
        int KEY_2 = 50;
        int KEY_3 = 51;
        int KEY_4 = 52;
        int KEY_5 = 53;
        int KEY_6 = 54;
        int KEY_7 = 55;
        int KEY_8 = 56;
        int KEY_9 = 57;

        int A = 65;
        int B = 66;
        int C = 67;
        int D = 68;
        int E = 69;
        int F = 70;
        int G = 71;
        int H = 72;
        int I = 73;
        int J = 74;
        int K = 75;
        int L = 76;
        int M = 77;
        int N = 78;
        int O = 79;
        int P = 80;
        int Q = 81;
        int R = 82;
        int S = 83;
        int T = 84;
        int U = 85;
        int V = 86;
        int W = 87;
        int X = 88;
        int Y = 89;
        int Z = 90;

        int ESCAPE = 256;
        int ENTER = 257;
        int TAB = 258;
        int BACKSPACE = 259;
        int INSERT = 260;
        int DELETE = 261;
        int RIGHT = 262;
        int LEFT = 263;
        int DOWN = 264;
        int UP = 265;
        int PAGE_UP = 266;
        int PAGE_DOWN = 267;
        int HOME = 268;
        int END = 269;

        int F1 = 290;
        int F2 = 291;
        int F3 = 292;
        int F4 = 293;
        int F5 = 294;
        int F6 = 295;
        int F7 = 296;
        int F8 = 297;
        int F9 = 298;
        int F10 = 299;
        int F11 = 300;
        int F12 = 301;

        int LEFT_SHIFT = 340;
        int LEFT_CONTROL = 341;
        int LEFT_ALT = 342;
        int RIGHT_SHIFT = 344;
        int RIGHT_CONTROL = 345;
        int RIGHT_ALT = 346;
    }
}
