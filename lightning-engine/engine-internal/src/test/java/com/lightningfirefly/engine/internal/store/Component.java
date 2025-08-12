package com.lightningfirefly.engine.internal.store;

import com.lightningfirefly.engine.core.store.BaseComponent;

import java.util.Set;

public class Component extends BaseComponent {

    public static final Component VELOCITY_X = new Component(1, "VELOCITY_X");
    public static final Component VELOCITY_Y = new Component(1, "VELOCITY_Y");
    public static final Component POSITION_X = new Component(1, "POSITION_X");
    public static final Component POSITION_Y = new Component(1, "POSITION_Y");

    public static final Set<BaseComponent> VALUES = Set.of(VELOCITY_X);

    public Component(int id, String name) {
        super(id, name);
    }

}
