package com.lightningfirefly.games.common.board;

/**
 * Exception thrown when an illegal move is attempted.
 */
public class IllegalMoveException extends RuntimeException {

    private final Move attemptedMove;
    private final String reason;

    public IllegalMoveException(Move move, String reason) {
        super("Illegal move " + move + ": " + reason);
        this.attemptedMove = move;
        this.reason = reason;
    }

    public IllegalMoveException(String message) {
        super(message);
        this.attemptedMove = null;
        this.reason = message;
    }

    public Move getAttemptedMove() {
        return attemptedMove;
    }

    public String getReason() {
        return reason;
    }
}
