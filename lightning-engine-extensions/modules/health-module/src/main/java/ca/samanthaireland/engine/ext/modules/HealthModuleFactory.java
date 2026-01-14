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

    // Module flag (PRIVATE - only EntityModule with superuser can attach during spawn)
    public static final BaseComponent FLAG = new PermissionComponent(
            IdGeneratorV2.newId(), "health", PermissionLevel.PRIVATE);

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
}
