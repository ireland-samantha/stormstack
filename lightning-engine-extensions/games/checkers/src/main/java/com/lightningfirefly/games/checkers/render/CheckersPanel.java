package com.lightningfirefly.games.checkers.render;

import com.lightningfirefly.engine.api.resource.adapter.CommandAdapter;
import com.lightningfirefly.engine.rendering.render2d.impl.opengl.GLColour;
import com.lightningfirefly.games.checkers.model.CheckersGame;
import com.lightningfirefly.games.checkers.module.CheckersModuleFactory;
import com.lightningfirefly.games.common.board.BoardPosition;
import com.lightningfirefly.games.common.board.Move;
import com.lightningfirefly.games.common.resource.HttpLazyResourceLoader;
import com.lightningfirefly.games.common.resource.LazyResourceLoader;
import com.lightningfirefly.engine.rendering.render2d.impl.opengl.GLButton;
import com.lightningfirefly.engine.rendering.render2d.impl.opengl.GLLabel;
import com.lightningfirefly.engine.rendering.render2d.impl.opengl.GLPanel;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.*;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * GUI Panel for playing multiplayer checkers.
 *
 * <p>Provides a complete checkers playing experience with:
 * <ul>
 *   <li>Visual board rendering based on ECS snapshot data</li>
 *   <li>Click-to-move interaction</li>
 *   <li>Valid move highlighting (using local game for preview)</li>
 *   <li>Game state display</li>
 *   <li>Backend API integration via commands</li>
 *   <li>Lazy resource loading for textures</li>
 * </ul>
 *
 * <p>For multiplayer, the panel:
 * <ul>
 *   <li>Sends move commands to the server</li>
 *   <li>Receives snapshot data via WebSocket to update display</li>
 *   <li>Uses local CheckersGame for move preview/validation only</li>
 * </ul>
 */
@Slf4j
public class CheckersPanel extends GLPanel {

    private final CheckersRenderer renderer;
    private final LazyResourceLoader resourceLoader;
    private final CommandAdapter commandAdapter;

    private final GLLabel statusLabel;
    private final GLLabel turnLabel;
    private final GLButton resetButton;
    private final GLButton newGameButton;

    private final long matchId;
    private final String serverUrl;

    // Local game state for move preview (not authoritative - server is authoritative)
    private CheckersGame localGame;

    // State from server snapshots
    private volatile List<PieceState> serverPieces = new ArrayList<>();
    private volatile int serverCurrentPlayer = 1;
    private volatile boolean serverGameOver = false;
    private volatile int serverWinner = 0;
    private volatile int mustContinueRow = -1;
    private volatile int mustContinueCol = -1;
    private volatile boolean needsRefresh = false;

    private BoardPosition selectedPosition;
    private List<Move> validMoves = List.of();

    // Board layout
    private static final int TITLE_BAR_HEIGHT = 26; // Default panel titleFontSize(14) + 12
    private static final int BOARD_MARGIN = 20;
    private static final int STATUS_HEIGHT = 60;

    /**
     * Create a checkers panel for local play (no server).
     */
    public CheckersPanel(int x, int y, int width, int height) {
        this(x, y, width, height, null, 0);
    }

