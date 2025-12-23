package com.lightningfirefly.examples.checkers.gm.ecs;

import com.lightningfirefly.game.gm.GameMasterContext;
import com.lightningfirefly.game.engine.orchestrator.gm.GameMasterCommand;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Infrastructure layer for executing checkers commands.
 *
 * <p>This class provides a clean domain-oriented API for the GameMaster,
 * abstracting over the raw command creation and execution.
 *
 * <p>The GameMaster calls methods like {@code createPiece()} or {@code movePiece()},
 * and this executor translates them into GameMasterCommands sent to the ECS layer.
 */
@Slf4j
public class CheckersCommandExecutor {

    private final GameMasterContext context;

    public CheckersCommandExecutor(GameMasterContext context) {
        this.context = context;
    }

    // Rendering constants for converting board coordinates to screen pixels
    private static final float SQUARE_SIZE = 64.0f;
    private static final float BOARD_OFFSET_X = 50.0f;
    private static final float BOARD_OFFSET_Y = 50.0f;

    // Resource names for checker textures
    private static final String RED_CHECKER_RESOURCE = "red-checker";
    private static final String BLACK_CHECKER_RESOURCE = "black-checker";

    /**
     * Create a new checker piece on the board.
     *
     * @param entityId the entity ID for the piece
     * @param boardX   board X position (0-7)
     * @param boardY   board Y position (0-7)
     * @param player   player (1=red, 2=black)
     */
    public void createPiece(long entityId, int boardX, int boardY, int player) {
        log.debug("Creating piece {} at ({},{}) for player {}", entityId, boardX, boardY, player);

        // Create the checkers-specific components (include matchId for snapshot filtering)
        context.executeCommand(command("createPiece", Map.of(
                "entityId", entityId,
                "boardX", boardX,
                "boardY", boardY,
                "player", player,
                "matchId", context.getMatchId()
        )));

        // Also attach movement components for rendering
        long screenX = (long) (BOARD_OFFSET_X + boardX * SQUARE_SIZE + SQUARE_SIZE / 2);
        long screenY = (long) (BOARD_OFFSET_Y + boardY * SQUARE_SIZE + SQUARE_SIZE / 2);
        context.executeCommand(command("attachMovement", Map.of(
                "entityId", entityId,
                "positionX", screenX,
                "positionY", screenY,
                "positionZ", 0L,
                "velocityX", 0L,
                "velocityY", 0L,
                "velocityZ", 0L,
                "matchId", context.getMatchId()
        )));

        // Attach sprite resource for rendering
        String resourceName = player == 1 ? RED_CHECKER_RESOURCE : BLACK_CHECKER_RESOURCE;
        long resourceId = context.getResourceIdByName(resourceName);
        if (resourceId > 0) {
            context.executeCommand(command("attachSprite", Map.of(
                    "entityId", entityId,
                    "resourceId", resourceId
            )));
            log.debug("Attached sprite {} (resource {}) to piece {}", resourceName, resourceId, entityId);
        } else {
            log.warn("Resource '{}' not found, piece {} will not have a sprite", resourceName, entityId);
        }
    }

    /**
     * Move a piece to a new board position.
     *
     * @param entityId the piece to move
     * @param toX      target X position
     * @param toY      target Y position
     */
    public void movePiece(long entityId, int toX, int toY) {
        log.debug("Moving piece {} to ({},{})", entityId, toX, toY);

        // Update checkers-specific board position
        context.executeCommand(command("movePiece", Map.of(
                "entityId", entityId,
                "toX", toX,
                "toY", toY
        )));

        // Also update screen position for rendering
        long screenX = (long) (BOARD_OFFSET_X + toX * SQUARE_SIZE + SQUARE_SIZE / 2);
        long screenY = (long) (BOARD_OFFSET_Y + toY * SQUARE_SIZE + SQUARE_SIZE / 2);
        context.executeCommand(command("attachMovement", Map.of(
                "entityId", entityId,
                "positionX", screenX,
                "positionY", screenY,
                "positionZ", 0L,
                "velocityX", 0L,
                "velocityY", 0L,
                "velocityZ", 0L
        )));
    }

    /**
     * Capture (remove) a piece from the board.
     *
     * @param entityId the piece to capture
     */
    public void capturePiece(long entityId) {
        log.debug("Capturing piece {}", entityId);
        context.executeCommand(command("capturePiece", Map.of(
                "entityId", entityId
        )));
    }

    /**
     * Promote a piece to king.
     *
     * @param entityId the piece to promote
     */
    public void promotePiece(long entityId) {
        log.debug("Promoting piece {} to king", entityId);
        context.executeCommand(command("promotePiece", Map.of(
                "entityId", entityId
        )));
    }

    /**
     * Helper to create a GameMasterCommand.
     */
    private static GameMasterCommand command(String name, Map<String, Object> payload) {
        return new GameMasterCommand() {
            @Override
            public String commandName() {
                return name;
            }

            @Override
            public Map<String, Object> payload() {
                return payload;
            }
        };
    }
}
