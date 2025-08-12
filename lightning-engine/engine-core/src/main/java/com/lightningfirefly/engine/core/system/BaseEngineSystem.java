package com.lightningfirefly.engine.core.system;

import com.lightningfirefly.engine.ext.module.EngineModule;
import com.lightningfirefly.engine.ext.module.Injector;

public abstract class BaseEngineSystem implements EngineSystem {
    protected final EngineModule engineModule;
    protected final Injector injector;

    protected BaseEngineSystem(EngineModule engineModule, Injector injector) {
        this.engineModule = engineModule;
        this.injector = injector;
    }
}