    /**
     * Create a checkers panel with server integration.
     *
     * @param x panel X position
     * @param y panel Y position
     * @param width panel width
     * @param height panel height
     * @param serverUrl the server URL for resource loading and commands
     * @param matchId the match ID for backend integration
     */
    public CheckersPanel(int x, int y, int width, int height, String serverUrl, long matchId) {
        super(x, y, width, height);
        setTitle("Checkers" + (matchId > 0 ? " - Match " + matchId : ""));

        this.serverUrl = serverUrl;
        this.matchId = matchId;
        this.localGame = new CheckersGame();

        // Setup resource loader if server URL provided
        if (serverUrl != null && !serverUrl.isEmpty()) {
            this.resourceLoader = new HttpLazyResourceLoader(serverUrl);
            this.commandAdapter = new CommandAdapter.HttpCommandAdapter(serverUrl);
        } else {
            this.resourceLoader = null;
            this.commandAdapter = null;
        }

        this.renderer = new CheckersRenderer(resourceLoader);
        renderer.initialize(localGame);

        // Calculate board size based on panel size
        int availableHeight = height - TITLE_BAR_HEIGHT - STATUS_HEIGHT - BOARD_MARGIN * 2;
        int availableWidth = width - BOARD_MARGIN * 2;
        int boardSize = Math.min(availableWidth, availableHeight);
        int squareSize = boardSize / CheckersModuleFactory.BOARD_SIZE;

        renderer.setSquareSize(squareSize);
        renderer.setOrigin(x + BOARD_MARGIN, y + TITLE_BAR_HEIGHT + STATUS_HEIGHT);

        // Create UI components
        statusLabel = new GLLabel(x + 10, y + TITLE_BAR_HEIGHT + 10, "Click a piece to move");
        statusLabel.setFontSize(14.0f);
        statusLabel.setTextColor(GLColour.TEXT_PRIMARY);

        turnLabel = new GLLabel(x + width - 150, y + TITLE_BAR_HEIGHT + 10, "Turn: Player 1");
        turnLabel.setFontSize(14.0f);
        turnLabel.setTextColor(GLColour.RED);

        resetButton = new GLButton(x + 10, y + TITLE_BAR_HEIGHT + 35, 80, 25, "Reset");
        resetButton.setOnClick(this::resetGame);

        newGameButton = new GLButton(x + 100, y + TITLE_BAR_HEIGHT + 35, 100, 25, "New Game");
        newGameButton.setOnClick(this::startNewGame);

        addChild(statusLabel);
        addChild(turnLabel);
        addChild(resetButton);
        addChild(newGameButton);

        updateTurnLabel();
    }

    /**
     * Update the panel state from an ECS snapshot.
     *
     * <p>Call this method when receiving snapshot data from WebSocket.
     * The snapshot should contain CheckersModule component data.
     *
     * @param snapshotData map of component name to list of values
     */
    public void updateFromSnapshot(Map<String, List<Long>> snapshotData) {
        if (snapshotData == null) return;

        List<Long> rows = snapshotData.get("CHECKER_ROW");
        List<Long> cols = snapshotData.get("CHECKER_COL");
        List<Long> owners = snapshotData.get("CHECKER_OWNER");
        List<Long> kings = snapshotData.get("CHECKER_IS_KING");
        List<Long> captured = snapshotData.get("CHECKER_IS_CAPTURED");
        List<Long> pieceIds = snapshotData.get("CHECKER_PIECE_ID");

        List<Long> currentPlayers = snapshotData.get("GAME_CURRENT_PLAYER");
        List<Long> gameOvers = snapshotData.get("GAME_IS_OVER");
        List<Long> winners = snapshotData.get("GAME_WINNER");
        List<Long> continueRows = snapshotData.get("GAME_MUST_CONTINUE_FROM_ROW");
        List<Long> continueCols = snapshotData.get("GAME_MUST_CONTINUE_FROM_COL");

        // Update piece state
        if (rows != null && cols != null && owners != null) {
            List<PieceState> newPieces = new ArrayList<>();
            int count = Math.min(rows.size(), Math.min(cols.size(), owners.size()));

            for (int i = 0; i < count; i++) {
                boolean isCaptured = captured != null && i < captured.size() && captured.get(i) == 1;
                if (!isCaptured) {
                    newPieces.add(new PieceState(
                            pieceIds != null && i < pieceIds.size() ? pieceIds.get(i) : i,
                            rows.get(i).intValue(),
                            cols.get(i).intValue(),
                            owners.get(i).intValue(),
                            kings != null && i < kings.size() && kings.get(i) == 1
                    ));
                }
            }
            serverPieces = newPieces;
        }

        // Update game state
        if (currentPlayers != null && !currentPlayers.isEmpty()) {
            serverCurrentPlayer = currentPlayers.get(0).intValue();
        }
        if (gameOvers != null && !gameOvers.isEmpty()) {
            serverGameOver = gameOvers.get(0) == 1;
        }
        if (winners != null && !winners.isEmpty()) {
            serverWinner = winners.get(0).intValue();
        }
        if (continueRows != null && !continueRows.isEmpty()) {
            mustContinueRow = continueRows.get(0).intValue();
        }
        if (continueCols != null && !continueCols.isEmpty()) {
            mustContinueCol = continueCols.get(0).intValue();
        }

        // Sync local game with server state
        syncLocalGameWithServer();
        needsRefresh = true;
    }

