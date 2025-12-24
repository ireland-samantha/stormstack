package com.lightningfirefly.examples.checkers.gm.domain;

import com.lightningfirefly.examples.checkers.gm.ecs.CheckersCommandExecutor;
import com.lightningfirefly.game.domain.GameMaster;
import com.lightningfirefly.game.domain.GameMasterContext;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Game Master for the checkers game.
 *
 * <p>This is where the domain logic lives. The GameMaster:
 * <ul>
 *   <li>Creates and manages domain objects (CheckerPiece, CheckersBoard)</li>
 *   <li>Processes game logic (moves, captures, promotions)</li>
 *   <li>Delegates command execution to the infrastructure layer</li>
 * </ul>
 */
@Slf4j
public class CheckersGameMaster implements GameMaster {

    private final GameMasterContext context;
    private final CheckersCommandExecutor executor;
    @Getter
    private final CheckersBoard board;
    private final AtomicLong entityIdGenerator = new AtomicLong(1000);
    private boolean initialized = false;

    public CheckersGameMaster(GameMasterContext context) {
        this.context = context;
        this.executor = new CheckersCommandExecutor(context);
        this.board = new CheckersBoard();
        log.info("CheckersGameMaster created for match {}", context.getMatchId());
    }

    /**
     * Constructor for testing with a custom executor.
     */
    public CheckersGameMaster(GameMasterContext context, CheckersCommandExecutor executor) {
        this.context = context;
        this.executor = executor;
        this.board = new CheckersBoard();
        log.info("CheckersGameMaster created for match {}", context.getMatchId());
    }

    @Override
    public void onTick() {
        if (!initialized) {
            initializeGame();
            initialized = true;
        }

        // Check for winner each tick
        board.getWinner().ifPresent(winner -> {
            String winnerName = winner == CheckerPiece.PLAYER_RED ? "RED" : "BLACK";
            log.info("Match {} - Game Over! {} wins!", context.getMatchId(), winnerName);
        });
    }

    /**
     * Initialize the game by creating all pieces on the board.
     */
    private void initializeGame() {
        log.info("Match {} - Initializing checkers game", context.getMatchId());

        // Create red pieces (rows 5-7, dark squares only)
        for (int y = 5; y <= 7; y++) {
            for (int x = 0; x < 8; x++) {
                if (board.isDarkSquare(x, y)) {
                    createPiece(x, y, CheckerPiece.PLAYER_RED);
                }
            }
        }

        // Create black pieces (rows 0-2, dark squares only)
        for (int y = 0; y <= 2; y++) {
            for (int x = 0; x < 8; x++) {
                if (board.isDarkSquare(x, y)) {
                    createPiece(x, y, CheckerPiece.PLAYER_BLACK);
                }
            }
        }

        log.info("Match {} - Created {} pieces", context.getMatchId(),
                board.getPiecesForPlayer(CheckerPiece.PLAYER_RED).size() +
                board.getPiecesForPlayer(CheckerPiece.PLAYER_BLACK).size());
    }

    /**
     * Create a new checker piece.
     */
    private void createPiece(int x, int y, int player) {
        long entityId = entityIdGenerator.incrementAndGet();

        // Create domain object
        CheckerPiece piece = new CheckerPiece(entityId, x, y, player);
        board.addPiece(piece);

        // Send command to ECS layer via executor
        executor.createPiece(entityId, x, y, player);

        log.debug("Created piece {} at ({},{}) for player {}", entityId, x, y, player);
    }

    /**
     * Process a move request from the player.
     *
     * @param pieceId the entity ID of the piece to move
     * @param toX     the target X position
     * @param toY     the target Y position
     * @return true if the move was valid and executed
     */
    public boolean processMove(long pieceId, int toX, int toY) {
        CheckersBoard.MoveResult result = board.tryMove(pieceId, toX, toY);

        if (!result.valid()) {
            log.warn("Invalid move: {}", result.errorMessage());
            return false;
        }

        // Send move command to ECS via executor
        executor.movePiece(pieceId, toX, toY);

        // Handle capture if any
        if (result.capturedPiece() != null) {
            executor.capturePiece(result.capturedPiece().getEntityId());
        }

        // Handle promotion if any
        if (result.promoted()) {
            executor.promotePiece(pieceId);
        }

        return true;
    }

    /**
     * Get the current player's turn.
     */
    public int getCurrentPlayer() {
        return board.getCurrentPlayer();
    }
}
