package com.lightningfirefly.game.domain;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for domain objects that are automatically synchronized with ECS snapshots.
 *
 * <p>Subclasses should:
 * <ol>
 *   <li>Annotate exactly one {@code long} field with {@link EcsEntityId}</li>
 *   <li>Annotate fields to sync with {@link EcsComponent}</li>
 *   <li>Call {@code super(entityId)} in the constructor</li>
 *   <li>Call {@link #dispose()} when the object is no longer needed</li>
 * </ol>
 *
 * <p>Example:
 * <pre>{@code
 * public class Player extends DomainObject {
 *     @EntityId
 *     long entityId;
 *
 *     @WatchedProperty(ecsPath = "moveable.positionX")
 *     float x;
 *
 *     @WatchedProperty(ecsPath = "moveable.positionY")
 *     float y;
 *
 *     public Player(long entityId) {
 *         super(entityId);
 *         this.entityId = entityId;
 *     }
 * }
 * }</pre>
 *
 * <p>When a snapshot is received, the framework will automatically update
 * the {@code x} and {@code y} fields based on the ECS component values.
 */
public abstract class DomainObject {

    private final long entityId;
    private final List<WatchedField> watchedFields;
    private volatile boolean disposed = false;

    /**
     * Create a new domain object and register it with the registry.
     *
     * @param entityId the entity ID this domain object represents
     */
    protected DomainObject(long entityId) {
        this.entityId = entityId;
        this.watchedFields = scanWatchedFields();

        // Also set the @EntityId field if present
        setEntityIdField(entityId);

        // Auto-register with the registry
        DomainObjectRegistry.getInstance().register(this);
    }

    /**
     * Get the entity ID.
     *
     * @return the entity ID
     */
    public long getEntityId() {
        return entityId;
    }

    /**
     * Get the watched fields for this domain object.
     *
     * @return list of watched field metadata
     */
    public List<WatchedField> getWatchedFields() {
        return watchedFields;
    }

    /**
     * Check if this domain object has been disposed.
     *
     * @return true if disposed
     */
    public boolean isDisposed() {
        return disposed;
    }

    /**
     * Dispose this domain object and unregister from snapshot updates.
     *
     * <p>After calling this method, the object will no longer receive updates.
     */
    public void dispose() {
        if (!disposed) {
            disposed = true;
            DomainObjectRegistry.getInstance().unregister(this);
        }
    }

    /**
     * Update a watched field with a new value.
     *
     * @param watchedField the field metadata
     * @param value the new value
     */
    public void updateField(WatchedField watchedField, float value) {
        if (disposed) {
            return;
        }

        Field field = watchedField.field();
        try {
            field.setAccessible(true);
            Class<?> type = field.getType();

            if (type == float.class || type == Float.class) {
                field.setFloat(this, value);
            } else if (type == double.class || type == Double.class) {
                field.setDouble(this, value);
            } else if (type == int.class || type == Integer.class) {
                field.setInt(this, (int) value);
            } else if (type == long.class || type == Long.class) {
                field.setLong(this, (long) value);
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to update field: " + field.getName(), e);
        }
    }

    /**
     * Called after all watched fields have been updated from a snapshot.
     *
     * <p>Subclasses can override this to perform additional logic after updates.
     */
    protected void onSnapshotUpdated() {
        // Default: no-op
    }

    private List<WatchedField> scanWatchedFields() {
        List<WatchedField> fields = new ArrayList<>();
        Class<?> clazz = getClass();

        while (clazz != null && clazz != DomainObject.class) {
            for (Field field : clazz.getDeclaredFields()) {
                EcsComponent annotation = field.getAnnotation(EcsComponent.class);
                if (annotation != null) {
                    String ecsPath = annotation.componentPath();
                    String[] parts = ecsPath.split("\\.", 2);
                    if (parts.length != 2) {
                        throw new IllegalArgumentException(
                            "Invalid ecsPath '" + ecsPath + "' on field " + field.getName() +
                            ". Expected format: 'moduleName.componentName'"
                        );
                    }
                    fields.add(new WatchedField(field, parts[0], parts[1], annotation.value()));
                }
            }
            clazz = clazz.getSuperclass();
        }

        return fields;
    }

    private void setEntityIdField(long entityId) {
        Class<?> clazz = getClass();

        while (clazz != null && clazz != DomainObject.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(EcsEntityId.class)) {
                    try {
                        field.setAccessible(true);
                        field.setLong(this, entityId);
                        return;
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("Failed to set @EntityId field", e);
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * Metadata for a watched field.
     *
     * @param field the reflected field
     * @param moduleName the ECS module name
     * @param componentName the ECS component name
     * @param alias optional alias for the property
     */
    public record WatchedField(Field field, String moduleName, String componentName, String alias) {
    }
}
