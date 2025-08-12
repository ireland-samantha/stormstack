package com.lightningfirefly.games.checkers.integration;

import com.lightningfirefly.engine.core.command.EngineCommand;
import com.lightningfirefly.engine.core.store.EntityComponentStore;
import com.lightningfirefly.engine.core.system.EngineSystem;
import com.lightningfirefly.engine.ext.module.Injector;
import com.lightningfirefly.engine.internal.core.store.ArrayEntityComponentStore;
import com.lightningfirefly.engine.internal.core.store.EcsProperties;
import com.lightningfirefly.games.checkers.module.CheckersModuleFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for the Checkers game with ECS storage.
 *
 * <p>These tests verify the full game flow:
 * <ul>
 *   <li>Game initialization creates entities in ECS</li>
 *   <li>Move commands update ECS state correctly</li>
 *   <li>Captures are handled properly</li>
 *   <li>Turn switching works</li>
 *   <li>Game over detection works</li>
 * </ul>
 */
@DisplayName("Checkers ECS Integration")
class CheckersEcsIntegrationTest {

    private EntityComponentStore store;
    private CheckersModuleFactory.CheckersModule module;
    private List<EngineSystem> systems;
    private Map<String, EngineCommand> commands;

    private static final long TEST_MATCH_ID = 1L;

    @BeforeEach
    void setUp() {
        // Create in-memory ECS store
        store = new ArrayEntityComponentStore(new EcsProperties(1000, 100));

        // Create module with real store
        Injector injector = new TestInjector(store);
        CheckersModuleFactory factory = new CheckersModuleFactory();
        module = (CheckersModuleFactory.CheckersModule) factory.create(injector);

        // Get systems and commands
        systems = module.createSystems();
        commands = new HashMap<>();
        for (EngineCommand cmd : module.createCommands()) {
            commands.put(cmd.getName(), cmd);
        }
    }

    private void runSystems() {
        for (EngineSystem system : systems) {
            system.updateEntities();
        }
    }

    private void executeCommand(String name, Map<String, Object> payload) {
        EngineCommand cmd = commands.get(name);
        assertThat(cmd).isNotNull();
        cmd.executeCommand(new TestCommandPayload(payload));
    }

    @Nested
    @DisplayName("Game Initialization")
    class GameInitialization {

        @Test
        @DisplayName("should create game entities on start")
        void shouldCreateGameEntitiesOnStart() {
            // Start game
            executeCommand("CheckersStartGame", Map.of("matchId", TEST_MATCH_ID));
            runSystems();

            // Verify game entity was created
            Long gameEntityId = module.getGameEntityId(TEST_MATCH_ID);
            assertThat(gameEntityId).isNotNull();

            // Verify game state
            long currentPlayer = store.getComponent(gameEntityId, CheckersModuleFactory.GAME_CURRENT_PLAYER);
            long isGameOver = store.getComponent(gameEntityId, CheckersModuleFactory.GAME_IS_OVER);

            assertThat(currentPlayer).isEqualTo(1); // Player 1 starts
            assertThat(isGameOver).isEqualTo(0); // Game not over
        }

        @Test
        @DisplayName("should create 24 piece entities")
        void shouldCreate24PieceEntities() {
            executeCommand("CheckersStartGame", Map.of("matchId", TEST_MATCH_ID));
            runSystems();

            // Count pieces in ECS
            Set<Long> pieceEntities = store.getEntitiesWithComponents(
                    CheckersModuleFactory.CHECKER_ROW,
                    CheckersModuleFactory.CHECKER_COL,
                    CheckersModuleFactory.CHECKER_OWNER
            );

            // Should have 24 pieces + 1 game state entity = at least 24 piece entities
            assertThat(pieceEntities.size()).isGreaterThanOrEqualTo(24);
        }

