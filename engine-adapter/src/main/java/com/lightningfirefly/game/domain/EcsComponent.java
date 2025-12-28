package com.lightningfirefly.game.domain;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field to be automatically updated from ECS components data.
 *
 * <p>When a components is received, the framework will:
 * <ol>
 *   <li>Find the entity using the {@link EcsEntityId} field</li>
 *   <li>Look up the component value using the {@code ecsPath}</li>
 *   <li>Update this field with the new value</li>
 * </ol>
 *
 * <p>The {@code ecsPath} format is {@code "moduleName.componentName"}, for example:
 * <ul>
 *   <li>{@code "moveable.positionX"} - position X from moveable module</li>
 *   <li>{@code "SpawnModule.ENTITY_TYPE"} - entity type from spawn module</li>
 * </ul>
 *
 * <p>Supported field types:
 * <ul>
 *   <li>{@code float} / {@code Float}</li>
 *   <li>{@code int} / {@code Integer} (truncated from float)</li>
 *   <li>{@code long} / {@code Long} (truncated from float)</li>
 *   <li>{@code double} / {@code Double}</li>
 * </ul>
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
 *     @WatchedProperty(ecsPath = "moveable.velocityX")
 *     float velocityX;
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EcsComponent {

    /**
     * The ECS path to the component value.
     *
     * <p>Format: {@code "moduleName.componentName"}
     *
     * @return the ECS path
     */
    String componentPath();

    /**
     * Optional alias for the property name (for debugging/logging).
     *
     * @return the property alias, or empty string if not specified
     */
    String value() default "";
}
