package com.lightningfirefly.games.checkers.module;

import com.lightningfirefly.engine.core.command.CommandBuilder;
import com.lightningfirefly.engine.core.command.EngineCommand;
import com.lightningfirefly.engine.core.store.BaseComponent;
import com.lightningfirefly.engine.core.store.EntityComponentStore;
import com.lightningfirefly.engine.core.system.EngineSystem;
import com.lightningfirefly.engine.ext.module.EngineModule;
import com.lightningfirefly.engine.ext.module.ModuleContext;
import com.lightningfirefly.engine.ext.module.ModuleFactory;
import com.lightningfirefly.engine.util.IdGeneratorV2;
import com.lightningfirefly.games.common.board.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Module factory for the Checkers game with ECS-based storage.
 *
 * <p>Implements multiplayer checkers where all game state is stored in ECS:
 * <ul>
 *   <li>Piece entities with position, owner, king status, captured state</li>
 *   <li>Game state entity with current player, game over status, winner</li>
 *   <li>Commands for starting game, making moves, and resetting</li>
 *   <li>Systems for processing moves and validating game rules</li>
 * </ul>
 *
 * <p>Game state is synchronized to clients via ECS snapshots over WebSocket.
 */
@Slf4j
public class CheckersModuleFactory implements ModuleFactory {

    public static final int BOARD_SIZE = 8;
    public static final int PIECES_PER_PLAYER = 12;

    // Component definitions for ECS storage
    public static final BaseComponent CHECKER_ROW = new CheckerComponent(
            IdGeneratorV2.newId(), "CHECKER_ROW");
    public static final BaseComponent CHECKER_COL = new CheckerComponent(
            IdGeneratorV2.newId(), "CHECKER_COL");
    public static final BaseComponent CHECKER_OWNER = new CheckerComponent(
            IdGeneratorV2.newId(), "CHECKER_OWNER"); // 1 = PLAYER_ONE, 2 = PLAYER_TWO
    public static final BaseComponent CHECKER_IS_KING = new CheckerComponent(
            IdGeneratorV2.newId(), "CHECKER_IS_KING"); // 0 = false, 1 = true
    public static final BaseComponent CHECKER_IS_CAPTURED = new CheckerComponent(
            IdGeneratorV2.newId(), "CHECKER_IS_CAPTURED"); // 0 = false, 1 = true
    public static final BaseComponent CHECKER_PIECE_ID = new CheckerComponent(
            IdGeneratorV2.newId(), "CHECKER_PIECE_ID"); // Unique piece identifier
    public static final BaseComponent CHECKERS_MODULE = new CheckerComponent(
            IdGeneratorV2.newId(), "CHECKERS_MODULE");

    // Game state components (attached to a game entity)
    public static final BaseComponent GAME_CURRENT_PLAYER = new CheckerComponent(
            IdGeneratorV2.newId(), "GAME_CURRENT_PLAYER"); // 1 = PLAYER_ONE, 2 = PLAYER_TWO
    public static final BaseComponent GAME_IS_OVER = new CheckerComponent(
            IdGeneratorV2.newId(), "GAME_IS_OVER"); // 0 = false, 1 = true
    public static final BaseComponent GAME_WINNER = new CheckerComponent(
            IdGeneratorV2.newId(), "GAME_WINNER"); // 0 = none, 1 = PLAYER_ONE, 2 = PLAYER_TWO
    public static final BaseComponent GAME_MATCH_ID = new CheckerComponent(
            IdGeneratorV2.newId(), "GAME_MATCH_ID"); // Match ID this game belongs to
    public static final BaseComponent GAME_MUST_CONTINUE_FROM_ROW = new CheckerComponent(
            IdGeneratorV2.newId(), "GAME_MUST_CONTINUE_FROM_ROW"); // -1 if no continuation
    public static final BaseComponent GAME_MUST_CONTINUE_FROM_COL = new CheckerComponent(
            IdGeneratorV2.newId(), "GAME_MUST_CONTINUE_FROM_COL");

    public static final List<BaseComponent> PIECE_COMPONENTS = List.of(
            CHECKER_ROW, CHECKER_COL, CHECKER_OWNER, CHECKER_IS_KING,
            CHECKER_IS_CAPTURED, CHECKER_PIECE_ID, CHECKERS_MODULE
    );

