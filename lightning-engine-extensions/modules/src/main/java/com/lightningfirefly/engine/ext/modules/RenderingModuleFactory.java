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

        // Resource ID - links to texture/sprite binary resource
        public static final RenderableComponent RESOURCE_ID =
                new RenderableComponent(IdGeneratorV2.newId(), "RESOURCE_ID");

        // Sprite display position (separate from physics position in MoveModule)
        public static final RenderableComponent SPRITE_X =
                new RenderableComponent(IdGeneratorV2.newId(), "SPRITE_X");
        public static final RenderableComponent SPRITE_Y =
                new RenderableComponent(IdGeneratorV2.newId(), "SPRITE_Y");

        // Sprite dimensions
        public static final RenderableComponent SPRITE_WIDTH =
                new RenderableComponent(IdGeneratorV2.newId(), "SPRITE_WIDTH");
        public static final RenderableComponent SPRITE_HEIGHT =
                new RenderableComponent(IdGeneratorV2.newId(), "SPRITE_HEIGHT");

        // Sprite rotation in degrees
        public static final RenderableComponent SPRITE_ROTATION =
                new RenderableComponent(IdGeneratorV2.newId(), "SPRITE_ROTATION");

        // Render order (higher values render on top)
        public static final RenderableComponent SPRITE_Z_INDEX =
                new RenderableComponent(IdGeneratorV2.newId(), "SPRITE_Z_INDEX");

        // Visibility flag (0 = hidden, non-zero = visible)
        public static final RenderableComponent SPRITE_VISIBLE =
                new RenderableComponent(IdGeneratorV2.newId(), "SPRITE_VISIBLE");

        // All sprite components for easy iteration
        public static final List<BaseComponent> ALL_SPRITE_COMPONENTS = List.of(
                RESOURCE_ID, SPRITE_X, SPRITE_Y, SPRITE_WIDTH, SPRITE_HEIGHT,
                SPRITE_ROTATION, SPRITE_Z_INDEX, SPRITE_VISIBLE
        );

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
            return ALL_SPRITE_COMPONENTS;
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
     * Command to attach sprite rendering components to an entity.
     *
     * <p>When executed, this command attaches all sprite rendering components to the specified entity,
     * including position, size, rotation, z-index, visibility, and resource ID.
     *
     * <p>Payload parameters:
     * <ul>
     *   <li>entityId (long) - The entity to attach the sprite to (required)</li>
     *   <li>resourceId (long) - The ID of the sprite resource to attach (required)</li>
     *   <li>x (float) - X position for rendering (default: 0)</li>
     *   <li>y (float) - Y position for rendering (default: 0)</li>
     *   <li>width (float) - Sprite width (default: 32)</li>
     *   <li>height (float) - Sprite height (default: 32)</li>
     *   <li>rotation (float) - Rotation in degrees (default: 0)</li>
     *   <li>zIndex (float) - Render order, higher on top (default: 0)</li>
     *   <li>visible (float) - Visibility flag, 0=hidden, 1=visible (default: 1)</li>
     * </ul>
     */
    public static class AttachSpriteEngineCommand implements EngineCommand {
        private final ModuleContext context;

        // Default values
        private static final float DEFAULT_WIDTH = 32.0f;
        private static final float DEFAULT_HEIGHT = 32.0f;
        private static final float DEFAULT_VISIBLE = 1.0f;

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
                    "resourceId", Long.class,
                    "x", Float.class,
                    "y", Float.class,
                    "width", Float.class,
                    "height", Float.class,
                    "rotation", Float.class,
                    "zIndex", Float.class,
                    "visible", Float.class
            );
        }

        @Override
        public void executeCommand(CommandPayload payload) {
            Map<String, Object> data = payload.getPayload();

            long entityId = extractLong(data, "entityId");
            long resourceId = extractLong(data, "resourceId");

            if (entityId == 0) {
                log.warn("attachSprite command missing required entityId");
                return;
            }

            // Extract optional parameters with defaults
            float x = extractFloat(data, "x", 0.0f);
            float y = extractFloat(data, "y", 0.0f);
            float width = extractFloat(data, "width", DEFAULT_WIDTH);
            float height = extractFloat(data, "height", DEFAULT_HEIGHT);
            float rotation = extractFloat(data, "rotation", 0.0f);
            float zIndex = extractFloat(data, "zIndex", 0.0f);
            float visible = extractFloat(data, "visible", DEFAULT_VISIBLE);

            EntityComponentStore store = context.getEntityComponentStore();

            // Attach all sprite components
            store.attachComponent(entityId, RenderModule.RESOURCE_ID, (float) resourceId);
            store.attachComponent(entityId, RenderModule.SPRITE_X, x);
            store.attachComponent(entityId, RenderModule.SPRITE_Y, y);
            store.attachComponent(entityId, RenderModule.SPRITE_WIDTH, width);
            store.attachComponent(entityId, RenderModule.SPRITE_HEIGHT, height);
            store.attachComponent(entityId, RenderModule.SPRITE_ROTATION, rotation);
            store.attachComponent(entityId, RenderModule.SPRITE_Z_INDEX, zIndex);
            store.attachComponent(entityId, RenderModule.SPRITE_VISIBLE, visible);

            log.info("Attached sprite to entity {}: resourceId={}, pos=({},{}), size=({},{}), rotation={}, zIndex={}, visible={}",
                    entityId, resourceId, x, y, width, height, rotation, zIndex, visible);
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

        private float extractFloat(Map<String, Object> data, String key, float defaultValue) {
            Object value = data.get(key);
            if (value == null) {
                return defaultValue;
            }
            if (value instanceof Number number) {
                return number.floatValue();
            }
            try {
                return Float.parseFloat(value.toString());
            } catch (NumberFormatException e) {
                return defaultValue;
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
