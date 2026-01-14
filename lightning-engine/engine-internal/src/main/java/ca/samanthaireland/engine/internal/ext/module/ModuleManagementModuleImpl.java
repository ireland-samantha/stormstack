/*
 * Copyright (c) 2026 Samantha Ireland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */


package ca.samanthaireland.engine.internal.ext.module;

import ca.samanthaireland.engine.core.command.CommandBuilder;
import ca.samanthaireland.engine.core.command.EngineCommand;
import ca.samanthaireland.engine.ext.module.EngineModule;
import ca.samanthaireland.engine.ext.module.ModuleContext;
import ca.samanthaireland.engine.ext.module.ModuleFactory;
import ca.samanthaireland.engine.core.store.BaseComponent;
import ca.samanthaireland.engine.core.system.EngineSystem;

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
