package com.lightningfirefly.game.engine;

import com.lightningfirefly.game.gm.GameMaster;

import java.util.List;

public interface GameScene {
    void attachSprite(List<Sprite> sprites);
    void attachControlSystem(ControlSystem controlSystem);
    void attachGm(GameMaster gameMaster);
}
