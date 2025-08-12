package com.lightningfirefly.engine.internal.ext.games;

import com.lightningfirefly.engine.ext.games.GameMaster;

import java.util.List;

public interface GameMasterResolver {
    List<GameMaster> findAll();
    // to do for AI: Add other crud methods
    void save(GameMaster gameMaster);

    // to do for ai: install from jars same as module logic
    void install(String jarPath);

}
