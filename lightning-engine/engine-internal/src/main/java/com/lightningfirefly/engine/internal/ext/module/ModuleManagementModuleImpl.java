package com.lightningfirefly.engine.internal.ext.module;

import com.lightningfirefly.engine.core.command.CommandBuilder;
import com.lightningfirefly.engine.core.command.EngineCommand;
import com.lightningfirefly.engine.ext.module.EngineModule;
import com.lightningfirefly.engine.ext.module.ModuleContext;
import com.lightningfirefly.engine.ext.module.ModuleFactory;
import com.lightningfirefly.engine.core.store.BaseComponent;
import com.lightningfirefly.engine.core.system.EngineSystem;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class ModuleManagementModuleImpl implements ModuleFactory {

    @Override
    public EngineModule create(ModuleContext context) {
        // ModuleManager is an internal type, so we use getModuleManager() and cast
        ModuleManager moduleManager = (ModuleManager) context.getModuleManager();
        return new ModuleModule(moduleManager);
    }

    public static class ModuleModule implements EngineModule {
        private final ModuleManager moduleManager;

        private final EngineCommand installModule;

        public ModuleModule(ModuleManager moduleManager) {
            this.moduleManager = moduleManager;
            this.installModule = CommandBuilder.newCommand()
                    .withName("installModule")
                    .withSchema(Map.of("jarFile", String.class))
                    .withExecution(payload -> {
                        try {
                            this.moduleManager.installModule(Path.of(String.valueOf(payload.getPayload().get("jarFile"))));
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to install module", e);
                        }
                    })
                    .build();
        }

        @Override
        public List<EngineSystem> createSystems() {
            return List.of();
        }

        @Override
        public List<EngineCommand> createCommands() {
            return List.of(installModule);
        }

        @Override
        public List<BaseComponent> createComponents() {
            return List.of();
        }

        @Override
        public BaseComponent createFlagComponent() {
            return null;
        }

        @Override
        public String getName() {
            return "ModuleManagement";
        }
    }

}
