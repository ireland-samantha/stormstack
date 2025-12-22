package com.lightningfirefly.engine.internal.ext.games;

import java.util.List;

public interface GameMasterResolver {
    List<GameMaster> findAll();

    // to do for AI: Add other crud methods
    void saveGm(GameMaster gameMaster);

    // to do for ai: install from jars same as module logic
    void installGm(String jarPath);

}
