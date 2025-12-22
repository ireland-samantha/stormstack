package com.lightningfirefly.engine.quarkus.api;

import com.lightningfirefly.engine.core.match.MatchService;
import com.lightningfirefly.engine.core.store.EntityComponentStore;
import com.lightningfirefly.engine.ext.gamemasters.TickCounterGameMasterFactory;
import com.lightningfirefly.engine.ext.module.ModuleContext;
import com.lightningfirefly.engine.ext.module.ModuleResolver;
import com.lightningfirefly.engine.ext.modules.MoveModuleFactory;
import com.lightningfirefly.engine.ext.modules.RenderingModuleFactory;
import com.lightningfirefly.engine.ext.modules.SpawnModuleFactory;
import com.lightningfirefly.engine.internal.ext.gamemaster.GameMasterManager;
import com.lightningfirefly.engine.internal.ext.module.ModuleManagementModuleImpl;
import com.lightningfirefly.engine.internal.ext.module.ModuleManager;
import io.quarkus.runtime.Startup;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.io.IOException;

@ApplicationScoped
@Startup
public class Bootstrap {
    @Inject
    EntityComponentStore entityComponentStore;

    @Inject
    ModuleContext moduleDependencyInjector;

    @Inject
    ModuleManager moduleManager;

    @Inject
    GameMasterManager gameMasterManager;

    @Inject
    ModuleResolver moduleResolver;

    @Inject
    MatchService matchService;

    void onStart(@Observes StartupEvent event) {
        // Install core engine modules
        moduleManager.installModule(ModuleManagementModuleImpl.class);
        moduleManager.installModule(SpawnModuleFactory.class);
        moduleManager.installModule(MoveModuleFactory.class);
        moduleManager.installModule(RenderingModuleFactory.class);

        // Install sample game masters
        gameMasterManager.installGameMaster(TickCounterGameMasterFactory.class);

        try {
            moduleManager.reloadInstalled();
            gameMasterManager.reloadInstalled();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        moduleDependencyInjector.addClass(MatchService.class, matchService);
        moduleDependencyInjector.addClass(ModuleManager.class, moduleManager);
        moduleDependencyInjector.addClass(ModuleResolver.class, moduleResolver);
        moduleDependencyInjector.addClass(EntityComponentStore.class, entityComponentStore);
    }
}
