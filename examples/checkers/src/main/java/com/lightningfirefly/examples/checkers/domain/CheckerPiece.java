package com.lightningfirefly.examples.checkers.domain;

import com.lightningfirefly.game.domain.DomainObject;
import com.lightningfirefly.game.domain.EcsComponent;
import com.lightningfirefly.game.domain.EcsEntityId;
import lombok.Getter;
import lombok.Setter;

/**
 * Domain object representing a checker piece.
 *
 * <p>Extends {@link DomainObject} to enable automatic synchronization with
 * ECS snapshots on the client side. The {@link EcsComponent} annotations
 * map fields to their corresponding ECS component paths.
 *
 * <p>On the server side (GameMaster), the setters are used directly to
 * update state. On the client side, fields are automatically updated
 * from snapshot data via the DomainObjectRegistry.
 */
@Getter
@Setter
public class CheckerPiece extends DomainObject {

    public static final int PLAYER_RED = 1;
    public static final int PLAYER_BLACK = 2;

    public static final int TYPE_NORMAL = 1;
    public static final int TYPE_KING = 2;

    @EcsEntityId
    private long entityId;

    @EcsComponent(componentPath = "CheckersModule.BOARD_X")
    private int boardX;

    @EcsComponent(componentPath = "CheckersModule.BOARD_Y")
    private int boardY;

    @EcsComponent(componentPath = "CheckersModule.PLAYER")
    private int player; // 1 = red, 2 = black

    @EcsComponent(componentPath = "CheckersModule.PIECE_TYPE")
    private int pieceType; // 1 = normal, 2 = king

    @EcsComponent(componentPath = "CheckersModule.IS_CAPTURED")
    private int capturedFlag; // 0 = not captured, 1 = captured

    public CheckerPiece(long entityId, int boardX, int boardY, int player) {
        super(entityId);
        this.entityId = entityId;
        this.boardX = boardX;
        this.boardY = boardY;
        this.player = player;
        this.pieceType = TYPE_NORMAL;
        this.capturedFlag = 0;
    }

    /**
     * Check if this piece is a king.
     */
    public boolean isKing() {
        return pieceType == TYPE_KING;
    }

    /**
     * Set this piece as a king.
     */
    public void setKing(boolean king) {
        this.pieceType = king ? TYPE_KING : TYPE_NORMAL;
    }

    /**
     * Check if this piece has been captured.
     */
    public boolean isCaptured() {
        return capturedFlag != 0;
    }

    /**
     * Set this piece as captured.
     */
    public void setCaptured(boolean captured) {
        this.capturedFlag = captured ? 1 : 0;
    }

    /**
     * Check if this piece can move in a given direction.
     * Regular pieces can only move forward (red moves up, black moves down).
     * Kings can move in both directions.
     */
    public boolean canMoveDirection(int deltaY) {
        if (isKing()) {
            return true;
        }
        // Red moves up (negative Y), Black moves down (positive Y)
        return (player == PLAYER_RED && deltaY < 0) ||
               (player == PLAYER_BLACK && deltaY > 0);
    }

    /**
     * Check if this piece should be promoted to king.
     */
    public boolean shouldPromote() {
        if (isKing()) {
            return false;
        }
        // Red reaches row 0, Black reaches row 7
        return (player == PLAYER_RED && boardY == 0) ||
               (player == PLAYER_BLACK && boardY == 7);
    }

    @Override
    protected void onSnapshotUpdated() {
        // Called after fields are updated from an ECS snapshot
        // Can be used for client-side reactions to state changes
    }

    @Override
    public String toString() {
        return String.format("CheckerPiece[id=%d, pos=(%d,%d), player=%s, king=%s]",
                entityId, boardX, boardY,
                player == PLAYER_RED ? "RED" : "BLACK",
                isKing());
    }
}