    public static final List<BaseComponent> GAME_STATE_COMPONENTS = List.of(
            GAME_CURRENT_PLAYER, GAME_IS_OVER, GAME_WINNER, GAME_MATCH_ID,
            GAME_MUST_CONTINUE_FROM_ROW, GAME_MUST_CONTINUE_FROM_COL, CHECKERS_MODULE
    );

    public static final List<BaseComponent> ALL_COMPONENTS;
    static {
        List<BaseComponent> all = new ArrayList<>();
        all.addAll(PIECE_COMPONENTS);
        all.addAll(GAME_STATE_COMPONENTS);
        ALL_COMPONENTS = List.copyOf(all);
    }

    @Override
    public EngineModule create(ModuleContext context) {
        return new CheckersModule(context);
    }

    /**
     * The Checkers module implementation with ECS-based game state.
     */
    @Slf4j
    public static class CheckersModule implements EngineModule {

        private final ModuleContext context;

        // Map from matchId to game entity ID
        private final Map<Long, Long> gameEntityIds = new ConcurrentHashMap<>();
        // Map from matchId to map of (pieceId -> entityId)
        private final Map<Long, Map<Long, Long>> pieceEntityIds = new ConcurrentHashMap<>();

        // Pending commands to be processed by systems
        private final List<PendingMove> pendingMoves = Collections.synchronizedList(new ArrayList<>());
        private final List<Long> pendingResets = Collections.synchronizedList(new ArrayList<>());
        private final List<Long> pendingNewGames = Collections.synchronizedList(new ArrayList<>());

        public CheckersModule(ModuleContext context) {
            this.context = context;
        }

        @Override
        public List<EngineSystem> createSystems() {
            return List.of(createGameInitSystem(), createMoveProcessingSystem());
        }

        @Override
        public List<EngineCommand> createCommands() {
            return List.of(startGameCommand, moveCommand, resetCommand);
        }

        @Override
        public List<BaseComponent> createComponents() {
            return ALL_COMPONENTS;
        }

        @Override
        public BaseComponent createFlagComponent() {
            return CHECKERS_MODULE;
        }

        @Override
        public String getName() {
            return "CheckersModule";
        }

        /**
         * Get the game entity ID for a match.
         */
        public Long getGameEntityId(long matchId) {
            return gameEntityIds.get(matchId);
        }

        // ---------- Commands ----------

        private final EngineCommand startGameCommand = CommandBuilder.newCommand()
                .withName("CheckersStartGame")
                .withSchema(Map.of("matchId", Long.class))
                .withExecution(payload -> {
                    long matchId = getLong(payload.getPayload(), "matchId");
                    log.info("Queuing new checkers game for match {}", matchId);
                    pendingNewGames.add(matchId);
                })
                .build();

        private final EngineCommand moveCommand = CommandBuilder.newCommand()
                .withName("CheckersMove")
                .withSchema(Map.of(
                        "matchId", Long.class,
                        "fromRow", Long.class,
                        "fromCol", Long.class,
                        "toRow", Long.class,
                        "toCol", Long.class
                ))
                .withExecution(payload -> {
                    Map<String, Object> p = payload.getPayload();
                    long matchId = getLong(p, "matchId");
                    int fromRow = (int) getLong(p, "fromRow");
                    int fromCol = (int) getLong(p, "fromCol");
                    int toRow = (int) getLong(p, "toRow");
                    int toCol = (int) getLong(p, "toCol");

                    pendingMoves.add(new PendingMove(matchId, fromRow, fromCol, toRow, toCol));
                })
                .build();

        private final EngineCommand resetCommand = CommandBuilder.newCommand()
                .withName("CheckersReset")
                .withSchema(Map.of("matchId", Long.class))
                .withExecution(payload -> {
                    long matchId = getLong(payload.getPayload(), "matchId");
                    pendingResets.add(matchId);
                })
                .build();

        // ---------- Systems ----------

        /**
         * Create the system to initialize new games and handle resets.
         */
        private EngineSystem createGameInitSystem() {
            return () -> {
                EntityComponentStore store = context.getEntityComponentStore();

                // Process new game requests
                synchronized (pendingNewGames) {
                    for (Long matchId : pendingNewGames) {
                        initializeGame(store, matchId);
                    }
                    pendingNewGames.clear();
                }

                // Process resets
                synchronized (pendingResets) {
                    for (Long matchId : pendingResets) {
                        resetGame(store, matchId);
                    }
                    pendingResets.clear();
                }
            };
        }

