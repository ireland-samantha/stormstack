package com.lightningfirefly.engine.ext.modules;

import com.lightningfirefly.engine.core.command.CommandBuilder;
import com.lightningfirefly.engine.core.command.EngineCommand;
import com.lightningfirefly.engine.core.store.BaseComponent;
import com.lightningfirefly.engine.core.store.EntityComponentStore;
import com.lightningfirefly.engine.core.system.EngineSystem;
import com.lightningfirefly.engine.ext.module.EngineModule;
import com.lightningfirefly.engine.ext.module.ModuleContext;
import com.lightningfirefly.engine.ext.module.ModuleFactory;
import com.lightningfirefly.engine.util.IdGeneratorV2;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Module factory for the Movement module.
 *
 * @deprecated use RigidBodyModuleFactory
 *
 * <p>Provides movement-related components and systems for entity position
 * and velocity management.
 */
@Slf4j
@Deprecated
public class MoveModuleFactory implements ModuleFactory {

    // Component definitions
    public static final BaseComponent VELOCITY_X = new MovementComponent(
            IdGeneratorV2.newId(), "VELOCITY_X");
    public static final BaseComponent VELOCITY_Y = new MovementComponent(
            IdGeneratorV2.newId(), "VELOCITY_Y");
    public static final BaseComponent VELOCITY_Z = new MovementComponent(
            IdGeneratorV2.newId(), "VELOCITY_Z");

    public static final BaseComponent POSITION_X = new MovementComponent(
            IdGeneratorV2.newId(), "POSITION_X");
    public static final BaseComponent POSITION_Y = new MovementComponent(
            IdGeneratorV2.newId(), "POSITION_Y");
    public static final BaseComponent POSITION_Z= new MovementComponent(
            IdGeneratorV2.newId(), "POSITION_Z");

    public static final BaseComponent MODULE = new MovementComponent(
            IdGeneratorV2.newId(), "move");

    public static final List<BaseComponent> ALL_COMPONENTS =
            List.of(VELOCITY_X, VELOCITY_Y, VELOCITY_Z, POSITION_X, POSITION_Y, POSITION_Z, MODULE);

    public static final List<BaseComponent> MOVE_COMPONENTS =
            List.of(POSITION_X, POSITION_Y, POSITION_Z, VELOCITY_X, VELOCITY_Y, VELOCITY_Z);

    @Override
    public EngineModule create(ModuleContext context) {
        return new MovementModule(context);
    }

    public static class MovementComponent extends BaseComponent {
        public MovementComponent(long id, String name) {
            super(id, name);
        }
    }

    public static class MovementModule implements EngineModule {
        private final ModuleContext context;
        private final List<Long> deleteMoveableQueue = new ArrayList<>();

        public MovementModule(ModuleContext context) {
            this.context = context;
        }

        @Override
        public List<EngineSystem> createSystems() {
            return List.of(createMoveSystem(), createMoveCleanupSystem());
        }

        @Override
        public List<EngineCommand> createCommands() {
            return List.of(attachMovementCommand(), deleteMoveCommand());
        }

        @Override
        public List<BaseComponent> createComponents() {
            return ALL_COMPONENTS;
        }

        @Override
        public BaseComponent createFlagComponent() {
            return MODULE;
        }

        @Override
        public String getName() {
            return "MoveModule";
        }

