package com.lightningfirefly.game.domain;

import java.util.List;

public interface GameScene {
    void attachSprite(List<Sprite> sprites);
    void attachControlSystem(ControlSystem controlSystem);
    void attachGm(GameMaster gameMaster);
}
