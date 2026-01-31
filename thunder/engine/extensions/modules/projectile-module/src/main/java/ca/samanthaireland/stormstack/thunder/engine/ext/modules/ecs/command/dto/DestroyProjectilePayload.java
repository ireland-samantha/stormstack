package ca.samanthaireland.stormstack.thunder.engine.ext.modules.ecs.command.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload DTO for the destroyProjectile command.
 */
public record DestroyProjectilePayload(
        @JsonProperty("entityId") long entityId
) {
}