        /**
         * Command to attach movement components to an existing entity.
         *
         * <p>Attaches position and velocity components to the specified entity,
         * enabling it to participate in the movement system.
         *
         * <p>Payload parameters:
         * <ul>
         *   <li>entityId (long) - The entity to attach movement to</li>
         *   <li>positionX, positionY, positionZ (long) - Initial position</li>
         *   <li>velocityX, velocityY, velocityZ (long) - Initial velocity</li>
         * </ul>
         */
        private EngineCommand attachMovementCommand() {
            return CommandBuilder
                .newCommand()
                .withExecution(payload -> {
                    Map<String, Object> data = payload.getPayload();

                    long entityId = extractLong(data, "entityId");
                    if (entityId == 0) {
                        log.warn("attachMovement command missing required entityId");
                        return;
                    }

                    long positionX = extractLong(data, "positionX");
                    long positionY = extractLong(data, "positionY");
                    long positionZ = extractLong(data, "positionZ");
                    long velocityX = extractLong(data, "velocityX");
                    long velocityY = extractLong(data, "velocityY");
                    long velocityZ = extractLong(data, "velocityZ");

                    EntityComponentStore store = context.getEntityComponentStore();

                    // Attach all movement components to the existing entity
                    store.attachComponents(entityId, MOVE_COMPONENTS,
                            new float[]{positionX, positionY, positionZ, velocityX, velocityY, velocityZ});
                    store.attachComponent(entityId, MODULE, 1.0f);

                    log.info("Attached movement to entity {}: pos=({},{},{}), vel=({},{},{})",
                            entityId, positionX, positionY, positionZ, velocityX, velocityY, velocityZ);
                })
                .withName("attachMovement")
                .withSchema(Map.of(
                        "entityId", Long.class,
                        "positionX", Long.class,
                        "positionY", Long.class,
                        "positionZ", Long.class,
                        "velocityX", Long.class,
                        "velocityY", Long.class,
                        "velocityZ", Long.class))
                .build();
        }

        private long extractLong(Map<String, Object> data, String key) {
            Object value = data.get(key);
            if (value == null) {
                return 0;
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        private EngineSystem createMoveSystem() {
            return () -> {
                log.debug("Running move system");

                EntityComponentStore entityComponentStore = context.getEntityComponentStore();

                // Get all entities that have position and velocity components
                Set<Long> entities = entityComponentStore.getEntitiesWithComponents(ALL_COMPONENTS);

                for (long entity : entities) {
                    // Get current velocity
                    float velocityX = entityComponentStore.getComponent(entity, VELOCITY_X);
                    float velocityY = entityComponentStore.getComponent(entity, VELOCITY_Y);
                    float velocityZ = entityComponentStore.getComponent(entity, VELOCITY_Z);

                    // Get current position
                    float positionX = entityComponentStore.getComponent(entity, POSITION_X);
                    float positionY = entityComponentStore.getComponent(entity, POSITION_Y);
                    float positionZ = entityComponentStore.getComponent(entity, POSITION_Z);

                    // Calculate new position: newPos = oldPos + velocity
                    float newPositionX = positionX + velocityX;
                    float newPositionY = positionY + velocityY;
                    float newPositionZ = positionZ + velocityZ;

                    log.debug("Move entity {} from {},{},{}->{},{},{}", entity, positionX, positionY, positionZ, newPositionX, newPositionY, newPositionZ);

                    // Update position components
                    entityComponentStore.attachComponents(
                            entity,
                            List.of(POSITION_X, POSITION_Y, POSITION_Z),
                            new float[]{newPositionX, newPositionY, newPositionZ}
                    );
                }
            };
        }

        private EngineSystem createMoveCleanupSystem() {
            return () -> {
                EntityComponentStore store = context.getEntityComponentStore();
                for (Long toDeleteEntity : deleteMoveableQueue) {
                    for (BaseComponent c : MOVE_COMPONENTS) {
                        log.trace("Delete {} from {}", c, toDeleteEntity);
                        store.removeComponent(toDeleteEntity, c);
                    }
                }
            };
        }

        private EngineCommand deleteMoveCommand() {
            return CommandBuilder.newCommand()
                    .withExecution(payload -> {
                        long entityId = Long.parseLong(String.valueOf(payload.getPayload().get("id")));
                        log.trace("Request delete moveable: {}", entityId);
                        deleteMoveableQueue.add(entityId);
                    })
                    .withName("DeleteMoveable")
                    .withSchema(Map.of("id", Long.class))
                    .build();
        }
    }
}
