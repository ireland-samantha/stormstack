package com.lightningfirefly.engine.internal.ext.gamemaster;

import com.lightningfirefly.engine.core.match.Match;
import com.lightningfirefly.engine.core.match.MatchService;
import com.lightningfirefly.game.gm.GameMaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("GameMasterTickService")
@ExtendWith(MockitoExtension.class)
class GameMasterTickServiceTest {

    @Mock
    private GameMasterManager gameMasterManager;

    @Mock
    private MatchService matchService;

    private GameMasterTickService service;

    @BeforeEach
    void setUp() {
        service = new GameMasterTickService(gameMasterManager, matchService);
    }

    @Nested
    @DisplayName("onTick")
    class OnTick {

        @Test
        @DisplayName("should not execute any game masters when no matches exist")
        void shouldNotExecuteWhenNoMatches() {
            when(matchService.getAllMatches()).thenReturn(List.of());

            service.onTick(1L);

            verifyNoInteractions(gameMasterManager);
        }

        @Test
        @DisplayName("should not execute any game masters when match has no game masters enabled")
        void shouldNotExecuteWhenNoGameMastersEnabled() {
            Match match = new Match(1L, List.of("SomeModule"), List.of());
            when(matchService.getAllMatches()).thenReturn(List.of(match));

            service.onTick(1L);

            verify(gameMasterManager, never()).createForMatch(anyString(), anyLong());
        }

        @Test
        @DisplayName("should not execute any game masters when match has null game masters")
        void shouldNotExecuteWhenNullGameMasters() {
            Match match = new Match(1L, List.of("SomeModule"), null);
            when(matchService.getAllMatches()).thenReturn(List.of(match));

            service.onTick(1L);

            verify(gameMasterManager, never()).createForMatch(anyString(), anyLong());
        }

        @Test
        @DisplayName("should create and execute game masters for match with enabled game masters")
        void shouldCreateAndExecuteGameMasters() {
            AtomicInteger tickCount = new AtomicInteger(0);
            GameMaster mockGm = () -> tickCount.incrementAndGet();

            Match match = new Match(1L, List.of(), List.of("TestGM"));
            when(matchService.getAllMatches()).thenReturn(List.of(match));
            when(gameMasterManager.createForMatch("TestGM", 1L)).thenReturn(mockGm);

            service.onTick(1L);
            service.onTick(2L);
            service.onTick(3L);

            assertThat(tickCount.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("should cache game master instances per match")
        void shouldCacheGameMasterInstances() {
            GameMaster mockGm = mock(GameMaster.class);

            Match match = new Match(1L, List.of(), List.of("TestGM"));
            when(matchService.getAllMatches()).thenReturn(List.of(match));
            when(gameMasterManager.createForMatch("TestGM", 1L)).thenReturn(mockGm);

            service.onTick(1L);
            service.onTick(2L);

            // Should only create once due to caching
            verify(gameMasterManager, times(1)).createForMatch("TestGM", 1L);
            // But execute twice
            verify(mockGm, times(2)).onTick();
        }

        @Test
        @DisplayName("should handle game master exceptions gracefully")
        void shouldHandleExceptionsGracefully() {
            AtomicInteger successCount = new AtomicInteger(0);
            GameMaster failingGm = () -> { throw new RuntimeException("Boom!"); };
            GameMaster successGm = () -> successCount.incrementAndGet();

            Match match = new Match(1L, List.of(), List.of("FailingGM", "SuccessGM"));
            when(matchService.getAllMatches()).thenReturn(List.of(match));
            when(gameMasterManager.createForMatch("FailingGM", 1L)).thenReturn(failingGm);
            when(gameMasterManager.createForMatch("SuccessGM", 1L)).thenReturn(successGm);

            // Should not throw, and should continue executing other game masters
            service.onTick(1L);

            assertThat(successCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("should execute game masters for multiple matches")
        void shouldExecuteForMultipleMatches() {
            AtomicInteger match1Ticks = new AtomicInteger(0);
            AtomicInteger match2Ticks = new AtomicInteger(0);
            GameMaster gm1 = () -> match1Ticks.incrementAndGet();
            GameMaster gm2 = () -> match2Ticks.incrementAndGet();

            Match match1 = new Match(1L, List.of(), List.of("GM1"));
            Match match2 = new Match(2L, List.of(), List.of("GM2"));
            when(matchService.getAllMatches()).thenReturn(List.of(match1, match2));
            when(gameMasterManager.createForMatch("GM1", 1L)).thenReturn(gm1);
            when(gameMasterManager.createForMatch("GM2", 2L)).thenReturn(gm2);

            service.onTick(1L);

            assertThat(match1Ticks.get()).isEqualTo(1);
            assertThat(match2Ticks.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("invalidateMatch")
    class InvalidateMatch {

        @Test
        @DisplayName("should clear cache for specific match")
        void shouldClearCacheForSpecificMatch() {
            GameMaster mockGm = mock(GameMaster.class);

            Match match = new Match(1L, List.of(), List.of("TestGM"));
            when(matchService.getAllMatches()).thenReturn(List.of(match));
            when(gameMasterManager.createForMatch("TestGM", 1L)).thenReturn(mockGm);

            // Execute once to populate cache
            service.onTick(1L);
            verify(gameMasterManager, times(1)).createForMatch("TestGM", 1L);

            // Invalidate the cache
            service.invalidateMatch(1L);

            // Execute again - should recreate the game master
            service.onTick(2L);
            verify(gameMasterManager, times(2)).createForMatch("TestGM", 1L);
        }
    }

    @Nested
    @DisplayName("invalidateAll")
    class InvalidateAll {

        @Test
        @DisplayName("should clear all caches")
        void shouldClearAllCaches() {
            GameMaster mockGm1 = mock(GameMaster.class);
            GameMaster mockGm2 = mock(GameMaster.class);

            Match match1 = new Match(1L, List.of(), List.of("GM1"));
            Match match2 = new Match(2L, List.of(), List.of("GM2"));
            when(matchService.getAllMatches()).thenReturn(List.of(match1, match2));
            when(gameMasterManager.createForMatch("GM1", 1L)).thenReturn(mockGm1);
            when(gameMasterManager.createForMatch("GM2", 2L)).thenReturn(mockGm2);

            // Execute once to populate cache
            service.onTick(1L);
            verify(gameMasterManager, times(1)).createForMatch("GM1", 1L);
            verify(gameMasterManager, times(1)).createForMatch("GM2", 2L);

            // Invalidate all caches
            service.invalidateAll();

            // Execute again - should recreate all game masters
            service.onTick(2L);
            verify(gameMasterManager, times(2)).createForMatch("GM1", 1L);
            verify(gameMasterManager, times(2)).createForMatch("GM2", 2L);
        }
    }
}
