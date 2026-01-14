package ca.samanthaireland.engine.ext.modules.ecs.command;

import ca.samanthaireland.engine.core.command.CommandBuilder;
import ca.samanthaireland.engine.core.command.EngineCommand;
import ca.samanthaireland.engine.core.command.PayloadMapper;
import ca.samanthaireland.engine.core.exception.InvalidParameterException;
import ca.samanthaireland.engine.ext.modules.domain.Vector3;
import ca.samanthaireland.engine.ext.modules.domain.service.PhysicsService;
import ca.samanthaireland.engine.ext.modules.ecs.command.dto.SetVelocityPayload;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Command to set the velocity of a rigid body directly.
 */
@Slf4j
public final class SetVelocityCommand {

    private SetVelocityCommand() {}

    /**
     * Creates a new setVelocity command.
     *
     * @param physicsService the physics service
     * @return the configured EngineCommand
     */
    public static EngineCommand create(PhysicsService physicsService) {
        return CommandBuilder.newCommand()
                .withName("setVelocity")
                .withSchema(Map.of(
                        "entityId", Long.class,
                        "velocityX", Float.class,
                        "velocityY", Float.class,
                        "velocityZ", Float.class
                ))
                .withExecution(payload -> {
                    SetVelocityPayload dto = PayloadMapper.convert(payload, SetVelocityPayload.class);

                    if (!dto.hasEntityId()) {
                        throw new InvalidParameterException("setVelocity: missing entityId");
                    }

                    Vector3 velocity = new Vector3(dto.getVelocityX(), dto.getVelocityY(), dto.getVelocityZ());
                    physicsService.setVelocity(dto.entityId(), velocity);

                    log.debug("Set velocity ({},{},{}) for entity {}",
                            velocity.x(), velocity.y(), velocity.z(), dto.entityId());
                })
                .build();
    }
}
