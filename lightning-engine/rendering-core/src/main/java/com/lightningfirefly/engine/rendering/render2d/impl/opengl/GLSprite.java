package com.lightningfirefly.engine.rendering.render2d.impl.opengl;

import lombok.Getter;
import org.joml.Vector2f;

@Getter
public class GLSprite {
    public int id;
    public Vector2f position = new Vector2f();
    public Vector2f size = new Vector2f(1, 1);
    public float rotation = 0f;
    public GLTexture texture;

    public GLSprite(GLTexture texture) {
        this.texture = texture;
    }
}

