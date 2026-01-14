package ca.samanthaireland.engine.ext.modules;

import ca.samanthaireland.engine.core.store.BaseComponent;
import ca.samanthaireland.engine.core.store.PermissionComponent;
import ca.samanthaireland.engine.core.store.PermissionLevel;
import ca.samanthaireland.engine.ext.module.EngineModule;
import ca.samanthaireland.engine.ext.module.ModuleContext;
import ca.samanthaireland.engine.ext.module.ModuleFactory;
import ca.samanthaireland.engine.util.IdGeneratorV2;

import java.util.List;

/**
 * Module factory for the Entity module.
 *
 * <p>Provides core entity functionality:
 * <ul>
 *   <li>Entity creation (spawn command)</li>
 *   <li>Entity metadata (type, owner, player)</li>
 * </ul>
 *
 * <p>Note: Position components are provided by GridMapModule.
 * Use EntityModuleExports or GridMapExports for position operations.
 */
public class EntityModuleFactory implements ModuleFactory {

    // Entity metadata components
    public static final BaseComponent ENTITY_TYPE = new PermissionComponent(
            IdGeneratorV2.newId(), "ENTITY_TYPE", PermissionLevel.READ);
    public static final BaseComponent OWNER_ID = new PermissionComponent(
            IdGeneratorV2.newId(), "OWNER_ID", PermissionLevel.READ);
    public static final BaseComponent PLAYER_ID = new PermissionComponent(
            IdGeneratorV2.newId(), "PLAYER_ID", PermissionLevel.READ);
    public static final BaseComponent FLAG = new PermissionComponent(
            IdGeneratorV2.newId(), "entity", PermissionLevel.READ);

    /**
     * Core entity components (type, owner, player).
     */
    public static final List<BaseComponent> CORE_COMPONENTS =
            List.of(ENTITY_TYPE, OWNER_ID, PLAYER_ID);

    /**
     * Components included in snapshots.
     * Note: Position components are provided by GridMapModule.
     */
    public static final List<BaseComponent> ALL_COMPONENTS =
            List.of(ENTITY_TYPE, OWNER_ID, PLAYER_ID);

    @Override
    public EngineModule create(ModuleContext context) {
        return new EntityModule(context);
    }
}
