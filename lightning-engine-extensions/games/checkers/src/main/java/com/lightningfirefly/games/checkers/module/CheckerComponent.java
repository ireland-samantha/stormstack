package com.lightningfirefly.games.checkers.module;

import com.lightningfirefly.engine.core.store.BaseComponent;

/**
 * Component class for checkers game ECS data.
 */
public class CheckerComponent extends BaseComponent {

    public CheckerComponent(long id, String name) {
        super(id, name);
    }

    public CheckerComponent(String name) {
        super(name);
    }
}