    /**
     * Sync local game state with server snapshot for move preview.
     */
    private void syncLocalGameWithServer() {
        localGame = new CheckersGame();
        localGame.reset();

        // This is a simplified sync - in production, you'd want more robust state reconstruction
        // The local game is used for move preview/validation only
    }

    @Override
    public void render(long nvg) {
        super.render(nvg);

        // Update UI if needed
        if (needsRefresh) {
            needsRefresh = false;
            updateTurnLabel();
            clearSelection();
        }

        // Render the checkers board with server state
        renderBoard(nvg);

        // Draw game over overlay if needed
        if (serverGameOver) {
            drawGameOverOverlay(nvg);
        }
    }

    private void renderBoard(long nvg) {
        // Draw board squares
        int squareSize = renderer.getSquareSize();
        int originX = getX() + BOARD_MARGIN;
        int originY = getY() + TITLE_BAR_HEIGHT + STATUS_HEIGHT;

        for (int row = 0; row < CheckersModuleFactory.BOARD_SIZE; row++) {
            for (int col = 0; col < CheckersModuleFactory.BOARD_SIZE; col++) {
                int x = originX + col * squareSize;
                int y = originY + row * squareSize;

                boolean isDark = (row + col) % 2 == 1;
                int color = isDark ? 0xFF8B5A2B : 0xFFDEB887; // Dark brown / Light tan

                nvgBeginPath(nvg);
                nvgRect(nvg, x, y, squareSize, squareSize);
                nvgFillColor(nvg, nvgColor(color));
                nvgFill(nvg);
            }
        }

        // Draw board border
        nvgBeginPath(nvg);
        nvgRect(nvg, originX, originY,
                CheckersModuleFactory.BOARD_SIZE * squareSize,
                CheckersModuleFactory.BOARD_SIZE * squareSize);
        nvgStrokeColor(nvg, nvgRGBA((byte) 0, (byte) 0, (byte) 0, (byte) 255));
        nvgStrokeWidth(nvg, 2);
        nvgStroke(nvg);

        // Draw valid move highlights
        for (Move move : validMoves) {
            BoardPosition to = move.getTo();
            int x = originX + to.col() * squareSize;
            int y = originY + to.row() * squareSize;

            nvgBeginPath(nvg);
            nvgRect(nvg, x + 5, y + 5, squareSize - 10, squareSize - 10);
            nvgFillColor(nvg, nvgRGBA((byte) 0, (byte) 255, (byte) 0, (byte) 100));
            nvgFill(nvg);

            if (move.isCapture()) {
                nvgBeginPath(nvg);
                nvgCircle(nvg, x + squareSize / 2.0f, y + squareSize / 2.0f, squareSize / 4.0f);
                nvgStrokeColor(nvg, nvgRGBA((byte) 255, (byte) 0, (byte) 0, (byte) 200));
                nvgStrokeWidth(nvg, 3);
                nvgStroke(nvg);
            }
        }

        // Draw selection
        if (selectedPosition != null) {
            int x = originX + selectedPosition.col() * squareSize;
            int y = originY + selectedPosition.row() * squareSize;
            nvgBeginPath(nvg);
            nvgRect(nvg, x + 2, y + 2, squareSize - 4, squareSize - 4);
            nvgStrokeColor(nvg, nvgRGBA((byte) 0, (byte) 100, (byte) 255, (byte) 200));
            nvgStrokeWidth(nvg, 4);
            nvgStroke(nvg);
        }

        // Draw pieces from server state
        for (PieceState piece : serverPieces) {
            int x = originX + piece.col * squareSize;
            int y = originY + piece.row * squareSize;
            drawPiece(nvg, piece, x, y, squareSize);
        }
    }

