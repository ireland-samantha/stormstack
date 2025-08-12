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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

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

    // Module flag
    public static final BaseComponent FLAG = new ItemComponent(
            IdGeneratorV2.newId(), "item");

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

    /**
     * Items module implementation.
     */
    public static class ItemsModule implements EngineModule {
        private final ModuleContext context;

        // Item type registry (keyed by matchId -> typeId -> definition)
        // This ensures each match has its own isolated item types
        private static final Map<Long, Map<Long, ItemTypeDefinition>> itemTypesByMatch = new ConcurrentHashMap<>();
        private static final Map<Long, AtomicLong> nextItemTypeIdByMatch = new ConcurrentHashMap<>();

        public ItemsModule(ModuleContext context) {
            this.context = context;
        }

        private Map<Long, ItemTypeDefinition> getItemTypes(long matchId) {
            return itemTypesByMatch.computeIfAbsent(matchId, k -> new ConcurrentHashMap<>());
        }

        private AtomicLong getNextItemTypeId(long matchId) {
            return nextItemTypeIdByMatch.computeIfAbsent(matchId, k -> new AtomicLong(1));
        }

        @Override
        public List<EngineSystem> createSystems() {
            return List.of(); // Items are event-driven, no tick systems needed
        }

        @Override
        public List<EngineCommand> createCommands() {
            return List.of(
                    createItemTypeCommand(),
                    createSpawnItemCommand(),
                    createPickupItemCommand(),
                    createDropItemCommand(),
                    createUseItemCommand(),
                    createGetItemTypesCommand()
            );
        }

        @Override
        public List<BaseComponent> createComponents() {
            return ALL_COMPONENTS;
        }

        @Override
        public BaseComponent createFlagComponent() {
            return FLAG;
        }

        @Override
        public String getName() {
            return "ItemsModule";
        }

        /**
         * Command to create a new item type.
         *
         * <p>Payload:
         * <ul>
         *   <li>name (String) - Item type name</li>
         *   <li>maxStack (float) - Maximum stack size (default 1)</li>
         *   <li>rarity (float) - Rarity tier (0=common, 1=uncommon, 2=rare, etc.)</li>
         *   <li>value (float) - Base value in currency</li>
         *   <li>weight (float) - Item weight</li>
         *   <li>healAmount (float) - HP restored when used (consumables)</li>
         *   <li>damageBonus (float) - Damage bonus when equipped</li>
         *   <li>armorValue (float) - Armor value when equipped</li>
         * </ul>
         *
         * <p>Returns the item type ID.
         */
        private EngineCommand createItemTypeCommand() {
            Map<String, Class<?>> schema = new HashMap<>();
            schema.put("matchId", Long.class);
            schema.put("name", String.class);
            schema.put("maxStack", Float.class);
            schema.put("rarity", Float.class);
            schema.put("value", Float.class);
            schema.put("weight", Float.class);
            schema.put("healAmount", Float.class);
            schema.put("damageBonus", Float.class);
            schema.put("armorValue", Float.class);

            return CommandBuilder.newCommand()
                    .withName("createItemType")
                    .withSchema(schema)
                    .withExecution(payload -> {
                        Map<String, Object> data = payload.getPayload();
                        long matchId = extractLong(data, "matchId");
                        String name = extractString(data, "name", "Unknown");
                        float maxStack = extractFloat(data, "maxStack", 1);
                        float rarity = extractFloat(data, "rarity", 0);
                        float value = extractFloat(data, "value", 0);
                        float weight = extractFloat(data, "weight", 0);
                        float healAmount = extractFloat(data, "healAmount", 0);
                        float damageBonus = extractFloat(data, "damageBonus", 0);
                        float armorValue = extractFloat(data, "armorValue", 0);

                        long typeId = getNextItemTypeId(matchId).getAndIncrement();
                        ItemTypeDefinition typeDef = new ItemTypeDefinition(
                                typeId, name, (int) maxStack, (int) rarity,
                                value, weight, healAmount, damageBonus, armorValue
                        );
                        getItemTypes(matchId).put(typeId, typeDef);

                        log.info("Created item type '{}' with ID {} for match {}", name, typeId, matchId);
                    })
                    .build();
        }

        /**
         * Command to spawn an item entity in the world.
         *
         * <p>Payload:
         * <ul>
         *   <li>matchId (long) - Target match</li>
         *   <li>itemTypeId (long) - Item type to spawn</li>
         *   <li>positionX/Y (float) - World position</li>
         *   <li>stackSize (float) - Number of items in stack (default 1)</li>
         * </ul>
         */
        private EngineCommand createSpawnItemCommand() {
            return CommandBuilder.newCommand()
                    .withName("spawnItem")
                    .withSchema(Map.of(
                            "matchId", Long.class,
                            "itemTypeId", Long.class,
                            "positionX", Float.class,
                            "positionY", Float.class,
                            "stackSize", Float.class
                    ))
                    .withExecution(payload -> {
                        Map<String, Object> data = payload.getPayload();
                        long matchId = extractLong(data, "matchId");
                        long itemTypeId = extractLong(data, "itemTypeId");
                        float posX = extractFloat(data, "positionX", 0);
                        float posY = extractFloat(data, "positionY", 0);
                        float stackSize = extractFloat(data, "stackSize", 1);

                        Map<Long, ItemTypeDefinition> matchItemTypes = getItemTypes(matchId);
                        log.info("spawnItem: looking up itemTypeId={} for matchId={}, registry has {} types: {}",
                                itemTypeId, matchId, matchItemTypes.size(), matchItemTypes.keySet());
                        ItemTypeDefinition typeDef = matchItemTypes.get(itemTypeId);
                        if (typeDef == null) {
                            log.warn("spawnItem: unknown item type {} for match {} (registry: {})", itemTypeId, matchId, matchItemTypes);
                            // Create entity anyway with default values
                            long entityId = context.getEntityFactory().createEntity(
                                    matchId,
                                    EntityModuleFactory.CORE_COMPONENTS,
                                    new float[]{itemTypeId, 0, 0}
                            );
                            EntityComponentStore store = context.getEntityComponentStore();
                            store.attachComponents(entityId, EntityModuleFactory.POSITION_COMPONENTS,
                                    new float[]{posX, posY, 0});
                            store.attachComponent(entityId, ITEM_TYPE_ID, itemTypeId);
                            store.attachComponent(entityId, STACK_SIZE, stackSize);
                            store.attachComponent(entityId, MAX_STACK, 100);  // Default
                            store.attachComponent(entityId, OWNER_ENTITY_ID, 0);
                            store.attachComponent(entityId, SLOT_INDEX, -1);
                            store.attachComponent(entityId, FLAG, 1.0f);
                            store.attachComponent(entityId, HEAL_AMOUNT, 0);
                            return;
                        }

                        // Create entity
                        long entityId = context.getEntityFactory().createEntity(
                                matchId,
                                EntityModuleFactory.CORE_COMPONENTS,
                                new float[]{itemTypeId, 0, 0}
                        );

                        EntityComponentStore store = context.getEntityComponentStore();

                        // Position
                        store.attachComponents(entityId, EntityModuleFactory.POSITION_COMPONENTS,
                                new float[]{posX, posY, 0});

                        // Item components
                        store.attachComponent(entityId, ITEM_TYPE_ID, itemTypeId);
                        store.attachComponent(entityId, STACK_SIZE, Math.min(stackSize, typeDef.maxStack()));
                        store.attachComponent(entityId, MAX_STACK, typeDef.maxStack());
                        store.attachComponent(entityId, OWNER_ENTITY_ID, 0); // On ground
                        store.attachComponent(entityId, SLOT_INDEX, -1);
                        store.attachComponent(entityId, FLAG, 1.0f);

                        // Copy type properties to instance
                        store.attachComponent(entityId, ITEM_NAME_HASH, typeDef.name().hashCode());
                        store.attachComponent(entityId, ITEM_RARITY, typeDef.rarity());
                        store.attachComponent(entityId, ITEM_VALUE, typeDef.value());
                        store.attachComponent(entityId, ITEM_WEIGHT, typeDef.weight());
                        store.attachComponent(entityId, HEAL_AMOUNT, typeDef.healAmount());
                        store.attachComponent(entityId, DAMAGE_BONUS, typeDef.damageBonus());
                        store.attachComponent(entityId, ARMOR_VALUE, typeDef.armorValue());

                        log.info("Spawned item '{}' (type {}) at ({}, {})",
                                typeDef.name(), itemTypeId, posX, posY);
                    })
                    .build();
        }

        /**
         * Command to pick up an item (add to inventory).
         *
         * <p>Payload:
         * <ul>
         *   <li>itemEntityId (long) - Item entity to pick up</li>
         *   <li>pickerEntityId (long) - Entity picking up the item</li>
         *   <li>slotIndex (float) - Inventory slot to place item</li>
         * </ul>
         */
        private EngineCommand createPickupItemCommand() {
            return CommandBuilder.newCommand()
                    .withName("pickupItem")
                    .withSchema(Map.of(
                            "itemEntityId", Long.class,
                            "pickerEntityId", Long.class,
                            "slotIndex", Float.class
                    ))
                    .withExecution(payload -> {
                        Map<String, Object> data = payload.getPayload();
                        long itemEntityId = extractLong(data, "itemEntityId");
                        long pickerEntityId = extractLong(data, "pickerEntityId");
                        float slotIndex = extractFloat(data, "slotIndex", 0);

                        EntityComponentStore store = context.getEntityComponentStore();

                        // Verify item exists
                        float flag = store.getComponent(itemEntityId, FLAG);
                        if (flag <= 0) {
                            log.warn("pickupItem: entity {} is not an item", itemEntityId);
                            return;
                        }

                        // Verify item is on ground
                        float currentOwner = store.getComponent(itemEntityId, OWNER_ENTITY_ID);
                        if (currentOwner > 0) {
                            log.warn("pickupItem: item {} is already owned by {}", itemEntityId, currentOwner);
                            return;
                        }

                        // Transfer ownership
                        store.attachComponent(itemEntityId, OWNER_ENTITY_ID, pickerEntityId);
                        store.attachComponent(itemEntityId, SLOT_INDEX, slotIndex);

                        // Remove from world position (set to picker's position later if needed)
                        log.info("Entity {} picked up item {}", pickerEntityId, itemEntityId);
                    })
                    .build();
        }

        /**
         * Command to drop an item from inventory.
         *
         * <p>Payload:
         * <ul>
         *   <li>itemEntityId (long) - Item entity to drop</li>
         *   <li>positionX/Y (float) - World position to drop at</li>
         * </ul>
         */
        private EngineCommand createDropItemCommand() {
            return CommandBuilder.newCommand()
                    .withName("dropItem")
                    .withSchema(Map.of(
                            "itemEntityId", Long.class,
                            "positionX", Float.class,
                            "positionY", Float.class
                    ))
                    .withExecution(payload -> {
                        Map<String, Object> data = payload.getPayload();
                        long itemEntityId = extractLong(data, "itemEntityId");
                        float posX = extractFloat(data, "positionX", 0);
                        float posY = extractFloat(data, "positionY", 0);

                        EntityComponentStore store = context.getEntityComponentStore();

                        // Verify item exists
                        float flag = store.getComponent(itemEntityId, FLAG);
                        if (flag <= 0) {
                            log.warn("dropItem: entity {} is not an item", itemEntityId);
                            return;
                        }

                        // Remove ownership
                        store.attachComponent(itemEntityId, OWNER_ENTITY_ID, 0);
                        store.attachComponent(itemEntityId, SLOT_INDEX, -1);

                        // Set position
                        store.attachComponent(itemEntityId, EntityModuleFactory.POSITION_X, posX);
                        store.attachComponent(itemEntityId, EntityModuleFactory.POSITION_Y, posY);

                        log.info("Dropped item {} at ({}, {})", itemEntityId, posX, posY);
                    })
                    .build();
        }

        /**
         * Command to use an item (consume or activate).
         *
         * <p>Payload:
         * <ul>
         *   <li>itemEntityId (long) - Item entity to use</li>
         *   <li>userEntityId (long) - Entity using the item</li>
         * </ul>
         */
        private EngineCommand createUseItemCommand() {
            return CommandBuilder.newCommand()
                    .withName("useItem")
                    .withSchema(Map.of(
                            "itemEntityId", Long.class,
                            "userEntityId", Long.class
                    ))
                    .withExecution(payload -> {
                        Map<String, Object> data = payload.getPayload();
                        long itemEntityId = extractLong(data, "itemEntityId");
                        long userEntityId = extractLong(data, "userEntityId");

                        log.info("useItem: itemEntityId={}, userEntityId={}", itemEntityId, userEntityId);

                        EntityComponentStore store = context.getEntityComponentStore();

                        // Verify item exists
                        float flag = store.getComponent(itemEntityId, FLAG);
                        log.info("useItem: item FLAG={}", flag);
                        if (flag <= 0) {
                            log.warn("useItem: entity {} is not an item (flag={})", itemEntityId, flag);
                            return;
                        }

                        // Get heal amount (stored on item, can be used by external systems)
                        float healAmount = store.getComponent(itemEntityId, HEAL_AMOUNT);
                        log.info("useItem: item HEAL_AMOUNT={}", healAmount);

                        // Consume stack
                        float stackSize = store.getComponent(itemEntityId, STACK_SIZE);
                        stackSize--;
                        if (stackSize <= 0) {
                            // Remove item
                            for (BaseComponent c : CORE_COMPONENTS) {
                                store.removeComponent(itemEntityId, c);
                            }
                            store.removeComponent(itemEntityId, FLAG);
                            log.info("Item {} consumed and destroyed (healAmount={} available for external handling)",
                                    itemEntityId, healAmount);
                        } else {
                            store.attachComponent(itemEntityId, STACK_SIZE, stackSize);
                            log.info("Item {} used, stack now {} (healAmount={} available for external handling)",
                                    itemEntityId, stackSize, healAmount);
                        }
                    })
                    .build();
        }

        /**
         * Command to get all item types (for debugging).
         */
        private EngineCommand createGetItemTypesCommand() {
            return CommandBuilder.newCommand()
                    .withName("getItemTypes")
                    .withSchema(Map.of("matchId", Long.class))
                    .withExecution(payload -> {
                        Map<String, Object> data = payload.getPayload();
                        long matchId = extractLong(data, "matchId");
                        Map<Long, ItemTypeDefinition> matchItemTypes = getItemTypes(matchId);
                        log.info("Item types registered for match {}: {}", matchId, matchItemTypes.size());
                        for (ItemTypeDefinition def : matchItemTypes.values()) {
                            log.info("  Type {}: '{}' (maxStack={}, rarity={})",
                                    def.id(), def.name(), def.maxStack(), def.rarity());
                        }
                    })
                    .build();
        }

        private long extractLong(Map<String, Object> data, String key) {
            Object value = data.get(key);
            if (value == null) return 0;
            if (value instanceof Number n) return n.longValue();
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        private float extractFloat(Map<String, Object> data, String key, float defaultValue) {
            Object value = data.get(key);
            if (value == null) return defaultValue;
            if (value instanceof Number n) return n.floatValue();
            try {
                return Float.parseFloat(value.toString());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        private String extractString(Map<String, Object> data, String key, String defaultValue) {
            Object value = data.get(key);
            if (value == null) return defaultValue;
            return value.toString();
        }
    }

    /**
     * Item type definition (stored in registry, not ECS).
     */
    public record ItemTypeDefinition(
            long id,
            String name,
            int maxStack,
            int rarity,
            float value,
            float weight,
            float healAmount,
            float damageBonus,
            float armorValue
    ) {}

    /**
     * Base component for item-related data.
     */
    public static class ItemComponent extends BaseComponent {
        public ItemComponent(long id, String name) {
            super(id, name);
        }
    }
}
