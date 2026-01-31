package ca.samanthaireland.stormstack.thunder.engine.ext.modules.ecs.component;

import ca.samanthaireland.stormstack.thunder.engine.core.store.BaseComponent;
import ca.samanthaireland.stormstack.thunder.engine.core.store.PermissionComponent;
import ca.samanthaireland.stormstack.thunder.engine.core.store.PermissionLevel;

/**
 * Base component for rendering-related data.
 */
public class RenderingComponent extends PermissionComponent {
    public RenderingComponent(long id, String name) {
        super(id, name, PermissionLevel.READ);
    }
}
