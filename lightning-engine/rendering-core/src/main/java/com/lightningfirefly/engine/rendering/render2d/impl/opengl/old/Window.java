package com.lightningfirefly.engine.rendering.render2d.impl.opengl.old;

import com.lightningfirefly.engine.rendering.render2d.Sprite;
import com.lightningfirefly.engine.rendering.render2d.KeyInputHandler;
import com.lightningfirefly.engine.rendering.render2d.MouseInputHandler;

@Deprecated
public interface Window {
    void render();
    void addSprite(Sprite sprite);
    void addControls(KeyInputHandler handler);
    void addControls(MouseInputHandler handler);
}