        @Test
        @DisplayName("should place pieces on correct squares")
        void shouldPlacePiecesOnCorrectSquares() {
            executeCommand("CheckersStartGame", Map.of("matchId", TEST_MATCH_ID));
            runSystems();

            Set<Long> entities = store.getEntitiesWithComponents(
                    CheckersModuleFactory.CHECKER_ROW,
                    CheckersModuleFactory.CHECKER_COL
            );

            int player1Count = 0;
            int player2Count = 0;

            for (Long entityId : entities) {
                if (!store.hasComponent(entityId, CheckersModuleFactory.CHECKER_OWNER)) {
                    continue; // Skip game state entity
                }

                long row = store.getComponent(entityId, CheckersModuleFactory.CHECKER_ROW);
                long col = store.getComponent(entityId, CheckersModuleFactory.CHECKER_COL);
                long owner = store.getComponent(entityId, CheckersModuleFactory.CHECKER_OWNER);

                // Verify pieces are on dark squares
                assertThat((row + col) % 2).isEqualTo(1);

                if (owner == 1) {
                    // Player 1 in top 3 rows
                    assertThat(row).isLessThan(3);
                    player1Count++;
                } else if (owner == 2) {
                    // Player 2 in bottom 3 rows
                    assertThat(row).isGreaterThanOrEqualTo(5);
                    player2Count++;
                }
            }

            assertThat(player1Count).isEqualTo(12);
            assertThat(player2Count).isEqualTo(12);
        }
    }

    @Nested
    @DisplayName("Move Execution")
    class MoveExecution {

        @BeforeEach
        void startGame() {
            executeCommand("CheckersStartGame", Map.of("matchId", TEST_MATCH_ID));
            runSystems();
        }

        @Test
        @DisplayName("should execute simple move")
        void shouldExecuteSimpleMove() {
            // Player 1 moves piece from (2,1) to (3,2)
            executeCommand("CheckersMove", Map.of(
                    "matchId", TEST_MATCH_ID,
                    "fromRow", 2L,
                    "fromCol", 1L,
                    "toRow", 3L,
                    "toCol", 2L
            ));
            runSystems();

            // Verify piece moved
            Long pieceAtOldPos = findPieceAt(2, 1);
            Long pieceAtNewPos = findPieceAt(3, 2);

            assertThat(pieceAtOldPos).isNull();
            assertThat(pieceAtNewPos).isNotNull();
        }

