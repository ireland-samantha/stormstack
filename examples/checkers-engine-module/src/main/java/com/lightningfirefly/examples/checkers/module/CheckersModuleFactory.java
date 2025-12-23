package com.lightningfirefly.examples.checkers.module;

import com.lightningfirefly.engine.core.command.CommandBuilder;
import com.lightningfirefly.engine.core.command.EngineCommand;
import com.lightningfirefly.engine.core.store.BaseComponent;
import com.lightningfirefly.engine.core.store.EntityComponentStore;
import com.lightningfirefly.engine.core.system.EngineSystem;
import com.lightningfirefly.engine.ext.module.EngineModule;
import com.lightningfirefly.engine.ext.module.ModuleContext;
import com.lightningfirefly.engine.ext.module.ModuleFactory;
import com.lightningfirefly.engine.util.IdGeneratorV2;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * ECS Module Factory for Checkers game.
 *
 * <p>This module provides the database layer for the checkers game:
 * <ul>
 *   <li>Components for board position, piece type, player ownership</li>
 *   <li>Commands to create/move/capture pieces</li>
 *   <li>Systems to validate and process game state</li>
 * </ul>
 *
 * <p>This module is packaged as a separate JAR and uploaded to the server.
 */
@Slf4j
public class CheckersModuleFactory implements ModuleFactory {

    // Component definitions
    public static final CheckersComponent BOARD_X = new CheckersComponent(
            IdGeneratorV2.newId(), "BOARD_X");
    public static final CheckersComponent BOARD_Y = new CheckersComponent(
            IdGeneratorV2.newId(), "BOARD_Y");
    public static final CheckersComponent PIECE_TYPE = new CheckersComponent(
            IdGeneratorV2.newId(), "PIECE_TYPE"); // 1 = normal, 2 = king
    public static final CheckersComponent PLAYER = new CheckersComponent(
            IdGeneratorV2.newId(), "PLAYER"); // 1 = red, 2 = black
    public static final CheckersComponent IS_CAPTURED = new CheckersComponent(
            IdGeneratorV2.newId(), "IS_CAPTURED"); // 1 = captured (should be removed)
    public static final CheckersComponent MODULE = new CheckersComponent(
            IdGeneratorV2.newId(), "checkers");

    public static final List<BaseComponent> ALL_COMPONENTS =
            List.of(BOARD_X, BOARD_Y, PIECE_TYPE, PLAYER, IS_CAPTURED, MODULE);

    public static final List<BaseComponent> PIECE_COMPONENTS =
            List.of(BOARD_X, BOARD_Y, PIECE_TYPE, PLAYER, IS_CAPTURED);

    // Piece type constants
    public static final int TYPE_NORMAL = 1;
    public static final int TYPE_KING = 2;

    // Player constants
    public static final int PLAYER_RED = 1;
    public static final int PLAYER_BLACK = 2;

    @Override
    public EngineModule create(ModuleContext context) {
        return new CheckersModule(context);
    }

    public static class CheckersComponent extends BaseComponent {
        public CheckersComponent(long id, String name) {
            super(id, name);
        }
    }

    public static class CheckersModule implements EngineModule {
        private final ModuleContext context;
        private final List<Long> capturedPieces = new ArrayList<>();

        public CheckersModule(ModuleContext context) {
            this.context = context;
        }

        @Override
        public List<EngineSystem> createSystems() {
            return List.of(createCaptureCleanupSystem());
        }

        @Override
        public List<EngineCommand> createCommands() {
            return List.of(
                    createPieceCommand(),
                    movePieceCommand(),
                    capturePieceCommand(),
                    promotePieceCommand()
            );
        }

        @Override
        public List<BaseComponent> createComponents() {
            return ALL_COMPONENTS;
        }

        @Override
        public BaseComponent createFlagComponent() {
            return MODULE;
        }

        @Override
        public String getName() {
            return "CheckersModule";
        }

