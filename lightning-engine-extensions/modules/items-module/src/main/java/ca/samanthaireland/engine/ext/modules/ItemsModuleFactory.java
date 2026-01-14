package ca.samanthaireland.engine.ext.modules;

import ca.samanthaireland.engine.core.store.BaseComponent;
import ca.samanthaireland.engine.core.store.PermissionComponent;
import ca.samanthaireland.engine.core.store.PermissionLevel;
import ca.samanthaireland.engine.ext.module.EngineModule;
import ca.samanthaireland.engine.ext.module.ModuleContext;
import ca.samanthaireland.engine.ext.module.ModuleFactory;
import ca.samanthaireland.engine.util.IdGeneratorV2;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Module factory for the Items module.
 *
 * <p>Provides item type definitions and inventory management:
 * <ul>
 *   <li>createItemType - Define a new item type with properties</li>
 *   <li>spawnItem - Create an item entity in the world</li>
 *   <li>pickupItem - Add item to entity's inventory</li>
 *   <li>dropItem - Remove item from inventory and place in world</li>
 *   <li>useItem - Trigger item use effect</li>
 * </ul>
 *
 * <p>Item properties:
 * <ul>
 *   <li>ITEM_TYPE_ID - Reference to the item type definition</li>
 *   <li>STACK_SIZE - Current stack count</li>
 *   <li>MAX_STACK - Maximum stack size for this type</li>
 *   <li>OWNER_ENTITY_ID - Entity carrying this item (0 if on ground)</li>
 *   <li>SLOT_INDEX - Inventory slot position</li>
 * </ul>
 *
 * <p>Item types are stored in a registry (not in ECS) and referenced by ID.
 */
@Slf4j
public class ItemsModuleFactory implements ModuleFactory {

    // Item instance components
    public static final BaseComponent ITEM_TYPE_ID = new ItemComponent(
            IdGeneratorV2.newId(), "ITEM_TYPE_ID");
    public static final BaseComponent STACK_SIZE = new ItemComponent(
            IdGeneratorV2.newId(), "STACK_SIZE");
    public static final BaseComponent MAX_STACK = new ItemComponent(
            IdGeneratorV2.newId(), "MAX_STACK");
    public static final BaseComponent OWNER_ENTITY_ID = new ItemComponent(
            IdGeneratorV2.newId(), "OWNER_ENTITY_ID");
    public static final BaseComponent SLOT_INDEX = new ItemComponent(
            IdGeneratorV2.newId(), "SLOT_INDEX");

    // Item type properties (stored per item type in registry)
    public static final BaseComponent ITEM_NAME_HASH = new ItemComponent(
            IdGeneratorV2.newId(), "ITEM_NAME_HASH");
    public static final BaseComponent ITEM_RARITY = new ItemComponent(
            IdGeneratorV2.newId(), "ITEM_RARITY");
    public static final BaseComponent ITEM_VALUE = new ItemComponent(
            IdGeneratorV2.newId(), "ITEM_VALUE");
    public static final BaseComponent ITEM_WEIGHT = new ItemComponent(
            IdGeneratorV2.newId(), "ITEM_WEIGHT");

    // Item effects (for consumables/equipment)
    public static final BaseComponent HEAL_AMOUNT = new ItemComponent(
            IdGeneratorV2.newId(), "HEAL_AMOUNT");
    public static final BaseComponent DAMAGE_BONUS = new ItemComponent(
            IdGeneratorV2.newId(), "DAMAGE_BONUS");
    public static final BaseComponent ARMOR_VALUE = new ItemComponent(
            IdGeneratorV2.newId(), "ARMOR_VALUE");

    // Module flag (PRIVATE - only EntityModule with superuser can attach during spawn)
    public static final BaseComponent FLAG = new PermissionComponent(
            IdGeneratorV2.newId(), "item", PermissionLevel.PRIVATE);

    /**
     * Core components for item instances.
     */
    public static final List<BaseComponent> CORE_COMPONENTS = List.of(
            ITEM_TYPE_ID, STACK_SIZE, MAX_STACK, OWNER_ENTITY_ID, SLOT_INDEX
    );

    /**
     * Extended components including item properties.
     */
    public static final List<BaseComponent> ALL_COMPONENTS = List.of(
            ITEM_TYPE_ID, STACK_SIZE, MAX_STACK, OWNER_ENTITY_ID, SLOT_INDEX,
            ITEM_NAME_HASH, ITEM_RARITY, ITEM_VALUE, ITEM_WEIGHT,
            HEAL_AMOUNT, DAMAGE_BONUS, ARMOR_VALUE
    );

    @Override
    public EngineModule create(ModuleContext context) {
        return new ItemsModule(context);
    }
}