    private void drawPiece(long nvg, PieceState piece, int x, int y, int squareSize) {
        float centerX = x + squareSize / 2.0f;
        float centerY = y + squareSize / 2.0f;
        float radius = squareSize * 0.4f;

        int pieceColor = piece.owner == 1 ? 0xFFDC143C : 0xFF1E1E1E; // Crimson / Dark gray

        // Outer circle (shadow)
        nvgBeginPath(nvg);
        nvgCircle(nvg, centerX + 2, centerY + 2, radius);
        nvgFillColor(nvg, nvgRGBA((byte) 0, (byte) 0, (byte) 0, (byte) 80));
        nvgFill(nvg);

        // Main circle
        nvgBeginPath(nvg);
        nvgCircle(nvg, centerX, centerY, radius);
        nvgFillColor(nvg, nvgColor(pieceColor));
        nvgFill(nvg);

        // Highlight
        nvgBeginPath(nvg);
        nvgCircle(nvg, centerX - radius * 0.3f, centerY - radius * 0.3f, radius * 0.3f);
        nvgFillColor(nvg, nvgRGBA((byte) 255, (byte) 255, (byte) 255, (byte) 60));
        nvgFill(nvg);

        // Border
        nvgBeginPath(nvg);
        nvgCircle(nvg, centerX, centerY, radius);
        nvgStrokeColor(nvg, nvgRGBA((byte) 0, (byte) 0, (byte) 0, (byte) 200));
        nvgStrokeWidth(nvg, 2);
        nvgStroke(nvg);

        // King crown
        if (piece.isKing) {
            drawCrown(nvg, centerX, centerY, radius * 0.5f);
        }
    }

    private void drawCrown(long nvg, float centerX, float centerY, float size) {
        float halfSize = size / 2;
        float topY = centerY - halfSize;
        float bottomY = centerY + halfSize * 0.5f;

        nvgBeginPath(nvg);
        nvgMoveTo(nvg, centerX - size, bottomY);
        nvgLineTo(nvg, centerX - size * 0.7f, topY);
        nvgLineTo(nvg, centerX - size * 0.35f, bottomY * 0.8f + topY * 0.2f);
        nvgLineTo(nvg, centerX, topY - halfSize * 0.3f);
        nvgLineTo(nvg, centerX + size * 0.35f, bottomY * 0.8f + topY * 0.2f);
        nvgLineTo(nvg, centerX + size * 0.7f, topY);
        nvgLineTo(nvg, centerX + size, bottomY);
        nvgClosePath(nvg);

        nvgFillColor(nvg, nvgRGBA((byte) 255, (byte) 215, (byte) 0, (byte) 255)); // Gold
        nvgFill(nvg);
        nvgStrokeColor(nvg, nvgRGBA((byte) 139, (byte) 90, (byte) 0, (byte) 255));
        nvgStrokeWidth(nvg, 1.5f);
        nvgStroke(nvg);
    }

    @Override
    public boolean onMouseClick(int mouseX, int mouseY, int button, int action) {
        // First check if children handle the click
        if (super.onMouseClick(mouseX, mouseY, button, action)) {
            return true;
        }

        // Handle board clicks
        if (action == 1 && button == 0) { // Left click press
            handleBoardClick(mouseX, mouseY);
            return true;
        }

        return false;
    }

