package com.lightningfirefly.engine.ext.gamemasters;

import com.lightningfirefly.game.gm.GameMaster;
import com.lightningfirefly.game.gm.GameMasterContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Sample game master that counts ticks.
 *
 * <p>This is a simple demonstration of the GameMaster interface.
 * It tracks the number of ticks and logs progress every 100 ticks.
 *
 * <p>Use this as a template for creating your own game masters.
 */
@Slf4j
public class TickCounterGameMaster implements GameMaster {

    private final GameMasterContext context;
    private long tickCount = 0;

    public TickCounterGameMaster(GameMasterContext context) {
        this.context = context;
        log.info("TickCounterGameMaster initialized for match {}", context.getMatchId());
    }

    @Override
    public void onTick() {
        tickCount++;

        // Log every 100 ticks
        if (tickCount % 100 == 0) {
            log.info("Match {} - TickCounter: {} ticks processed",
                    context.getMatchId(), tickCount);
        }
    }

    /**
     * Get the current tick count.
     *
     * @return the number of ticks processed
     */
    public long getTickCount() {
        return tickCount;
    }
}
