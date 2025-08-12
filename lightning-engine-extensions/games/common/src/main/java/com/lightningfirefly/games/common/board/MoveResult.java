package com.lightningfirefly.games.common.board;

import java.util.List;
import java.util.Optional;

/**
 * Result of executing a move.
 */
public record MoveResult(
        Move move,
        boolean success,
        List<Long> capturedPieceIds,
        boolean wasPromotion,
        boolean gameOver,
        Optional<Player> winner,
        boolean canContinueCapturing
) {
    /**
     * Create a successful move result.
     */
    public static MoveResult success(Move move) {
        return new MoveResult(move, true, List.of(), false, false, Optional.empty(), false);
    }

    /**
     * Create a successful capture result.
     */
    public static MoveResult capture(Move move, List<Long> capturedIds, boolean canContinue) {
        return new MoveResult(move, true, capturedIds, false, false, Optional.empty(), canContinue);
    }

    /**
     * Create a promotion result.
     */
    public static MoveResult promotion(Move move) {
        return new MoveResult(move, true, List.of(), true, false, Optional.empty(), false);
    }

    /**
     * Create a game-ending result.
     */
    public static MoveResult gameEnd(Move move, Player winner) {
        return new MoveResult(move, true, List.of(), false, true, Optional.ofNullable(winner), false);
    }

    /**
     * Create a failed move result.
     */
    public static MoveResult failure(Move move) {
        return new MoveResult(move, false, List.of(), false, false, Optional.empty(), false);
    }
}