    private void handleBoardClick(int mouseX, int mouseY) {
        if (serverGameOver) {
            return;
        }

        BoardPosition clickedPos = screenToBoard(mouseX, mouseY);
        if (clickedPos == null) {
            clearSelection();
            return;
        }

        // If multi-jump in progress, can only continue with that piece
        if (mustContinueRow >= 0 && mustContinueCol >= 0) {
            if (selectedPosition == null ||
                    selectedPosition.row() != mustContinueRow ||
                    selectedPosition.col() != mustContinueCol) {
                selectPiece(new BoardPosition(mustContinueRow, mustContinueCol));
            }
            tryMove(clickedPos);
            return;
        }

        // Check if clicking on a valid move target
        if (selectedPosition != null) {
            Move targetMove = validMoves.stream()
                    .filter(m -> m.getTo().equals(clickedPos))
                    .findFirst()
                    .orElse(null);

            if (targetMove != null) {
                executeMove(targetMove);
                return;
            }
        }

        // Check if clicking on own piece
        PieceState piece = getPieceAt(clickedPos.row(), clickedPos.col());
        if (piece != null && piece.owner == serverCurrentPlayer) {
            selectPiece(clickedPos);
        } else {
            clearSelection();
        }
    }

    private BoardPosition screenToBoard(int screenX, int screenY) {
        int squareSize = renderer.getSquareSize();
        int originX = getX() + BOARD_MARGIN;
        int originY = getY() + TITLE_BAR_HEIGHT + STATUS_HEIGHT;

        int col = (screenX - originX) / squareSize;
        int row = (screenY - originY) / squareSize;

        if (row >= 0 && row < CheckersModuleFactory.BOARD_SIZE &&
                col >= 0 && col < CheckersModuleFactory.BOARD_SIZE) {
            return new BoardPosition(row, col);
        }
        return null;
    }

    private PieceState getPieceAt(int row, int col) {
        for (PieceState piece : serverPieces) {
            if (piece.row == row && piece.col == col) {
                return piece;
            }
        }
        return null;
    }

    private void selectPiece(BoardPosition position) {
        selectedPosition = position;
        validMoves = calculateValidMoves(position);

        if (validMoves.isEmpty()) {
            statusLabel.setText("No valid moves for this piece");
        } else {
            statusLabel.setText("Select destination (" + validMoves.size() + " moves)");
        }
    }

    private List<Move> calculateValidMoves(BoardPosition position) {
        // Use local game logic to calculate valid moves for preview
        // The server will validate the actual move
        PieceState piece = getPieceAt(position.row(), position.col());
        if (piece == null || piece.owner != serverCurrentPlayer) {
            return List.of();
        }

        List<Move> moves = new ArrayList<>();
        int forward = (piece.owner == 1) ? 1 : -1;

        // Check if any capture is available for this player
        boolean hasCapture = hasAnyCapture(piece.owner);

        // Forward moves
        addMoveIfValid(moves, position, forward, -1, piece.owner, piece.isKing, hasCapture);
        addMoveIfValid(moves, position, forward, 1, piece.owner, piece.isKing, hasCapture);

        // Backward moves (kings only)
        if (piece.isKing) {
            addMoveIfValid(moves, position, -forward, -1, piece.owner, piece.isKing, hasCapture);
            addMoveIfValid(moves, position, -forward, 1, piece.owner, piece.isKing, hasCapture);
        }

        return moves;
    }

