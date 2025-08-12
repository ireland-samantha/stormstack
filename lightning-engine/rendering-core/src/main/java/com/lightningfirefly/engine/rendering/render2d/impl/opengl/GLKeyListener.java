package com.lightningfirefly.engine.rendering.render2d.impl.opengl;

import com.lightningfirefly.engine.rendering.render2d.KeyInputHandler;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_UP;

public class GLKeyListener {

    private final long window; // pixels per call

    private final KeyInputHandler handler;

    public GLKeyListener(long window, KeyInputHandler handler) {
        this.window = window;
        this.handler = handler;
        attach();
    }

    public void attach() {
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS || action == GLFW_REPEAT) {
                switch (key) {
                    case GLFW_KEY_UP    -> handler.onArrowKeyPress(KeyInputHandler.KeyType.UP);
                    case GLFW_KEY_DOWN  -> handler.onArrowKeyPress(KeyInputHandler.KeyType.DOWN);
                    case GLFW_KEY_LEFT  -> handler.onArrowKeyPress(KeyInputHandler.KeyType.LEFT);
                    case GLFW_KEY_RIGHT -> handler.onArrowKeyPress(KeyInputHandler.KeyType.RIGHT);
                }
            }
        });
    }
}
