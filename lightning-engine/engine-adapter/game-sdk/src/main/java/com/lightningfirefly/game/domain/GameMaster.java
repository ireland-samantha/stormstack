package com.lightningfirefly.game.domain;

/**
 * A game master is where you implement game domain-specific logic.
 *
 * It should call other domain classes which call infrastructure methods which add
 * commands to the server queue.
 */
public interface GameMaster {
    void onTick();
}