        /**
         * Create the system to process pending moves.
         */
        private EngineSystem createMoveProcessingSystem() {
            return () -> {
                EntityComponentStore store = context.getEntityComponentStore();

                synchronized (pendingMoves) {
                    for (PendingMove pending : pendingMoves) {
                        processMove(store, pending);
                    }
                    pendingMoves.clear();
                }
            };
        }

        // ---------- Game Logic (ECS-based) ----------

        private void initializeGame(EntityComponentStore store, long matchId) {
            // Clean up any existing game
            cleanupGame(store, matchId);

            Map<Long, Long> pieceIds = new HashMap<>();
            long pieceIdCounter = 1;

            // Create pieces for Player 1 (top 3 rows)
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < BOARD_SIZE; col++) {
                    if ((row + col) % 2 == 1) {
                        long pieceId = pieceIdCounter++;
                        long entityId = Math.abs(IdGeneratorV2.newId());
                        pieceIds.put(pieceId, entityId);

                        store.attachComponents(entityId, PIECE_COMPONENTS, new float[]{
                                row,           // CHECKER_ROW
                                col,           // CHECKER_COL
                                1,             // CHECKER_OWNER (Player 1)
                                0,             // CHECKER_IS_KING
                                0,             // CHECKER_IS_CAPTURED
                                pieceId,       // CHECKER_PIECE_ID
                                1              // CHECKERS_MODULE flag
                        });
                    }
                }
            }

            // Create pieces for Player 2 (bottom 3 rows)
            for (int row = BOARD_SIZE - 3; row < BOARD_SIZE; row++) {
                for (int col = 0; col < BOARD_SIZE; col++) {
                    if ((row + col) % 2 == 1) {
                        long pieceId = pieceIdCounter++;
                        long entityId = Math.abs(IdGeneratorV2.newId());
                        pieceIds.put(pieceId, entityId);

                        store.attachComponents(entityId, PIECE_COMPONENTS, new float[]{
                                row,           // CHECKER_ROW
                                col,           // CHECKER_COL
                                2,             // CHECKER_OWNER (Player 2)
                                0,             // CHECKER_IS_KING
                                0,             // CHECKER_IS_CAPTURED
                                pieceId,       // CHECKER_PIECE_ID
                                1              // CHECKERS_MODULE flag
                        });
                    }
                }
            }

            pieceEntityIds.put(matchId, pieceIds);

            // Create game state entity
            long gameEntityId = Math.abs(IdGeneratorV2.newId());
            gameEntityIds.put(matchId, gameEntityId);

            store.attachComponents(gameEntityId, GAME_STATE_COMPONENTS, new float[]{
                    1,         // GAME_CURRENT_PLAYER (Player 1 starts)
                    0,         // GAME_IS_OVER
                    0,         // GAME_WINNER (none)
                    matchId,   // GAME_MATCH_ID
                    -1,        // GAME_MUST_CONTINUE_FROM_ROW (no continuation)
                    -1,        // GAME_MUST_CONTINUE_FROM_COL
                    1          // CHECKERS_MODULE flag
            });

