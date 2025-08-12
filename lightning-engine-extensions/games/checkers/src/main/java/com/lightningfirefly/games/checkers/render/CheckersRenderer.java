package com.lightningfirefly.games.checkers.render;

import com.lightningfirefly.games.checkers.model.CheckerPiece;
import com.lightningfirefly.games.checkers.model.CheckersGame;
import com.lightningfirefly.games.common.board.BoardGame;
import com.lightningfirefly.games.common.board.BoardPosition;
import com.lightningfirefly.games.common.board.Move;
import com.lightningfirefly.games.common.board.Player;
import com.lightningfirefly.games.common.render.BoardGameRenderer;
import com.lightningfirefly.games.common.resource.GameResource;
import com.lightningfirefly.games.common.resource.LazyResourceLoader;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Renderer for checkers using NanoVG.
 *
 * <p>Uses lazy resource loading for piece textures from the backend API.
 */
@Slf4j
public class CheckersRenderer implements BoardGameRenderer<CheckerPiece> {

    // Colors
    private static final int DARK_SQUARE_COLOR = nvgRGBA(139, 90, 43, 255);
    private static final int LIGHT_SQUARE_COLOR = nvgRGBA(222, 184, 135, 255);
    private static final int HIGHLIGHT_COLOR = nvgRGBA(255, 255, 0, 128);
    private static final int VALID_MOVE_COLOR = nvgRGBA(0, 255, 0, 100);
    private static final int SELECTION_COLOR = nvgRGBA(0, 100, 255, 150);
    private static final int RED_PIECE_COLOR = nvgRGBA(220, 20, 60, 255);
    private static final int BLACK_PIECE_COLOR = nvgRGBA(30, 30, 30, 255);
    private static final int KING_CROWN_COLOR = nvgRGBA(255, 215, 0, 255);

    private BoardGame<CheckerPiece> game;
    private int originX = 0;
    private int originY = 0;
    private int squareSize = 60;

    private BoardPosition selectedSquare;
    private List<Move> highlightedMoves = new ArrayList<>();

    // Lazy resource loader for textures
    private final LazyResourceLoader resourceLoader;
    private final Map<String, Integer> textureHandles = new ConcurrentHashMap<>();
    private boolean texturesRequested = false;

    // Resource names
    private static final String[] TEXTURE_RESOURCES = {
            "red-checker.png",
            "black-checker.png",
            "red-king.png",
            "black-king.png"
    };

    /**
     * Create renderer without resource loading (use simple shapes).
     */
    public CheckersRenderer() {
        this(null);
    }

