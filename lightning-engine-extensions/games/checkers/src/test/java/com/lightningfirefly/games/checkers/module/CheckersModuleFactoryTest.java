package com.lightningfirefly.games.checkers.module;

import com.lightningfirefly.engine.core.store.BaseComponent;
import com.lightningfirefly.engine.core.store.EntityComponentStore;
import com.lightningfirefly.engine.core.system.EngineSystem;
import com.lightningfirefly.engine.ext.module.EngineModule;
import com.lightningfirefly.engine.ext.module.Injector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CheckersModuleFactory}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CheckersModuleFactory")
class CheckersModuleFactoryTest {

    @Mock
    private Injector injector;

    @Mock
    private EntityComponentStore store;

    private CheckersModuleFactory factory;

    @BeforeEach
    void setUp() {
        factory = new CheckersModuleFactory();
    }

    @Nested
    @DisplayName("Module Creation")
    class ModuleCreation {

        @Test
        @DisplayName("should create module")
        void shouldCreateModule() {
            EngineModule module = factory.create(injector);

            assertThat(module).isNotNull();
            assertThat(module.getName()).isEqualTo("CheckersModule");
        }

        @Test
        @DisplayName("should return all components")
        void shouldReturnAllComponents() {
            EngineModule module = factory.create(injector);
            List<BaseComponent> components = module.createComponents();

            assertThat(components).isNotEmpty();
            assertThat(components).contains(
                    CheckersModuleFactory.CHECKER_ROW,
                    CheckersModuleFactory.CHECKER_COL,
                    CheckersModuleFactory.CHECKER_OWNER,
                    CheckersModuleFactory.CHECKER_IS_KING,
                    CheckersModuleFactory.CHECKER_IS_CAPTURED,
                    CheckersModuleFactory.CHECKER_PIECE_ID,
                    CheckersModuleFactory.GAME_CURRENT_PLAYER,
                    CheckersModuleFactory.GAME_IS_OVER,
                    CheckersModuleFactory.GAME_WINNER,
                    CheckersModuleFactory.GAME_MATCH_ID,
                    CheckersModuleFactory.CHECKERS_MODULE
            );
        }

        @Test
        @DisplayName("should return flag component")
        void shouldReturnFlagComponent() {
            EngineModule module = factory.create(injector);
            BaseComponent flag = module.createFlagComponent();

            assertThat(flag).isEqualTo(CheckersModuleFactory.CHECKERS_MODULE);
        }

        @Test
        @DisplayName("should create systems")
        void shouldCreateSystems() {
            EngineModule module = factory.create(injector);
            List<EngineSystem> systems = module.createSystems();

            assertThat(systems).hasSize(2);
        }

        @Test
        @DisplayName("should create commands")
        void shouldCreateCommands() {
            EngineModule module = factory.create(injector);

            assertThat(module.createCommands()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("Component Constants")
    class ComponentConstants {

        @Test
        @DisplayName("piece components should be defined")
        void pieceComponentsShouldBeDefined() {
            assertThat(CheckersModuleFactory.PIECE_COMPONENTS)
                    .hasSize(7)
                    .contains(
                            CheckersModuleFactory.CHECKER_ROW,
                            CheckersModuleFactory.CHECKER_COL,
                            CheckersModuleFactory.CHECKER_OWNER,
                            CheckersModuleFactory.CHECKER_IS_KING,
                            CheckersModuleFactory.CHECKER_IS_CAPTURED,
                            CheckersModuleFactory.CHECKER_PIECE_ID,
                            CheckersModuleFactory.CHECKERS_MODULE
                    );
        }

        @Test
        @DisplayName("game state components should be defined")
        void gameStateComponentsShouldBeDefined() {
            assertThat(CheckersModuleFactory.GAME_STATE_COMPONENTS)
                    .hasSize(7)
                    .contains(
                            CheckersModuleFactory.GAME_CURRENT_PLAYER,
                            CheckersModuleFactory.GAME_IS_OVER,
                            CheckersModuleFactory.GAME_WINNER,
                            CheckersModuleFactory.GAME_MATCH_ID,
                            CheckersModuleFactory.GAME_MUST_CONTINUE_FROM_ROW,
                            CheckersModuleFactory.GAME_MUST_CONTINUE_FROM_COL,
                            CheckersModuleFactory.CHECKERS_MODULE
                    );
        }

        @Test
        @DisplayName("all components should include both piece and game state")
        void allComponentsShouldIncludeBoth() {
            List<BaseComponent> all = CheckersModuleFactory.ALL_COMPONENTS;

            assertThat(all).containsAll(CheckersModuleFactory.PIECE_COMPONENTS);
            assertThat(all).containsAll(CheckersModuleFactory.GAME_STATE_COMPONENTS);
        }

        @Test
        @DisplayName("components should have unique IDs")
        void componentsShouldHaveUniqueIds() {
            List<BaseComponent> all = CheckersModuleFactory.ALL_COMPONENTS;
            long uniqueIds = all.stream()
                    .map(BaseComponent::id)
                    .distinct()
                    .count();

            assertThat(uniqueIds).isEqualTo(all.size());
        }

        @Test
        @DisplayName("board size should be 8")
        void boardSizeShouldBe8() {
            assertThat(CheckersModuleFactory.BOARD_SIZE).isEqualTo(8);
        }

        @Test
        @DisplayName("pieces per player should be 12")
        void piecesPerPlayerShouldBe12() {
            assertThat(CheckersModuleFactory.PIECES_PER_PLAYER).isEqualTo(12);
        }
    }

    @Nested
    @DisplayName("Commands")
    class Commands {

        private CheckersModuleFactory.CheckersModule module;

        @BeforeEach
        void setUp() {
            module = (CheckersModuleFactory.CheckersModule) factory.create(injector);
        }

        @Test
        @DisplayName("commands should have correct names")
        void commandsShouldHaveCorrectNames() {
            var commands = module.createCommands();
            var commandNames = commands.stream()
                    .map(c -> c.getName())
                    .toList();

            assertThat(commandNames).containsExactlyInAnyOrder(
                    "CheckersStartGame",
                    "CheckersMove",
                    "CheckersReset"
            );
        }
    }

    @Nested
    @DisplayName("Game Entity Access")
    class GameEntityAccess {

        private CheckersModuleFactory.CheckersModule module;

        @BeforeEach
        void setUp() {
            module = (CheckersModuleFactory.CheckersModule) factory.create(injector);
        }

        @Test
        @DisplayName("getGameEntityId should return null for unknown match")
        void getGameEntityIdShouldReturnNullForUnknownMatch() {
            Long entityId = module.getGameEntityId(999L);

            assertThat(entityId).isNull();
        }
    }
}