            log.info("Initialized checkers game for match {} with {} piece entities",
                    matchId, pieceIds.size());
        }

        private void resetGame(EntityComponentStore store, long matchId) {
            log.info("Resetting game for match {}", matchId);
            initializeGame(store, matchId);
        }

        private void cleanupGame(EntityComponentStore store, long matchId) {
            // Delete existing piece entities
            Map<Long, Long> pieceIds = pieceEntityIds.remove(matchId);
            if (pieceIds != null) {
                for (Long entityId : pieceIds.values()) {
                    store.deleteEntity(entityId);
                }
            }

            // Delete game state entity
            Long gameEntityId = gameEntityIds.remove(matchId);
            if (gameEntityId != null) {
                store.deleteEntity(gameEntityId);
            }
        }

        private void processMove(EntityComponentStore store, PendingMove pending) {
            Long gameEntityId = gameEntityIds.get(pending.matchId);
            if (gameEntityId == null) {
                log.warn("No game found for match {}", pending.matchId);
                return;
            }

            // Read game state from ECS
            float[] gameState = new float[GAME_STATE_COMPONENTS.size()];
            store.getComponents(gameEntityId, GAME_STATE_COMPONENTS, gameState);

            int currentPlayer = (int) gameState[0];
            boolean isGameOver = gameState[1] == 1;

            if (isGameOver) {
                log.warn("Game already over for match {}", pending.matchId);
                return;
            }

            // Check for forced continuation
            int mustContinueRow = (int) gameState[4];
            int mustContinueCol = (int) gameState[5];

            if (mustContinueRow >= 0 && mustContinueCol >= 0) {
                if (pending.fromRow != mustContinueRow || pending.fromCol != mustContinueCol) {
                    log.warn("Must continue from ({}, {}) but attempted from ({}, {})",
                            mustContinueRow, mustContinueCol, pending.fromRow, pending.fromCol);
                    return;
                }
            }

            // Find the piece at the source position
            Long pieceEntityId = findPieceAt(store, pending.matchId, pending.fromRow, pending.fromCol);
            if (pieceEntityId == null) {
                log.warn("No piece at ({}, {}) in match {}", pending.fromRow, pending.fromCol, pending.matchId);
                return;
            }

            // Read piece data
            float[] pieceData = new float[PIECE_COMPONENTS.size()];
            store.getComponents(pieceEntityId, PIECE_COMPONENTS, pieceData);

            int pieceOwner = (int) pieceData[2];
            boolean isKing = pieceData[3] == 1;
            boolean isCaptured = pieceData[4] == 1;

            if (isCaptured || pieceOwner != currentPlayer) {
                log.warn("Invalid piece for move: captured={}, owner={}, currentPlayer={}",
                        isCaptured, pieceOwner, currentPlayer);
                return;
            }

            // Calculate valid moves for this piece
            List<Move> validMoves = getValidMovesForPiece(store, pending.matchId,
                    pending.fromRow, pending.fromCol, pieceOwner, isKing);

            // Find matching move
            BoardPosition from = new BoardPosition(pending.fromRow, pending.fromCol);
            BoardPosition to = new BoardPosition(pending.toRow, pending.toCol);
            Move matchingMove = validMoves.stream()
                    .filter(m -> m.getTo().equals(to))
                    .findFirst()
                    .orElse(null);

            if (matchingMove == null) {
                log.warn("Invalid move from ({},{}) to ({},{}) in match {}",
                        pending.fromRow, pending.fromCol, pending.toRow, pending.toCol, pending.matchId);
                return;
            }

            // Execute the move
            executeMove(store, pending.matchId, gameEntityId, pieceEntityId, matchingMove, isKing, pieceOwner);
        }

        private void executeMove(EntityComponentStore store, long matchId, long gameEntityId,
                                 long pieceEntityId, Move move, boolean wasKing, int pieceOwner) {

            // Update piece position
            store.attachComponent(pieceEntityId, CHECKER_ROW, (float) move.getTo().row());
            store.attachComponent(pieceEntityId, CHECKER_COL, (float) move.getTo().col());

            // Handle captures
            boolean wasCapture = !move.getCapturedPositions().isEmpty();
            if (wasCapture) {
                for (BoardPosition capturedPos : move.getCapturedPositions()) {
                    Long capturedEntityId = findPieceAt(store, matchId, capturedPos.row(), capturedPos.col());
                    if (capturedEntityId != null) {
                        store.attachComponent(capturedEntityId, CHECKER_IS_CAPTURED, 1.0f);
                        log.debug("Captured piece at ({}, {})", capturedPos.row(), capturedPos.col());
                    }
                }
            }

            // Check for promotion
            boolean promoted = false;
            if (!wasKing) {
                int promotionRow = (pieceOwner == 1) ? BOARD_SIZE - 1 : 0;
                if (move.getTo().row() == promotionRow) {
                    store.attachComponent(pieceEntityId, CHECKER_IS_KING, 1.0f);
                    promoted = true;
                    log.debug("Piece promoted to king at ({}, {})", move.getTo().row(), move.getTo().col());
                }
            }

            // Check for multi-jump (only if captured and not promoted)
            boolean canContinue = false;
            int continueRow = -1;
            int continueCol = -1;

            if (wasCapture && !promoted) {
                boolean isKing = wasKing || promoted;
                List<Move> moreCaptured = getCaptureMoves(store, matchId,
                        move.getTo().row(), move.getTo().col(), pieceOwner, isKing);
                if (!moreCaptured.isEmpty()) {
                    canContinue = true;
                    continueRow = move.getTo().row();
                    continueCol = move.getTo().col();
                }
            }

            // Update game state
            if (canContinue) {
                // Same player continues
                store.attachComponent(gameEntityId, GAME_MUST_CONTINUE_FROM_ROW, (float) continueRow);
                store.attachComponent(gameEntityId, GAME_MUST_CONTINUE_FROM_COL, (float) continueCol);
            } else {
                // Switch turns
                int nextPlayer = (pieceOwner == 1) ? 2 : 1;
                store.attachComponent(gameEntityId, GAME_CURRENT_PLAYER, (float) nextPlayer);
                store.attachComponent(gameEntityId, GAME_MUST_CONTINUE_FROM_ROW, -1.0f);
                store.attachComponent(gameEntityId, GAME_MUST_CONTINUE_FROM_COL, -1.0f);

                // Check for game over
                checkGameOver(store, matchId, gameEntityId, nextPlayer);
            }

            log.debug("Executed move {} -> {}, capture={}, promotion={}, continue={}",
                    move.getFrom(), move.getTo(), wasCapture, promoted, canContinue);
        }

        private void checkGameOver(EntityComponentStore store, long matchId, long gameEntityId, int nextPlayer) {
            // Count pieces for each player
            int player1Pieces = 0;
            int player2Pieces = 0;

            Map<Long, Long> pieceIds = pieceEntityIds.get(matchId);
            if (pieceIds == null) return;

            for (Long entityId : pieceIds.values()) {
                float[] data = new float[PIECE_COMPONENTS.size()];
                store.getComponents(entityId, PIECE_COMPONENTS, data);

                if (data[4] == 0) { // Not captured
                    if (data[2] == 1) player1Pieces++;
                    else player2Pieces++;
                }
            }

            // Check if a player has no pieces
            if (player1Pieces == 0) {
                store.attachComponent(gameEntityId, GAME_IS_OVER, 1.0f);
                store.attachComponent(gameEntityId, GAME_WINNER, 2.0f);
                log.info("Game over: Player 2 wins (Player 1 has no pieces)");
                return;
            }
            if (player2Pieces == 0) {
                store.attachComponent(gameEntityId, GAME_IS_OVER, 1.0f);
                store.attachComponent(gameEntityId, GAME_WINNER, 1.0f);
                log.info("Game over: Player 1 wins (Player 2 has no pieces)");
                return;
            }

            // Check if next player has valid moves
            boolean hasValidMove = false;
            for (Long entityId : pieceIds.values()) {
                float[] data = new float[PIECE_COMPONENTS.size()];
                store.getComponents(entityId, PIECE_COMPONENTS, data);

                if (data[4] == 0 && data[2] == nextPlayer) { // Not captured and owns piece
                    List<Move> moves = getValidMovesForPiece(store, matchId,
                            (int) data[0], (int) data[1], nextPlayer, data[3] == 1);
                    if (!moves.isEmpty()) {
                        hasValidMove = true;
                        break;
                    }
                }
            }

            if (!hasValidMove) {
                store.attachComponent(gameEntityId, GAME_IS_OVER, 1.0f);
                int winner = (nextPlayer == 1) ? 2 : 1;
                store.attachComponent(gameEntityId, GAME_WINNER, (float) winner);
                log.info("Game over: Player {} wins (Player {} has no valid moves)", winner, nextPlayer);
            }
        }

        private Long findPieceAt(EntityComponentStore store, long matchId, int row, int col) {
            Map<Long, Long> pieceIds = pieceEntityIds.get(matchId);
            if (pieceIds == null) return null;

            for (Long entityId : pieceIds.values()) {
                float[] data = new float[PIECE_COMPONENTS.size()];
                store.getComponents(entityId, PIECE_COMPONENTS, data);

                if (data[0] == row && data[1] == col && data[4] == 0) { // row, col, not captured
                    return entityId;
                }
            }
            return null;
        }

        private List<Move> getValidMovesForPiece(EntityComponentStore store, long matchId,
                                                  int row, int col, int owner, boolean isKing) {
            // If there are any captures available for this player, only captures are valid
            boolean playerHasCapture = hasAnyCapture(store, matchId, owner);

            List<Move> captures = getCaptureMoves(store, matchId, row, col, owner, isKing);
            if (!captures.isEmpty()) {
                return captures;
            }

            if (playerHasCapture) {
                // Must capture with another piece
                return List.of();
            }

            // Simple moves
            return getSimpleMoves(store, matchId, row, col, owner, isKing);
        }

        private boolean hasAnyCapture(EntityComponentStore store, long matchId, int owner) {
            Map<Long, Long> pieceIds = pieceEntityIds.get(matchId);
            if (pieceIds == null) return false;

            for (Long entityId : pieceIds.values()) {
                float[] data = new float[PIECE_COMPONENTS.size()];
                store.getComponents(entityId, PIECE_COMPONENTS, data);

                if (data[4] == 0 && data[2] == owner) { // Not captured and owned by player
                    List<Move> captures = getCaptureMoves(store, matchId,
                            (int) data[0], (int) data[1], owner, data[3] == 1);
                    if (!captures.isEmpty()) {
                        return true;
                    }
                }
            }
            return false;
        }

        private List<Move> getSimpleMoves(EntityComponentStore store, long matchId,
                                           int row, int col, int owner, boolean isKing) {
            List<Move> moves = new ArrayList<>();
            int forward = (owner == 1) ? 1 : -1;

            // Forward diagonal moves
            addSimpleMoveIfValid(moves, store, matchId, row, col, forward, -1);
            addSimpleMoveIfValid(moves, store, matchId, row, col, forward, 1);

            // Backward diagonal moves (kings only)
            if (isKing) {
                addSimpleMoveIfValid(moves, store, matchId, row, col, -forward, -1);
                addSimpleMoveIfValid(moves, store, matchId, row, col, -forward, 1);
            }

            return moves;
        }

        private void addSimpleMoveIfValid(List<Move> moves, EntityComponentStore store, long matchId,
                                          int row, int col, int deltaRow, int deltaCol) {
            int toRow = row + deltaRow;
            int toCol = col + deltaCol;

            if (toRow >= 0 && toRow < BOARD_SIZE && toCol >= 0 && toCol < BOARD_SIZE) {
                if (findPieceAt(store, matchId, toRow, toCol) == null) {
                    moves.add(new Move(
                            new BoardPosition(row, col),
                            new BoardPosition(toRow, toCol)
                    ));
                }
            }
        }

        private List<Move> getCaptureMoves(EntityComponentStore store, long matchId,
                                           int row, int col, int owner, boolean isKing) {
            List<Move> captures = new ArrayList<>();
            int forward = (owner == 1) ? 1 : -1;

            // Forward captures
            addCaptureIfValid(captures, store, matchId, row, col, forward, -1, owner);
            addCaptureIfValid(captures, store, matchId, row, col, forward, 1, owner);

            // Backward captures (kings only)
            if (isKing) {
                addCaptureIfValid(captures, store, matchId, row, col, -forward, -1, owner);
                addCaptureIfValid(captures, store, matchId, row, col, -forward, 1, owner);
            }

            return captures;
        }

        private void addCaptureIfValid(List<Move> captures, EntityComponentStore store, long matchId,
                                       int row, int col, int deltaRow, int deltaCol, int owner) {
            int enemyRow = row + deltaRow;
            int enemyCol = col + deltaCol;
            int landingRow = row + deltaRow * 2;
            int landingCol = col + deltaCol * 2;

            if (landingRow < 0 || landingRow >= BOARD_SIZE || landingCol < 0 || landingCol >= BOARD_SIZE) {
                return;
            }

            Long enemyEntityId = findPieceAt(store, matchId, enemyRow, enemyCol);
            if (enemyEntityId == null) return;

            // Check if enemy piece belongs to opponent
            float[] enemyData = new float[PIECE_COMPONENTS.size()];
            store.getComponents(enemyEntityId, PIECE_COMPONENTS, enemyData);

            if (enemyData[2] == owner || enemyData[4] == 1) {
                return; // Same owner or already captured
            }

            // Check if landing square is empty
            if (findPieceAt(store, matchId, landingRow, landingCol) != null) {
                return;
            }

            captures.add(new Move(
                    new BoardPosition(row, col),
                    new BoardPosition(landingRow, landingCol),
                    List.of(new BoardPosition(enemyRow, enemyCol)),
                    Move.MoveType.CAPTURE
            ));
        }

        // ---------- Helper Methods ----------

        private long getLong(Map<String, Object> map, String key) {
            Object value = map.get(key);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return Long.parseLong(String.valueOf(value));
        }

        private record PendingMove(long matchId, int fromRow, int fromCol, int toRow, int toCol) {}
    }
}