        /**
         * Command to create a new checkers piece.
         *
         * <p>The entity is created with the GameMaster-provided entityId, and we attach
         * the core components (MATCH_ID, ENTITY_ID) plus our module's components.
         */
        private EngineCommand createPieceCommand() {
            return CommandBuilder
                    .newCommand()
                    .withExecution(payload -> {
                        Map<String, Object> data = payload.getPayload();

                        long entityId = extractLong(data, "entityId");
                        int boardX = extractInt(data, "boardX");
                        int boardY = extractInt(data, "boardY");
                        int player = extractInt(data, "player");
                        long matchId = extractLong(data, "matchId");

                        if (entityId == 0) {
                            log.warn("createPiece: missing entityId");
                            return;
                        }

                        EntityComponentStore store = context.getEntityComponentStore();

                        // Attach core components for snapshot filtering
                        store.attachComponent(entityId, com.lightningfirefly.engine.core.entity.CoreComponents.MATCH_ID, matchId);
                        store.attachComponent(entityId, com.lightningfirefly.engine.core.entity.CoreComponents.ENTITY_ID, entityId);

                        // Attach module-specific components
                        store.attachComponents(entityId, PIECE_COMPONENTS,
                                new float[]{boardX, boardY, TYPE_NORMAL, player, 0});
                        store.attachComponent(entityId, MODULE, 1.0f);

                        log.info("Created piece {} at ({},{}) for player {} in match {}",
                                entityId, boardX, boardY, player, matchId);
                    })
                    .withName("createPiece")
                    .withSchema(Map.of(
                            "entityId", Long.class,
                            "boardX", Integer.class,
                            "boardY", Integer.class,
                            "player", Integer.class,
                            "matchId", Long.class))
                    .build();
        }

        /**
         * Command to move a piece to a new board position.
         */
        private EngineCommand movePieceCommand() {
            return CommandBuilder
                    .newCommand()
                    .withExecution(payload -> {
                        Map<String, Object> data = payload.getPayload();

                        long entityId = extractLong(data, "entityId");
                        int toX = extractInt(data, "toX");
                        int toY = extractInt(data, "toY");

                        EntityComponentStore store = context.getEntityComponentStore();

                        // Update position
                        store.attachComponent(entityId, BOARD_X, toX);
                        store.attachComponent(entityId, BOARD_Y, toY);

                        log.info("Moved piece {} to ({},{})", entityId, toX, toY);
                    })
                    .withName("movePiece")
                    .withSchema(Map.of(
                            "entityId", Long.class,
                            "toX", Integer.class,
                            "toY", Integer.class))
                    .build();
        }

        /**
         * Command to capture (remove) a piece.
         */
        private EngineCommand capturePieceCommand() {
            return CommandBuilder
                    .newCommand()
                    .withExecution(payload -> {
                        Map<String, Object> data = payload.getPayload();
                        long entityId = extractLong(data, "entityId");

                        EntityComponentStore store = context.getEntityComponentStore();
                        store.attachComponent(entityId, IS_CAPTURED, 1);
                        capturedPieces.add(entityId);

                        log.info("Captured piece {}", entityId);
                    })
                    .withName("capturePiece")
                    .withSchema(Map.of("entityId", Long.class))
                    .build();
        }

        /**
         * Command to promote a piece to king.
         */
        private EngineCommand promotePieceCommand() {
            return CommandBuilder
                    .newCommand()
                    .withExecution(payload -> {
                        Map<String, Object> data = payload.getPayload();
                        long entityId = extractLong(data, "entityId");

                        EntityComponentStore store = context.getEntityComponentStore();
                        store.attachComponent(entityId, PIECE_TYPE, TYPE_KING);

                        log.info("Promoted piece {} to king", entityId);
                    })
                    .withName("promotePiece")
                    .withSchema(Map.of("entityId", Long.class))
                    .build();
        }

        /**
         * System to clean up captured pieces.
         */
        private EngineSystem createCaptureCleanupSystem() {
            return () -> {
                EntityComponentStore store = context.getEntityComponentStore();
                for (Long pieceId : capturedPieces) {
                    for (BaseComponent c : PIECE_COMPONENTS) {
                        store.removeComponent(pieceId, c);
                    }
                    store.removeComponent(pieceId, MODULE);
                    log.trace("Cleaned up captured piece {}", pieceId);
                }
                capturedPieces.clear();
            };
        }

        private long extractLong(Map<String, Object> data, String key) {
            Object value = data.get(key);
            if (value == null) return 0;
            if (value instanceof Number n) return n.longValue();
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        private int extractInt(Map<String, Object> data, String key) {
            Object value = data.get(key);
            if (value == null) return 0;
            if (value instanceof Number n) return n.intValue();
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }
}
