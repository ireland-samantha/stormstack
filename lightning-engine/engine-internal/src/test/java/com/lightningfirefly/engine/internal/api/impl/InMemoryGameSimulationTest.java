package com.lightningfirefly.engine.internal.api.impl;

import com.lightningfirefly.engine.internal.GameLoop;
import com.lightningfirefly.engine.core.command.EngineCommand;
import com.lightningfirefly.engine.core.command.CommandPayload;
import com.lightningfirefly.engine.core.command.CommandQueue;
import com.lightningfirefly.engine.core.exception.EntityNotFoundException;
import com.lightningfirefly.engine.core.match.Match;
import com.lightningfirefly.engine.core.match.MatchService;
import com.lightningfirefly.engine.core.match.Player;
import com.lightningfirefly.engine.core.match.PlayerMatch;
import com.lightningfirefly.engine.core.match.PlayerMatchService;
import com.lightningfirefly.engine.core.match.PlayerService;
import com.lightningfirefly.engine.internal.core.match.InMemoryGameSimulation;
import com.lightningfirefly.engine.internal.core.command.CommandResolver;
import com.lightningfirefly.engine.internal.core.snapshot.SnapshotProvider;
import com.lightningfirefly.engine.internal.ext.module.ModuleManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link InMemoryGameSimulation}.
 *
 * <p>Tests verify that GameSimulation correctly delegates to services.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InMemoryGameSimulation")
class InMemoryGameSimulationTest {

    @Mock
    private MatchService matchService;

    @Mock
    private PlayerService playerService;

    @Mock
    private PlayerMatchService playerMatchService;

    @Mock
    private ModuleManager moduleManager;

    @Mock
    private CommandResolver commandResolver;

    @Mock
    private CommandQueue commandQueue;

    @Mock
    private SnapshotProvider snapshotProvider;

    @Mock
    private GameLoop gameLoop;

    private InMemoryGameSimulation simulation;

    @BeforeEach
    void setUp() {
        simulation = new InMemoryGameSimulation(
                matchService, playerService, playerMatchService,
                moduleManager, commandResolver, commandQueue,
                snapshotProvider, gameLoop);
    }

    @Nested
    @DisplayName("tick")
    class Tick {

        @Test
        @DisplayName("should return zero initially")
        void shouldReturnZeroInitially() {
            assertThat(simulation.getCurrentTick()).isEqualTo(0);
        }

