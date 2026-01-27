package ca.samanthaireland.engine.ext.modules.ecs.command;

import ca.samanthaireland.engine.core.command.CommandBuilder;
import ca.samanthaireland.engine.core.command.EngineCommand;
import ca.samanthaireland.engine.core.command.PayloadMapper;
import ca.samanthaireland.engine.core.exception.InvalidParameterException;
import ca.samanthaireland.engine.ext.modules.domain.Vector3;
import ca.samanthaireland.engine.ext.modules.domain.service.PhysicsService;
import ca.samanthaireland.engine.ext.modules.ecs.command.dto.ApplyImpulsePayload;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Command to apply an impulse to a rigid body.
 */
@Slf4j
public final class ApplyImpulseCommand {

    private ApplyImpulseCommand() {}

    /**
     * Creates a new applyImpulse command.
     *
     * @param physicsService the physics service
     * @return the configured EngineCommand
     */
    public static EngineCommand create(PhysicsService physicsService) {
        return CommandBuilder.newCommand()
                .withName("applyImpulse")
                .withSchema(Map.of(
                        "entityId", Long.class,
                        "impulseX", Float.class,
                        "impulseY", Float.class,
                        "impulseZ", Float.class
                ))
                .withExecution(payload -> {
                    ApplyImpulsePayload dto = PayloadMapper.convert(payload, ApplyImpulsePayload.class);

                    if (!dto.hasEntityId()) {
                        throw new InvalidParameterException("applyImpulse: missing entityId");
                    }

                    Vector3 impulse = new Vector3(dto.getImpulseX(), dto.getImpulseY(), dto.getImpulseZ());
                    physicsService.applyImpulse(dto.entityId(), impulse);

                    log.debug("Applied impulse ({},{},{}) to entity {}",
                            impulse.x(), impulse.y(), impulse.z(), dto.entityId());
                })
                .build();
    }
}
