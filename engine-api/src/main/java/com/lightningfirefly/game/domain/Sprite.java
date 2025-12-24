package com.lightningfirefly.game.domain;

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