        @Test
        @DisplayName("should increment tick")
        void shouldIncrementTick() {
            assertThat(simulation.advanceTick()).isEqualTo(1);
            assertThat(simulation.advanceTick()).isEqualTo(2);
            assertThat(simulation.getCurrentTick()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("match operations")
    class MatchOperations {

        @Test
        @DisplayName("createMatch should delegate to service")
        void createMatchShouldDelegateToService() {
            Match match = new Match(1L, List.of(), List.of());
            when(matchService.createMatch(match)).thenReturn(match);

            simulation.createMatch(match);

            verify(matchService).createMatch(match);
        }

        @Test
        @DisplayName("deleteMatch should check existence and delete")
        void deleteMatchShouldCheckExistenceAndDelete() {
            when(matchService.matchExists(1L)).thenReturn(true);

            simulation.deleteMatch(1L);

            verify(matchService).matchExists(1L);
            verify(matchService).deleteMatch(1L);
        }

        @Test
        @DisplayName("deleteMatch non-existent should not call delete")
        void deleteMatchNonExistentShouldNotCallDelete() {
            when(matchService.matchExists(1L)).thenReturn(false);

            simulation.deleteMatch(1L);

            verify(matchService).matchExists(1L);
            verify(matchService, never()).deleteMatch(anyLong());
        }

        @Test
        @DisplayName("getMatch should delegate to service")
        void getMatchShouldDelegateToService() {
            Match match = new Match(1L, List.of(), List.of());
            when(matchService.getMatch(1L)).thenReturn(Optional.of(match));

            Optional<Match> result = simulation.getMatch(1L);

            assertThat(result).contains(match);
            verify(matchService).getMatch(1L);
        }

        @Test
        @DisplayName("getAllMatches should delegate to service")
        void getAllMatchesShouldDelegateToService() {
            List<Match> matches = List.of(new Match(1L, List.of(), List.of()), new Match(2L, List.of(), List.of()));
            when(matchService.getAllMatches()).thenReturn(matches);

            List<Match> result = simulation.getAllMatches();

            assertThat(result).isEqualTo(matches);
            verify(matchService).getAllMatches();
        }
    }

    @Nested
    @DisplayName("player operations")
    class PlayerOperations {

        @Test
        @DisplayName("createPlayer should delegate to service")
        void createPlayerShouldDelegateToService() {
            Player player = new Player(1L);
            when(playerService.createPlayer(player)).thenReturn(player);

            simulation.createPlayer(player);

            verify(playerService).createPlayer(player);
        }

        @Test
        @DisplayName("deletePlayer should check existence and delete")
        void deletePlayerShouldCheckExistenceAndDelete() {
            when(playerService.playerExists(1L)).thenReturn(true);

            simulation.deletePlayer(1L);

            verify(playerService).playerExists(1L);
            verify(playerService).deletePlayer(1L);
        }

        @Test
        @DisplayName("deletePlayer non-existent should not call delete")
        void deletePlayerNonExistentShouldNotCallDelete() {
            when(playerService.playerExists(1L)).thenReturn(false);

            simulation.deletePlayer(1L);

            verify(playerService).playerExists(1L);
            verify(playerService, never()).deletePlayer(anyLong());
        }

        @Test
        @DisplayName("getPlayer should delegate to service")
        void getPlayerShouldDelegateToService() {
            Player player = new Player(1L);
            when(playerService.getPlayer(1L)).thenReturn(Optional.of(player));

            Optional<Player> result = simulation.getPlayer(1L);

            assertThat(result).contains(player);
            verify(playerService).getPlayer(1L);
        }

        @Test
        @DisplayName("getAllPlayers should delegate to service")
        void getAllPlayersShouldDelegateToService() {
            List<Player> players = List.of(new Player(1L), new Player(2L));
            when(playerService.getAllPlayers()).thenReturn(players);

            List<Player> result = simulation.getAllPlayers();

            assertThat(result).isEqualTo(players);
            verify(playerService).getAllPlayers();
        }
    }

    @Nested
    @DisplayName("player-match operations")
    class PlayerMatchOperations {

        @Test
        @DisplayName("joinMatch should delegate to service")
        void joinMatchShouldDelegateToService() {
            PlayerMatch playerMatch = new PlayerMatch(1L, 100L);
            when(playerMatchService.joinMatch(1L, 100L)).thenReturn(playerMatch);

            simulation.joinMatch(playerMatch);

            verify(playerMatchService).joinMatch(1L, 100L);
        }

        @Test
        @DisplayName("leaveMatch should check existence and delete")
        void leaveMatchShouldCheckExistenceAndDelete() {
            PlayerMatch playerMatch = new PlayerMatch(1L, 100L);
            when(playerMatchService.isPlayerInMatch(1L, 100L)).thenReturn(true);

            simulation.leaveMatch(playerMatch);

            verify(playerMatchService).isPlayerInMatch(1L, 100L);
            verify(playerMatchService).leaveMatch(1L, 100L);
        }

        @Test
        @DisplayName("leaveMatch non-existent should not call service")
        void leaveMatchNonExistentShouldNotCallService() {
            PlayerMatch playerMatch = new PlayerMatch(1L, 100L);
            when(playerMatchService.isPlayerInMatch(1L, 100L)).thenReturn(false);

            simulation.leaveMatch(playerMatch);

            verify(playerMatchService).isPlayerInMatch(1L, 100L);
            verify(playerMatchService, never()).leaveMatch(anyLong(), anyLong());
        }

        @Test
        @DisplayName("getPlayerMatch should delegate to service")
        void getPlayerMatchShouldDelegateToService() {
            PlayerMatch playerMatch = new PlayerMatch(1L, 100L);
            when(playerMatchService.getPlayerMatch(1L, 100L)).thenReturn(Optional.of(playerMatch));

            Optional<PlayerMatch> result = simulation.getPlayerMatch(1L, 100L);

            assertThat(result).contains(playerMatch);
            verify(playerMatchService).getPlayerMatch(1L, 100L);
        }

        @Test
        @DisplayName("getPlayerMatchesByMatch should delegate to service")
        void getPlayerMatchesByMatchShouldDelegateToService() {
            List<PlayerMatch> playerMatches = List.of(
                    new PlayerMatch(1L, 100L),
                    new PlayerMatch(2L, 100L));
            when(playerMatchService.getPlayersInMatch(100L)).thenReturn(playerMatches);

            List<PlayerMatch> result = simulation.getPlayerMatchesByMatch(100L);

            assertThat(result).isEqualTo(playerMatches);
            verify(playerMatchService).getPlayersInMatch(100L);
        }

        @Test
        @DisplayName("getPlayerMatchesByPlayer should delegate to service")
        void getPlayerMatchesByPlayerShouldDelegateToService() {
            List<PlayerMatch> playerMatches = List.of(
                    new PlayerMatch(1L, 100L),
                    new PlayerMatch(1L, 200L));
            when(playerMatchService.getMatchesForPlayer(1L)).thenReturn(playerMatches);

            List<PlayerMatch> result = simulation.getPlayerMatchesByPlayer(1L);

            assertThat(result).isEqualTo(playerMatches);
            verify(playerMatchService).getMatchesForPlayer(1L);
        }
    }

    @Nested
    @DisplayName("module operations")
    class ModuleOperations {

        @Test
        @DisplayName("installModule should call module manager")
        void installModuleShouldCallModuleManager() throws Exception {
            Path jarPath = Paths.get("/path/to/module.jar");

            simulation.installModule("/path/to/module.jar");

            verify(moduleManager).installModule(jarPath);
        }

        @Test
        @DisplayName("installModule with null module manager should not throw")
        void installModuleWithNullModuleManagerShouldNotThrow() {
            InMemoryGameSimulation simWithoutModuleManager = new InMemoryGameSimulation(
                    matchService, playerService, playerMatchService, null, commandResolver, commandQueue,
                    snapshotProvider, gameLoop);

            simWithoutModuleManager.installModule("/path/to/module.jar");
            // Should not throw
        }
    }

    @Nested
    @DisplayName("command operations")
    class CommandOperations {

        @Test
        @DisplayName("enqueueCommand should resolve and schedule command")
        void enqueueCommandShouldResolveAndScheduleCommand() {
            EngineCommand command = mock(EngineCommand.class);
            CommandPayload payload = mock(CommandPayload.class);
            when(commandResolver.resolveByName("testCommand")).thenReturn(command);

            simulation.enqueueCommand("testCommand", payload);

            verify(commandResolver).resolveByName("testCommand");
            verify(commandQueue).enqueue(command, payload);
        }

        @Test
        @DisplayName("enqueueCommand with unknown command should throw")
        void enqueueCommandWithUnknownCommandShouldThrow() {
            when(commandResolver.resolveByName("unknownCommand")).thenReturn(null);

            assertThatThrownBy(() -> simulation.enqueueCommand("unknownCommand", mock(CommandPayload.class)))
                    .isInstanceOf(EntityNotFoundException.class);

            verify(commandResolver).resolveByName("unknownCommand");
            verifyNoInteractions(commandQueue);
        }
    }
}
