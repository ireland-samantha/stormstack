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
 * Module factory for the Health module.
 *
 * <p>Provides health/damage management:
 * <ul>
 *   <li>CURRENT_HP - Current hit points</li>
 *   <li>MAX_HP - Maximum hit points</li>
 *   <li>DAMAGE_TAKEN - Damage accumulated this tick (cleared after processing)</li>
 *   <li>IS_DEAD - Flag indicating entity has died (HP <= 0)</li>
 *   <li>INVULNERABLE - Flag to prevent damage</li>
 * </ul>
 *
 * <p>Commands:
 * <ul>
 *   <li>attachHealth - Attach health to an entity with maxHP</li>
 *   <li>damage - Deal damage to an entity</li>
 *   <li>heal - Restore health to an entity</li>
 *   <li>setInvulnerable - Toggle invulnerability</li>
 * </ul>
 */
@Slf4j
public class HealthModuleFactory implements ModuleFactory {

    // Health components
    public static final BaseComponent CURRENT_HP = new HealthComponent(
            IdGeneratorV2.newId(), "CURRENT_HP");
    public static final BaseComponent MAX_HP = new HealthComponent(
            IdGeneratorV2.newId(), "MAX_HP");
    public static final BaseComponent DAMAGE_TAKEN = new HealthComponent(
            IdGeneratorV2.newId(), "DAMAGE_TAKEN");
    public static final BaseComponent IS_DEAD = new HealthComponent(
            IdGeneratorV2.newId(), "IS_DEAD");
    public static final BaseComponent INVULNERABLE = new HealthComponent(
            IdGeneratorV2.newId(), "INVULNERABLE");

    // Module flag
    public static final BaseComponent FLAG = new HealthComponent(
            IdGeneratorV2.newId(), "health");

    /**
     * Core components for health tracking.
     */
    public static final List<BaseComponent> CORE_COMPONENTS = List.of(
            CURRENT_HP, MAX_HP, DAMAGE_TAKEN, IS_DEAD, INVULNERABLE
    );

    /**
     * Components for snapshot export.
     */
    public static final List<BaseComponent> ALL_COMPONENTS = CORE_COMPONENTS;

    @Override
    public EngineModule create(ModuleContext context) {
        return new HealthModule(context);
    }

    /**
     * Health module implementation.
     */
    public static class HealthModule implements EngineModule {
        private final ModuleContext context;

        public HealthModule(ModuleContext context) {
            this.context = context;
        }

        @Override
        public List<EngineSystem> createSystems() {
            return List.of(createDamageProcessingSystem());
        }

