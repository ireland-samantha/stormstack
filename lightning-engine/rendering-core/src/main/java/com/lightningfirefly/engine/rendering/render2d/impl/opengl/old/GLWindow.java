package com.lightningfirefly.engine.rendering.render2d.impl.opengl.old;

import com.lightningfirefly.engine.rendering.render2d.KeyInputHandler;
import com.lightningfirefly.engine.rendering.render2d.Sprite;
import com.lightningfirefly.engine.rendering.render2d.MouseInputHandler;
import com.lightningfirefly.engine.rendering.render2d.impl.opengl.GLKeyListener;
import com.lightningfirefly.engine.rendering.render2d.impl.opengl.GLSprite;
import com.lightningfirefly.engine.rendering.render2d.impl.opengl.GLSpriteRenderer;
import com.lightningfirefly.engine.rendering.render2d.impl.opengl.GLTexture;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11C.*;
@Deprecated
public class GLWindow implements Window {
    private final List<Sprite> sprites = new ArrayList<>();
    private final Map<Integer, Sprite> spriteMap = new HashMap<>();

    private KeyInputHandler keyListener;

    @Override
    public void addSprite(Sprite sprite) {
        sprites.add(sprite);
    }

    @Override
    public void addControls(KeyInputHandler handler) {
        keyListener = handler;
    }

    @Override
    public void addControls(MouseInputHandler handler) {
        // TODO impement
    }

    @Override
    public void render() {
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

        long window = glfwCreateWindow(800, 600, "Lightning Engine", 0, 0);
        if (window == 0) throw new RuntimeException("Failed to create window");

        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);


        glfwSwapInterval(1);

        Matrix4f projection = new Matrix4f().ortho2D(0, 800, 600, 0);
        GLSpriteRenderer renderer = new GLSpriteRenderer(projection);

        List<GLSprite> glSprites = new ArrayList<>();
        List<GLTexture> textures = new ArrayList<>();

        for (Sprite sprite : sprites) {
            GLTexture playerTex = new GLTexture(sprite.getTexturePath());
            GLSprite player = new GLSprite(playerTex);
            player.id = sprite.getId();
            player.position.set(sprite.getX(), sprite.getY());
            player.size.set(sprite.getSizeX(), sprite.getSizeY());
            glSprites.add(player);
            textures.add(playerTex);

            spriteMap.put(sprite.getId(), sprite);
        }

        attachEventHandlers(window);

        while (!glfwWindowShouldClose(window)) {
            glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT);

            for (GLSprite sprite : glSprites) {
                Sprite newSprite = spriteMap.get(sprite.id);
                sprite.position.x = newSprite.getX();
                sprite.position.y = newSprite.getY();
                renderer.draw(sprite);
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
        }

        // Clean up
        textures.forEach(GLTexture::dispose);
        glfwTerminate();
    }

    private void attachEventHandlers(long window) {
        if (keyListener != null) {
            GLKeyListener glHandler = new GLKeyListener(window, keyListener);
            glHandler.attach();
        }
    }
}
