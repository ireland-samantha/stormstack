package com.lightningfirefly.game.backend.adapter;

import java.util.Map;

public interface GameMasterCommand {
    String commandName();
    Map<String, Object> payload();
}
