package com.lightningfirefly.engine.ext.modules;

import com.lightningfirefly.engine.core.command.CommandPayload;
import com.lightningfirefly.engine.core.command.EngineCommand;
import com.lightningfirefly.engine.core.store.BaseComponent;
import com.lightningfirefly.engine.core.store.EntityComponentStore;
import com.lightningfirefly.engine.core.system.EngineSystem;
import com.lightningfirefly.engine.ext.module.EngineModule;
import com.lightningfirefly.engine.ext.module.ModuleContext;
import com.lightningfirefly.engine.ext.module.ModuleFactory;
import com.lightningfirefly.engine.util.IdGeneratorV2;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Module factory for rendering-related functionality.
 *
 * <p>Provides the ability to attach resource IDs to entities for rendering.
 * The RESOURCE_ID component links an entity to a binary resource (texture, sprite, etc.)
 * stored in the ResourceManager.
 */
@Slf4j
public class RenderingModuleFactory implements ModuleFactory {

    @Override
    public EngineModule create(ModuleContext context) {
        return new RenderModule(context);
    }

    public static class RenderModule implements EngineModule {
        private final ModuleContext context;
        private final AttachSpriteEngineCommand attachSpriteCommand;

        public static final RenderableComponent RESOURCE_ID =
                new RenderableComponent(IdGeneratorV2.newId(), "RESOURCE_ID");

        public RenderModule(ModuleContext context) {
            this.context = context;
            this.attachSpriteCommand = new AttachSpriteEngineCommand(context);
        }

        @Override
        public List<EngineSystem> createSystems() {
            return List.of();
        }

        @Override
        public List<EngineCommand> createCommands() {
            return List.of(attachSpriteCommand);
        }

        @Override
        public List<BaseComponent> createComponents() {
            return List.of(RESOURCE_ID);
        }

        @Override
        public BaseComponent createFlagComponent() {
            return RESOURCE_ID;
        }

        @Override
        public String getName() {
            return "RenderModule";
        }
    }

    /**
     * Command to attach a sprite (resource ID) to an entity.
     *
     * <p>When executed, this command attaches the RESOURCE_ID component to the specified entity,
     * linking it to a binary resource (sprite/texture) stored in the ResourceManager.
     *
     * <p>Payload parameters:
     * <ul>
     *   <li>entityId (long) - The entity to attach the sprite to</li>
     *   <li>resourceId (long) - The ID of the sprite resource to attach</li>
     * </ul>
     */
    public static class AttachSpriteEngineCommand implements EngineCommand {
        private final ModuleContext context;

        public AttachSpriteEngineCommand(ModuleContext context) {
            this.context = context;
        }

        @Override
        public String getName() {
            return "attachSprite";
        }

        @Override
        public Map<String, Class<?>> schema() {
            return Map.of(
                    "entityId", Long.class,
                    "resourceId", Long.class
            );
        }

        @Override
        public void executeCommand(CommandPayload payload) {
            Map<String, Object> data = payload.getPayload();

            long entityId = extractLong(data, "entityId");
            long resourceId = extractLong(data, "resourceId");

            if (entityId == 0 || resourceId == 0) {
                log.warn("attachSprite command missing required parameters: entityId={}, resourceId={}",
                        entityId, resourceId);
                return;
            }

            EntityComponentStore store = context.getEntityComponentStore();
            store.attachComponent(entityId, RenderModule.RESOURCE_ID, (float) resourceId);

            log.info("Attached sprite {} to entity {}", resourceId, entityId);
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
    }

    /**
     * Base component for rendering-related data.
     */
    public static class RenderableComponent extends BaseComponent {
        public RenderableComponent(long id, String name) {
            super(id, name);
        }
    }
}
