package com.lightningfirefly.examples.checkers.input;

import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

/**
 * Client-side infrastructure layer for handling input and coordinate conversion.
 *
 * <p>This class converts screen coordinates to board coordinates and
 * invokes a callback for move processing. The callback is typically
 * connected to a server command sender.
 *
 * <p>This is a pure client-side class with no dependency on server-side code.
 */
@Slf4j
public class CheckersInputHandler {

    // Rendering/layout constants for coordinate conversion
    public static final float SQUARE_SIZE = 64.0f;
    public static final float BOARD_OFFSET_X = 50.0f;
    public static final float BOARD_OFFSET_Y = 50.0f;
    public static final int BOARD_SIZE = 8;

    private final Consumer<MoveCommand> moveCommandHandler;

    /**
     * Create an input handler with the given move command consumer.
     *
     * @param moveCommandHandler callback invoked when a valid move is requested
     */
    public CheckersInputHandler(Consumer<MoveCommand> moveCommandHandler) {
        this.moveCommandHandler = moveCommandHandler;
    }

    /**
     * Process a move request using screen coordinates.
     * Converts screen coordinates to board coordinates and validates.
     *
     * @param pieceId the entity ID of the piece to move
     * @param screenX the target X screen position
     * @param screenY the target Y screen position
     * @return true if the position was valid (command sent)
     */
    public boolean processMoveFromScreen(long pieceId, float screenX, float screenY) {
        int boardX = screenToBoardX(screenX);
        int boardY = screenToBoardY(screenY);

        if (!isValidBoardPosition(boardX, boardY)) {
            log.debug("Clicked outside board at screen ({}, {})", screenX, screenY);
            return false;
        }

        log.debug("Sending move command: piece {} to ({}, {})", pieceId, boardX, boardY);
        moveCommandHandler.accept(new MoveCommand(pieceId, boardX, boardY));
        return true;
    }

    /**
     * Check if screen coordinates are within the board bounds.
     */
    public boolean isScreenPositionOnBoard(float screenX, float screenY) {
        int boardX = screenToBoardX(screenX);
        int boardY = screenToBoardY(screenY);
        return isValidBoardPosition(boardX, boardY);
    }

    /**
     * Convert screen X coordinate to board X coordinate.
     */
    public static int screenToBoardX(float screenX) {
        return (int) ((screenX - BOARD_OFFSET_X) / SQUARE_SIZE);
    }

    /**
     * Convert screen Y coordinate to board Y coordinate.
     */
    public static int screenToBoardY(float screenY) {
        return (int) ((screenY - BOARD_OFFSET_Y) / SQUARE_SIZE);
    }

    /**
     * Convert board X coordinate to screen X coordinate (center of square).
     */
    public static float boardToScreenX(int boardX) {
        return BOARD_OFFSET_X + boardX * SQUARE_SIZE + SQUARE_SIZE / 2;
    }

    /**
     * Convert board Y coordinate to screen Y coordinate (center of square).
     */
    public static float boardToScreenY(int boardY) {
        return BOARD_OFFSET_Y + boardY * SQUARE_SIZE + SQUARE_SIZE / 2;
    }

    /**
     * Check if board coordinates are valid.
     */
    public static boolean isValidBoardPosition(int x, int y) {
        return x >= 0 && x < BOARD_SIZE && y >= 0 && y < BOARD_SIZE;
    }

    /**
     * Command to move a piece to a board position.
     * Sent to the server for validation and execution.
     */
    public record MoveCommand(long entityId, int boardX, int boardY) {}
}
