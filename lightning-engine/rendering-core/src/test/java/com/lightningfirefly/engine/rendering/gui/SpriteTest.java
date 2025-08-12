package com.lightningfirefly.engine.rendering.gui;

import com.lightningfirefly.engine.rendering.render2d.Sprite;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the Sprite class.
 */
class SpriteTest {

    @Test
    void sprite_builderCreatesSprite() {
        Sprite sprite = Sprite.builder()
                .id(1)
                .texturePath("textures/test.png")
                .x(100)
                .y(200)
                .sizeX(64)
                .sizeY(64)
                .build();

        assertThat(sprite.getId()).isEqualTo(1);
        assertThat(sprite.getTexturePath()).isEqualTo("textures/test.png");
        assertThat(sprite.getX()).isEqualTo(100);
        assertThat(sprite.getY()).isEqualTo(200);
        assertThat(sprite.getSizeX()).isEqualTo(64);
        assertThat(sprite.getSizeY()).isEqualTo(64);
    }

    @Test
    void sprite_positionCanBeModified() {
        Sprite sprite = Sprite.builder()
                .id(1)
                .texturePath("textures/test.png")
                .x(100)
                .y(100)
                .sizeX(32)
                .sizeY(32)
                .build();

        sprite.setX(200);
        sprite.setY(300);

        assertThat(sprite.getX()).isEqualTo(200);
        assertThat(sprite.getY()).isEqualTo(300);
    }

    @Test
    void sprite_sizeCanBeModified() {
        Sprite sprite = Sprite.builder()
                .id(1)
                .texturePath("textures/test.png")
                .x(0)
                .y(0)
                .sizeX(32)
                .sizeY(32)
                .build();

        sprite.setSizeX(128);
        sprite.setSizeY(128);

        assertThat(sprite.getSizeX()).isEqualTo(128);
        assertThat(sprite.getSizeY()).isEqualTo(128);
    }

    @Test
    void sprite_texturePathCanBeModified() {
        Sprite sprite = Sprite.builder()
                .id(1)
                .texturePath("textures/old.png")
                .x(0)
                .y(0)
                .sizeX(32)
                .sizeY(32)
                .build();

        sprite.setTexturePath("textures/new.png");

        assertThat(sprite.getTexturePath()).isEqualTo("textures/new.png");
    }
}
