package com.lightningfirefly.examples.checkers.engine;

/**
 * Constants for the Checkers GameMaster.
 *
 * <p>The actual GameMasterFactory implementation is in the checkers-gamemaster module,
 * which is packaged as a separate JAR and uploaded to the server.
 */
public final class CheckersGameMasterFactory {

    /**
     * The name of the Checkers GameMaster as registered on the server.
     */
    public static final String NAME = "CheckersGameMaster";

    private CheckersGameMasterFactory() {
        // Constants only
    }
}
