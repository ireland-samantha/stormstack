package com.lightningfirefly.engine.ext.module;

import com.lightningfirefly.engine.core.command.EngineCommand;
import com.lightningfirefly.engine.core.store.BaseComponent;
import com.lightningfirefly.engine.core.system.EngineSystem;

import java.util.List;

public interface EngineModule {
    /**
     * Allocates heap memory for any systems the module adds to the game loop
     */
    List<EngineSystem> createSystems();

    /**
     * Allocates heap memory for any public-facing interfaces the module exposes
     */
    List<EngineCommand> createCommands();

    /**
     * Allocates heap memory for any components the module declares and is responsible for the lifecycle of.
     */
    List<BaseComponent> createComponents();

    BaseComponent createFlagComponent();

    String getName();
}
