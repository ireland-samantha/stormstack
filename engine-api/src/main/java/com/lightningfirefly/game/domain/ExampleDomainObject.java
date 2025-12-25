package com.lightningfirefly.game.domain;

/**
 * Example domain object demonstrating ECS-to-Domain mapping.
 *
 * <p>This class shows how to create a domain object that automatically
 * synchronizes its fields with ECS components data.
 *
 * <p>Key features:
 * <ul>
 *   <li>{@link EcsEntityId} marks the field that holds the entity's ID</li>
 *   <li>{@link EcsComponent} marks fields that sync with ECS components</li>
 *   <li>The object auto-registers when created and updates on each components</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * // Create a domain object for entity 42
 * ExampleDomainObject player = new ExampleDomainObject(42);
 *
 * // When snapshots arrive, the SnapshotObserver updates all fields
 * // The player.positionX, player.positionY, etc. are automatically updated
 *
 * // When done, dispose to stop receiving updates
 * player.dispose();
 * }</pre>
 */
public class ExampleDomainObject extends DomainObject {

    @EcsEntityId
    long entityId;

    @EcsComponent(componentPath = "MoveModule.VELOCITY_X")
    float velocityX;

    @EcsComponent(componentPath = "MoveModule.VELOCITY_Y")
    float velocityY;

    @EcsComponent(componentPath = "MoveModule.POSITION_X")
    float positionX;

    @EcsComponent(componentPath = "MoveModule.POSITION_Y")
    float positionY;

    /**
     * Create a new example domain object.
     *
     * @param entityId the entity ID to track
     */
    public ExampleDomainObject(long entityId) {
        super(entityId);
    }

    // Getters for the watched properties

    public float getVelocityX() {
        return velocityX;
    }

    public float getVelocityY() {
        return velocityY;
    }

    public float getPositionX() {
        return positionX;
    }

    public float getPositionY() {
        return positionY;
    }

    @Override
    protected void onSnapshotUpdated() {
        // Called after all fields are updated from a components
        // Subclasses can add custom logic here
    }
}