        @Test
        @DisplayName("should switch turns after move")
        void shouldSwitchTurnsAfterMove() {
            Long gameEntityId = module.getGameEntityId(TEST_MATCH_ID);

            // Verify Player 1's turn
            assertThat(store.getComponent(gameEntityId, CheckersModuleFactory.GAME_CURRENT_PLAYER))
                    .isEqualTo(1);

            // Make a move
            executeCommand("CheckersMove", Map.of(
                    "matchId", TEST_MATCH_ID,
                    "fromRow", 2L,
                    "fromCol", 1L,
                    "toRow", 3L,
                    "toCol", 2L
            ));
            runSystems();

            // Verify Player 2's turn
            assertThat(store.getComponent(gameEntityId, CheckersModuleFactory.GAME_CURRENT_PLAYER))
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("should reject invalid move")
        void shouldRejectInvalidMove() {
            Long gameEntityId = module.getGameEntityId(TEST_MATCH_ID);

            // Try to move a piece that doesn't exist
            executeCommand("CheckersMove", Map.of(
                    "matchId", TEST_MATCH_ID,
                    "fromRow", 4L,
                    "fromCol", 4L,
                    "toRow", 5L,
                    "toCol", 5L
            ));
            runSystems();

            // Turn should still be Player 1
            assertThat(store.getComponent(gameEntityId, CheckersModuleFactory.GAME_CURRENT_PLAYER))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("should reject moving opponent's piece")
        void shouldRejectMovingOpponentPiece() {
            Long gameEntityId = module.getGameEntityId(TEST_MATCH_ID);

            // Try to move Player 2's piece on Player 1's turn
            executeCommand("CheckersMove", Map.of(
                    "matchId", TEST_MATCH_ID,
                    "fromRow", 5L,
                    "fromCol", 2L,
                    "toRow", 4L,
                    "toCol", 3L
            ));
            runSystems();

            // Turn should still be Player 1
            assertThat(store.getComponent(gameEntityId, CheckersModuleFactory.GAME_CURRENT_PLAYER))
                    .isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Captures")
    class Captures {

        @BeforeEach
        void startGame() {
            executeCommand("CheckersStartGame", Map.of("matchId", TEST_MATCH_ID));
            runSystems();
        }

        @Test
        @DisplayName("should execute capture and mark piece as captured")
        void shouldExecuteCaptureAndMarkPieceCaptured() {
            // Setup: Move pieces to create capture opportunity
            // Player 1: (2,3) -> (3,4)
            executeCommand("CheckersMove", Map.of(
                    "matchId", TEST_MATCH_ID,
                    "fromRow", 2L, "fromCol", 3L,
                    "toRow", 3L, "toCol", 4L
            ));
            runSystems();

            // Player 2: (5,4) -> (4,3)
            executeCommand("CheckersMove", Map.of(
                    "matchId", TEST_MATCH_ID,
                    "fromRow", 5L, "fromCol", 4L,
                    "toRow", 4L, "toCol", 3L
            ));
            runSystems();

            // Player 1 captures: (3,4) -> (5,2) capturing (4,3)
            executeCommand("CheckersMove", Map.of(
                    "matchId", TEST_MATCH_ID,
                    "fromRow", 3L, "fromCol", 4L,
                    "toRow", 5L, "toCol", 2L
            ));
            runSystems();

            // Verify capture - piece at (4,3) should be captured
            Long capturedPiece = findPieceAt(4, 3);
            assertThat(capturedPiece).isNull(); // Not found at position (marked captured)

            // Verify capturing piece moved
            Long capturingPiece = findPieceAt(5, 2);
            assertThat(capturingPiece).isNotNull();
        }
    }

    @Nested
    @DisplayName("Game Reset")
    class GameReset {

        @BeforeEach
        void startGame() {
            executeCommand("CheckersStartGame", Map.of("matchId", TEST_MATCH_ID));
            runSystems();
        }

        @Test
        @DisplayName("should reset game state")
        void shouldResetGameState() {
            // Make some moves
            executeCommand("CheckersMove", Map.of(
                    "matchId", TEST_MATCH_ID,
                    "fromRow", 2L, "fromCol", 1L,
                    "toRow", 3L, "toCol", 2L
            ));
            runSystems();

            // Reset
            executeCommand("CheckersReset", Map.of("matchId", TEST_MATCH_ID));
            runSystems();

            // Verify reset
            Long gameEntityId = module.getGameEntityId(TEST_MATCH_ID);
            assertThat(gameEntityId).isNotNull();

            long currentPlayer = store.getComponent(gameEntityId, CheckersModuleFactory.GAME_CURRENT_PLAYER);
            assertThat(currentPlayer).isEqualTo(1);

            // Verify pieces are back in initial positions
            Long pieceAtInitial = findPieceAt(2, 1);
            assertThat(pieceAtInitial).isNotNull();
        }
    }

    // Helper methods

    private Long findPieceAt(int row, int col) {
        Set<Long> entities = store.getEntitiesWithComponents(
                CheckersModuleFactory.CHECKER_ROW,
                CheckersModuleFactory.CHECKER_COL
        );

        for (Long entityId : entities) {
            if (!store.hasComponent(entityId, CheckersModuleFactory.CHECKER_OWNER)) {
                continue;
            }

            long entityRow = store.getComponent(entityId, CheckersModuleFactory.CHECKER_ROW);
            long entityCol = store.getComponent(entityId, CheckersModuleFactory.CHECKER_COL);
            long isCaptured = store.getComponent(entityId, CheckersModuleFactory.CHECKER_IS_CAPTURED);

            if (entityRow == row && entityCol == col && isCaptured == 0) {
                return entityId;
            }
        }
        return null;
    }

    // Test doubles

    private record TestInjector(EntityComponentStore store) implements Injector {
        @Override
        public EntityComponentStore getStoreRequired() {
            return store;
        }
    }

    private record TestCommandPayload(Map<String, Object> payload)
            implements com.lightningfirefly.engine.core.command.CommandPayload {
        @Override
        public Map<String, Object> getPayload() {
            return payload;
        }
    }
}
