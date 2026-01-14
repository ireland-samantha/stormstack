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

import lombok.Getter;
import lombok.Setter;

/**
 * A game sprite representing a visual entity in the game world.
 * This is the game-level abstraction that maps to rendering-core sprites.
 */
@Getter
@Setter
public class Sprite extends DomainObject {

    @Getter
    @EcsEntityId
    private final long entityId;

    @EcsComponent(componentPath = "RenderModule.RESOURCE_ID")
    private long resourceId;

    private String texturePath;

    private float x;
    private float y;

    private float width;
    private float height;

    private float rotation;
    private int zIndex;

    private boolean visible;

    public Sprite(long entityId) {
        super(entityId);
        this.entityId = entityId;
        this.visible = true;
        this.width = 32;
        this.height = 32;
        this.zIndex = 0;
        this.rotation = 0;
    }


    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Check if a point is within this sprite's bounds.
     */
    public boolean contains(float px, float py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    @Override
    public String toString() {
        return "Sprite{entityId=" + entityId + ", x=" + x + ", y=" + y +
                ", width=" + width + ", height=" + height + "}";
    }
}