    /**
     * Create renderer with lazy resource loading.
     */
    public CheckersRenderer(LazyResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void initialize(BoardGame<CheckerPiece> game) {
        this.game = game;

        // Start preloading textures if resource loader is available
        if (resourceLoader != null && !texturesRequested) {
            texturesRequested = true;
            resourceLoader.preloadAll(TEXTURE_RESOURCES)
                    .thenRun(() -> log.info("Checkers textures preloaded"));
        }
    }

    @Override
    public void render(long nvg) {
        if (game == null || nvg == 0) return;

        // Draw board
        drawBoard(nvg);

        // Draw highlights
        drawHighlights(nvg);

        // Draw pieces
        drawPieces(nvg);

        // Draw selection
        drawSelection(nvg);
    }

    private void drawBoard(long nvg) {
        for (int row = 0; row < game.getHeight(); row++) {
            for (int col = 0; col < game.getWidth(); col++) {
                int x = originX + col * squareSize;
                int y = originY + row * squareSize;

                boolean isDark = (row + col) % 2 == 1;
                int color = isDark ? DARK_SQUARE_COLOR : LIGHT_SQUARE_COLOR;

                nvgBeginPath(nvg);
                nvgRect(nvg, x, y, squareSize, squareSize);
                nvgFillColor(nvg, nvgColor(color));
                nvgFill(nvg);
            }
        }

        // Draw board border
        nvgBeginPath(nvg);
        nvgRect(nvg, originX, originY,
                game.getWidth() * squareSize, game.getHeight() * squareSize);
        nvgStrokeColor(nvg, nvgColor(nvgRGBA(0, 0, 0, 255)));
        nvgStrokeWidth(nvg, 2);
        nvgStroke(nvg);
    }

    private void drawHighlights(long nvg) {
        for (Move move : highlightedMoves) {
            BoardPosition to = move.getTo();
            int x = originX + to.col() * squareSize;
            int y = originY + to.row() * squareSize;

            nvgBeginPath(nvg);
            nvgRect(nvg, x + 5, y + 5, squareSize - 10, squareSize - 10);
            nvgFillColor(nvg, nvgColor(VALID_MOVE_COLOR));
            nvgFill(nvg);

            // Capture indicator
            if (move.isCapture()) {
                nvgBeginPath(nvg);
                nvgCircle(nvg, x + squareSize / 2.0f, y + squareSize / 2.0f, squareSize / 4.0f);
                nvgStrokeColor(nvg, nvgColor(nvgRGBA(255, 0, 0, 200)));
                nvgStrokeWidth(nvg, 3);
                nvgStroke(nvg);
            }
        }
    }

    private void drawPieces(long nvg) {
        for (CheckerPiece piece : game.getAllPieces()) {
            if (piece.isCaptured()) continue;

            BoardPosition pos = piece.getPosition();
            int x = originX + pos.col() * squareSize;
            int y = originY + pos.row() * squareSize;

            drawPiece(nvg, piece, x, y);
        }
    }

    private void drawPiece(long nvg, CheckerPiece piece, int x, int y) {
        float centerX = x + squareSize / 2.0f;
        float centerY = y + squareSize / 2.0f;
        float radius = squareSize * 0.4f;

        // Try to use texture if available
        String textureName = piece.getTypeName() + ".png";
        Integer textureHandle = textureHandles.get(textureName);

        // For now, draw simple shapes (texture loading would require OpenGL context management)
        // The texture system can be integrated with GLTexture when running in a GL context

        // Draw piece base
        int pieceColor = piece.getOwner() == Player.PLAYER_ONE ? RED_PIECE_COLOR : BLACK_PIECE_COLOR;

        // Outer circle (shadow)
        nvgBeginPath(nvg);
        nvgCircle(nvg, centerX + 2, centerY + 2, radius);
        nvgFillColor(nvg, nvgColor(nvgRGBA(0, 0, 0, 80)));
        nvgFill(nvg);

        // Main circle
        nvgBeginPath(nvg);
        nvgCircle(nvg, centerX, centerY, radius);
        nvgFillColor(nvg, nvgColor(pieceColor));
        nvgFill(nvg);

        // Highlight
        nvgBeginPath(nvg);
        nvgCircle(nvg, centerX - radius * 0.3f, centerY - radius * 0.3f, radius * 0.3f);
        nvgFillColor(nvg, nvgColor(nvgRGBA(255, 255, 255, 60)));
        nvgFill(nvg);

        // Border
        nvgBeginPath(nvg);
        nvgCircle(nvg, centerX, centerY, radius);
        nvgStrokeColor(nvg, nvgColor(nvgRGBA(0, 0, 0, 200)));
        nvgStrokeWidth(nvg, 2);
        nvgStroke(nvg);

        // King crown
        if (piece.isKing()) {
            drawCrown(nvg, centerX, centerY, radius * 0.5f);
        }
    }

    private void drawCrown(long nvg, float centerX, float centerY, float size) {
        // Draw a simple crown shape
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

        nvgFillColor(nvg, nvgColor(KING_CROWN_COLOR));
        nvgFill(nvg);
        nvgStrokeColor(nvg, nvgColor(nvgRGBA(139, 90, 0, 255)));
        nvgStrokeWidth(nvg, 1.5f);
        nvgStroke(nvg);
    }

    private void drawSelection(long nvg) {
        if (selectedSquare == null) return;

        int x = originX + selectedSquare.col() * squareSize;
        int y = originY + selectedSquare.row() * squareSize;

        nvgBeginPath(nvg);
        nvgRect(nvg, x + 2, y + 2, squareSize - 4, squareSize - 4);
        nvgStrokeColor(nvg, nvgColor(SELECTION_COLOR));
        nvgStrokeWidth(nvg, 4);
        nvgStroke(nvg);
    }

    @Override
    public void highlightValidMoves(BoardPosition position, List<Move> validMoves) {
        this.highlightedMoves = new ArrayList<>(validMoves);
    }

    @Override
    public void clearHighlights() {
        this.highlightedMoves.clear();
    }

    @Override
    public void setSelectedSquare(BoardPosition position) {
        this.selectedSquare = position;
    }

    @Override
    public BoardPosition screenToBoard(int screenX, int screenY) {
        int col = (screenX - originX) / squareSize;
        int row = (screenY - originY) / squareSize;

        BoardPosition pos = new BoardPosition(row, col);
        if (pos.isWithinBounds(game.getWidth(), game.getHeight())) {
            return pos;
        }
        return null;
    }

    @Override
    public int[] boardToScreen(BoardPosition position) {
        return new int[]{
                originX + position.col() * squareSize + squareSize / 2,
                originY + position.row() * squareSize + squareSize / 2
        };
    }

    @Override
    public int getSquareSize() {
        return squareSize;
    }

    @Override
    public void setOrigin(int x, int y) {
        this.originX = x;
        this.originY = y;
    }

    @Override
    public void setSquareSize(int size) {
        this.squareSize = size;
    }

    @Override
    public void dispose() {
        // Clean up any loaded textures
        textureHandles.clear();
    }

    // Helper to create NanoVG color from packed int
    private org.lwjgl.nanovg.NVGColor nvgColor(int packedColor) {
        org.lwjgl.nanovg.NVGColor color = org.lwjgl.nanovg.NVGColor.create();
        color.r(((packedColor >> 16) & 0xFF) / 255.0f);
        color.g(((packedColor >> 8) & 0xFF) / 255.0f);
        color.b((packedColor & 0xFF) / 255.0f);
        color.a(((packedColor >> 24) & 0xFF) / 255.0f);
        return color;
    }

    private static int nvgRGBA(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
