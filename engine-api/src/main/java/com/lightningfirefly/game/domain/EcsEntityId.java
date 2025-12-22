package com.lightningfirefly.game.domain;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as the entity ID for a domain object.
 *
 * <p>The annotated field must be of type {@code long}. This field is used
 * to identify which entity in the ECS snapshot this domain object represents.
 *
 * <p>Example:
 * <pre>{@code
 * public class Player extends DomainObject {
 *     @EntityId
 *     long entityId;
 *
 *     @WatchedProperty(ecsPath = "moveable.positionX")
 *     float x;
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EcsEntityId {
}
