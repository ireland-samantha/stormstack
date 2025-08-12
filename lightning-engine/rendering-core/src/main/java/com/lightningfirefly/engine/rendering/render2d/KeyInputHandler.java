package com.lightningfirefly.engine.rendering.render2d;

public interface KeyInputHandler extends Controls {
    void onArrowKeyPress(KeyType keyType);

    enum KeyType {
        UP, LEFT, RIGHT, DOWN
    }
}