    private boolean hasAnyCapture(int owner) {
        for (PieceState piece : serverPieces) {
            if (piece.owner == owner) {
                if (hasCapture(piece.row, piece.col, piece.owner, piece.isKing)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasCapture(int row, int col, int owner, boolean isKing) {
        int forward = (owner == 1) ? 1 : -1;

        if (canCapture(row, col, forward, -1, owner)) return true;
        if (canCapture(row, col, forward, 1, owner)) return true;
        if (isKing) {
            if (canCapture(row, col, -forward, -1, owner)) return true;
            if (canCapture(row, col, -forward, 1, owner)) return true;
        }
        return false;
    }

    private boolean canCapture(int row, int col, int dRow, int dCol, int owner) {
        int enemyRow = row + dRow;
        int enemyCol = col + dCol;
        int landingRow = row + dRow * 2;
        int landingCol = col + dCol * 2;

        if (landingRow < 0 || landingRow >= CheckersModuleFactory.BOARD_SIZE ||
                landingCol < 0 || landingCol >= CheckersModuleFactory.BOARD_SIZE) {
            return false;
        }

        PieceState enemy = getPieceAt(enemyRow, enemyCol);
        if (enemy == null || enemy.owner == owner) {
            return false;
        }

        return getPieceAt(landingRow, landingCol) == null;
    }

    private void addMoveIfValid(List<Move> moves, BoardPosition from, int dRow, int dCol,
                                 int owner, boolean isKing, boolean mustCapture) {
        int toRow = from.row() + dRow;
        int toCol = from.col() + dCol;

        // Check capture
        int landingRow = from.row() + dRow * 2;
        int landingCol = from.col() + dCol * 2;

        if (landingRow >= 0 && landingRow < CheckersModuleFactory.BOARD_SIZE &&
                landingCol >= 0 && landingCol < CheckersModuleFactory.BOARD_SIZE) {

            PieceState enemy = getPieceAt(toRow, toCol);
            if (enemy != null && enemy.owner != owner && getPieceAt(landingRow, landingCol) == null) {
                moves.add(new Move(from, new BoardPosition(landingRow, landingCol),
                        List.of(new BoardPosition(toRow, toCol)), Move.MoveType.CAPTURE));
                return;
            }
        }

        // Simple move (only if no captures available)
        if (!mustCapture && toRow >= 0 && toRow < CheckersModuleFactory.BOARD_SIZE &&
                toCol >= 0 && toCol < CheckersModuleFactory.BOARD_SIZE) {
            if (getPieceAt(toRow, toCol) == null) {
                moves.add(new Move(from, new BoardPosition(toRow, toCol)));
            }
        }
    }

    private void clearSelection() {
        selectedPosition = null;
        validMoves = List.of();
        statusLabel.setText("Click a piece to move");
    }

    private void tryMove(BoardPosition target) {
        Move move = validMoves.stream()
                .filter(m -> m.getTo().equals(target))
                .findFirst()
                .orElse(null);

        if (move != null) {
            executeMove(move);
        } else {
            statusLabel.setText("Invalid move - must capture if possible");
        }
    }

    private void executeMove(Move move) {
        // Send move command to server
        if (commandAdapter != null) {
            sendMoveCommand(move);
            statusLabel.setText("Move sent to server...");
        } else {
            // Local play fallback
            localGame.executeMove(move);
            syncLocalGameWithServer();
        }

        clearSelection();
    }

    private void sendMoveCommand(Move move) {
        try {
            commandAdapter.submitCommand(matchId, "CheckersMove", 0, Map.of(
                    "matchId", matchId,
                    "fromRow", (long) move.getFrom().row(),
                    "fromCol", (long) move.getFrom().col(),
                    "toRow", (long) move.getTo().row(),
                    "toCol", (long) move.getTo().col()
            ));
        } catch (IOException e) {
            log.error("Failed to send move command: {}", e.getMessage());
            statusLabel.setText("Error: " + e.getMessage());
        }
    }

    private void updateTurnLabel() {
        if (serverGameOver) {
            turnLabel.setText("Game Over");
            turnLabel.setTextColor(GLColour.TEXT_SECONDARY);
        } else {
            boolean isPlayer1 = serverCurrentPlayer == 1;
            turnLabel.setText("Turn: " + (isPlayer1 ? "Player 1" : "Player 2"));
            turnLabel.setTextColor(isPlayer1 ? GLColour.RED : GLColour.TEXT_PRIMARY);
        }
    }

    private void resetGame() {
        // Send reset command to backend
        if (commandAdapter != null) {
            try {
                commandAdapter.submitCommand(matchId, "CheckersReset", 0, Map.of(
                        "matchId", matchId
                ));
                statusLabel.setText("Reset command sent");
            } catch (IOException e) {
                log.error("Failed to send reset command: {}", e.getMessage());
                statusLabel.setText("Error: " + e.getMessage());
            }
        } else {
            localGame.reset();
            syncLocalGameWithServer();
        }

        clearSelection();
    }

    private void startNewGame() {
        // Send start game command to backend
        if (commandAdapter != null) {
            try {
                commandAdapter.submitCommand(matchId, "CheckersStartGame", 0, Map.of(
                        "matchId", matchId
                ));
                statusLabel.setText("New game started!");
            } catch (IOException e) {
                log.error("Failed to send start game command: {}", e.getMessage());
                statusLabel.setText("Error: " + e.getMessage());
            }
        } else {
            localGame.reset();
            syncLocalGameWithServer();
            statusLabel.setText("New game started!");
        }

        clearSelection();
    }

    private void drawGameOverOverlay(long nvg) {
        float centerX = getX() + getWidth() / 2.0f;
        float centerY = getY() + getHeight() / 2.0f;

        // Semi-transparent overlay
        nvgBeginPath(nvg);
        nvgRect(nvg, getX(), getY() + TITLE_BAR_HEIGHT,
                getWidth(), getHeight() - TITLE_BAR_HEIGHT);
        nvgFillColor(nvg, nvgRGBA((byte) 0, (byte) 0, (byte) 0, (byte) 150));
        nvgFill(nvg);

        // Winner text
        String winnerText;
        if (serverWinner == 1) {
            winnerText = "Player 1 (Red) Wins!";
        } else if (serverWinner == 2) {
            winnerText = "Player 2 (Black) Wins!";
        } else {
            winnerText = "Game Over!";
        }

        nvgFontSize(nvg, 36.0f);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        nvgFillColor(nvg, nvgRGBA((byte) 255, (byte) 255, (byte) 255, (byte) 255));
        nvgText(nvg, centerX, centerY - 20, winnerText);

        nvgFontSize(nvg, 18.0f);
        nvgText(nvg, centerX, centerY + 20, "Click 'New Game' to play again");
    }

    public void dispose() {
        if (resourceLoader instanceof AutoCloseable) {
            try {
                ((AutoCloseable) resourceLoader).close();
            } catch (Exception e) {
                log.error("Error closing resource loader: {}", e.getMessage());
            }
        }
        renderer.dispose();
    }

    /**
     * Get the local game for testing.
     */
    public CheckersGame getLocalGame() {
        return localGame;
    }

    /**
     * Get the renderer for testing.
     */
    public CheckersRenderer getRenderer() {
        return renderer;
    }

    // Helper to create NanoVG color from packed ARGB int
    private org.lwjgl.nanovg.NVGColor nvgColor(int packedColor) {
        org.lwjgl.nanovg.NVGColor color = org.lwjgl.nanovg.NVGColor.create();
        color.r(((packedColor >> 16) & 0xFF) / 255.0f);
        color.g(((packedColor >> 8) & 0xFF) / 255.0f);
        color.b((packedColor & 0xFF) / 255.0f);
        color.a(((packedColor >> 24) & 0xFF) / 255.0f);
        return color;
    }

    private org.lwjgl.nanovg.NVGColor nvgRGBA(byte r, byte g, byte b, byte a) {
        return org.lwjgl.nanovg.NVGColor.create()
                .r((r & 0xFF) / 255.0f)
                .g((g & 0xFF) / 255.0f)
                .b((b & 0xFF) / 255.0f)
                .a((a & 0xFF) / 255.0f);
    }

    /**
     * Internal record for piece state from server.
     */
    private record PieceState(long id, int row, int col, int owner, boolean isKing) {}
}
