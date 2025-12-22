package com.lightningfirefly.game.engine;

import com.lightningfirefly.game.domain.DomainObject;
import com.lightningfirefly.game.domain.EcsComponent;
import com.lightningfirefly.game.domain.EcsEntityId;

/**
 * A game sprite representing a visual entity in the game world.
 * This is the game-level abstraction that maps to rendering-core sprites.
 */
public class Sprite extends DomainObject {

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

    public long getEntityId() {
        return entityId;
    }

    public String getTexturePath() {
        return texturePath;
    }

    public void setTexturePath(String texturePath) {
        this.texturePath = texturePath;
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public float getRotation() {
        return rotation;
    }

    public void setRotation(float rotation) {
        this.rotation = rotation;
    }

    public int getZIndex() {
        return zIndex;
    }

    public void setZIndex(int zIndex) {
        this.zIndex = zIndex;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
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
