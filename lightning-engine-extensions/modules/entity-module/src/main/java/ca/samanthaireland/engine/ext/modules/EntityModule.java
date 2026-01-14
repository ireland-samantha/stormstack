package ca.samanthaireland.engine.ext.modules;

import ca.samanthaireland.engine.core.command.EngineCommand;
import ca.samanthaireland.engine.core.store.BaseComponent;
import ca.samanthaireland.engine.core.system.EngineSystem;
import ca.samanthaireland.engine.ext.module.EngineModule;
import ca.samanthaireland.engine.ext.module.ModuleContext;
import ca.samanthaireland.engine.ext.module.ModuleExports;
import ca.samanthaireland.engine.ext.modules.domain.repository.EntityRepository;
import ca.samanthaireland.engine.ext.modules.domain.service.EntityService;
import ca.samanthaireland.engine.ext.modules.ecs.command.SpawnCommand;
import ca.samanthaireland.engine.ext.modules.ecs.repository.EcsEntityRepository;

import java.util.List;

import static ca.samanthaireland.engine.ext.modules.EntityModuleFactory.ALL_COMPONENTS;
import static ca.samanthaireland.engine.ext.modules.EntityModuleFactory.FLAG;

/**
 * Entity module implementation.
 *
 * <p>Provides core entity functionality:
 * <ul>
 *   <li>Entity creation (spawn command)</li>
 *   <li>Entity metadata (type, owner, player)</li>
 * </ul>
 *
 * <p>Note: Position components are provided by GridMapModule.
 */
public class EntityModule implements EngineModule {
    private final EntityService entityService;

    public EntityModule(ModuleContext context) {
        EntityRepository entityRepository = new EcsEntityRepository(context);

        this.entityService = new EntityService(
                entityRepository,
                context.getMatchService(),
                context.getModuleResolver(),
                context
        );
    }

    @Override
    public List<EngineSystem> createSystems() {
        return List.of();
    }

    @Override
    public List<EngineCommand> createCommands() {
        return List.of(
                SpawnCommand.create(entityService)
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
        return "EntityModule";
    }

    @Override
    public List<ModuleExports> getExports() {
        return List.of();
    }
}