        @Override
        public List<EngineCommand> createCommands() {
            return List.of(
                    createAttachHealthCommand(),
                    createDamageCommand(),
                    createHealCommand(),
                    createSetInvulnerableCommand()
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
            return "HealthModule";
        }

        /**
         * System to process damage each tick.
         *
         * <p>For each entity with health:
         * <ol>
         *   <li>Check if invulnerable - skip damage if so</li>
         *   <li>Subtract DAMAGE_TAKEN from CURRENT_HP</li>
         *   <li>Clamp HP to [0, MAX_HP]</li>
         *   <li>Set IS_DEAD if HP <= 0</li>
         *   <li>Clear DAMAGE_TAKEN for next tick</li>
         * </ol>
         */
        private EngineSystem createDamageProcessingSystem() {
            return () -> {
                EntityComponentStore store = context.getEntityComponentStore();
                Set<Long> entities = store.getEntitiesWithComponents(List.of(FLAG));

                for (Long entity : entities) {
                    float isDead = store.getComponent(entity, IS_DEAD);
                    if (isDead > 0) {
                        continue; // Skip dead entities
                    }

                    float invulnerable = store.getComponent(entity, INVULNERABLE);
                    float damageTaken = store.getComponent(entity, DAMAGE_TAKEN);

                    if (damageTaken > 0 && invulnerable <= 0) {
                        float currentHP = store.getComponent(entity, CURRENT_HP);
                        float maxHP = store.getComponent(entity, MAX_HP);

                        currentHP -= damageTaken;

                        // Clamp HP
                        if (currentHP <= 0) {
                            currentHP = 0;
                            store.attachComponent(entity, IS_DEAD, 1.0f);
                            log.info("Entity {} has died", entity);
                        } else if (currentHP > maxHP) {
                            currentHP = maxHP;
                        }

                        store.attachComponent(entity, CURRENT_HP, currentHP);
                        log.debug("Entity {} took {} damage, HP now {}/{}",
                                entity, damageTaken, currentHP, maxHP);
                    }

                    // Clear damage taken for next tick
                    store.attachComponent(entity, DAMAGE_TAKEN, 0);
                }
            };
        }

        /**
         * Command to attach health to an entity.
         *
         * <p>Payload:
         * <ul>
         *   <li>entityId (long) - Target entity</li>
         *   <li>maxHP (float) - Maximum hit points</li>
         *   <li>currentHP (float) - Starting HP (optional, defaults to maxHP)</li>
         * </ul>
         */
        private EngineCommand createAttachHealthCommand() {
            return CommandBuilder.newCommand()
                    .withName("attachHealth")
                    .withSchema(Map.of(
                            "entityId", Long.class,
                            "maxHP", Float.class,
                            "currentHP", Float.class
                    ))
                    .withExecution(payload -> {
                        Map<String, Object> data = payload.getPayload();
                        long entityId = extractLong(data, "entityId");
                        if (entityId == 0) {
                            log.warn("attachHealth: missing entityId");
                            return;
                        }

                        float maxHP = extractFloat(data, "maxHP", 100);
                        float currentHP = extractFloat(data, "currentHP", maxHP);

                        EntityComponentStore store = context.getEntityComponentStore();
                        store.attachComponent(entityId, MAX_HP, maxHP);
                        store.attachComponent(entityId, CURRENT_HP, currentHP);
                        store.attachComponent(entityId, DAMAGE_TAKEN, 0);
                        store.attachComponent(entityId, IS_DEAD, 0);
                        store.attachComponent(entityId, INVULNERABLE, 0);
                        store.attachComponent(entityId, FLAG, 1.0f);

                        log.info("Attached health to entity {}: {}/{} HP",
                                entityId, currentHP, maxHP);
                    })
                    .build();
        }

        /**
         * Command to deal damage to an entity.
         *
         * <p>Payload:
         * <ul>
         *   <li>entityId (long) - Target entity</li>
         *   <li>amount (float) - Damage amount</li>
         * </ul>
         */
        private EngineCommand createDamageCommand() {
            return CommandBuilder.newCommand()
                    .withName("damage")
                    .withSchema(Map.of(
                            "entityId", Long.class,
                            "amount", Float.class
                    ))
                    .withExecution(payload -> {
                        Map<String, Object> data = payload.getPayload();
                        long entityId = extractLong(data, "entityId");
                        float amount = extractFloat(data, "amount", 0);

                        if (entityId == 0 || amount <= 0) {
                            log.warn("damage: invalid entityId or amount");
                            return;
                        }

                        EntityComponentStore store = context.getEntityComponentStore();

                        // Check if entity has health
                        float flag = store.getComponent(entityId, FLAG);
                        if (flag <= 0) {
                            log.warn("damage: entity {} does not have health", entityId);
                            return;
                        }

                        // Accumulate damage (processed by system)
                        float currentDamage = store.getComponent(entityId, DAMAGE_TAKEN);
                        store.attachComponent(entityId, DAMAGE_TAKEN, currentDamage + amount);

                        log.debug("Queued {} damage to entity {}", amount, entityId);
                    })
                    .build();
        }

        /**
         * Command to heal an entity.
         *
         * <p>Payload:
         * <ul>
         *   <li>entityId (long) - Target entity</li>
         *   <li>amount (float) - Heal amount</li>
         * </ul>
         */
        private EngineCommand createHealCommand() {
            return CommandBuilder.newCommand()
                    .withName("heal")
                    .withSchema(Map.of(
                            "entityId", Long.class,
                            "amount", Float.class
                    ))
                    .withExecution(payload -> {
                        Map<String, Object> data = payload.getPayload();
                        long entityId = extractLong(data, "entityId");
                        float amount = extractFloat(data, "amount", 0);

                        if (entityId == 0 || amount <= 0) {
                            log.warn("heal: invalid entityId or amount");
                            return;
                        }

                        EntityComponentStore store = context.getEntityComponentStore();

                        // Check if entity has health and is not dead
                        float flag = store.getComponent(entityId, FLAG);
                        float isDead = store.getComponent(entityId, IS_DEAD);
                        if (flag <= 0 || isDead > 0) {
                            log.warn("heal: entity {} does not have health or is dead", entityId);
                            return;
                        }

                        float currentHP = store.getComponent(entityId, CURRENT_HP);
                        float maxHP = store.getComponent(entityId, MAX_HP);

                        currentHP = Math.min(currentHP + amount, maxHP);
                        store.attachComponent(entityId, CURRENT_HP, currentHP);

                        log.debug("Healed entity {} by {}, HP now {}/{}",
                                entityId, amount, currentHP, maxHP);
                    })
                    .build();
        }

        /**
         * Command to set invulnerability.
         *
         * <p>Payload:
         * <ul>
         *   <li>entityId (long) - Target entity</li>
         *   <li>invulnerable (boolean/float) - 1 for invulnerable, 0 for vulnerable</li>
         * </ul>
         */
        private EngineCommand createSetInvulnerableCommand() {
            return CommandBuilder.newCommand()
                    .withName("setInvulnerable")
                    .withSchema(Map.of(
                            "entityId", Long.class,
                            "invulnerable", Float.class
                    ))
                    .withExecution(payload -> {
                        Map<String, Object> data = payload.getPayload();
                        long entityId = extractLong(data, "entityId");
                        float invulnerable = extractFloat(data, "invulnerable", 0);

                        if (entityId == 0) {
                            log.warn("setInvulnerable: missing entityId");
                            return;
                        }

                        EntityComponentStore store = context.getEntityComponentStore();
                        store.attachComponent(entityId, INVULNERABLE, invulnerable > 0 ? 1.0f : 0);

                        log.debug("Set entity {} invulnerable={}", entityId, invulnerable > 0);
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
    }

    /**
     * Base component for health-related data.
     */
    public static class HealthComponent extends BaseComponent {
        public HealthComponent(long id, String name) {
            super(id, name);
        }
    }
}
