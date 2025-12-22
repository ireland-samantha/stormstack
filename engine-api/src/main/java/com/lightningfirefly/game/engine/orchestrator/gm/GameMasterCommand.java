package com.lightningfirefly.game.engine.orchestrator.gm;

import java.util.Map;

public interface GameMasterCommand {
    String commandName();
    Map<String, Object> payload();
}
