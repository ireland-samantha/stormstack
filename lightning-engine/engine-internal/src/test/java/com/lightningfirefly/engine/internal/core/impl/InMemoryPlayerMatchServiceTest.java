package com.lightningfirefly.engine.internal.core.impl;

import com.lightningfirefly.engine.core.exception.EntityNotFoundException;
import com.lightningfirefly.engine.core.match.MatchService;
import com.lightningfirefly.engine.internal.core.match.InMemoryPlayerMatchService;
import com.lightningfirefly.engine.core.match.PlayerMatch;
import com.lightningfirefly.engine.core.match.PlayerMatchRepository;
import com.lightningfirefly.engine.core.match.PlayerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link InMemoryPlayerMatchService}.
 *
 * <p>Tests verify Service pattern compliance with business logic operations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InMemoryPlayerMatchService")
class InMemoryPlayerMatchServiceTest {

    @Mock
    private PlayerMatchRepository playerMatchRepository;

    @Mock
    private PlayerService playerService;

    @Mock
    private MatchService matchService;

    private InMemoryPlayerMatchService service;

    @BeforeEach
    void setUp() {
        service = new InMemoryPlayerMatchService(playerMatchRepository, playerService, matchService);
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("should reject null playerMatchRepository")
        void shouldRejectNullPlayerMatchRepository() {
            assertThatThrownBy(() -> new InMemoryPlayerMatchService(null, playerService, matchService))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("playerMatchRepository must not be null");
        }

        @Test
        @DisplayName("should reject null playerService")
        void shouldRejectNullPlayerService() {
            assertThatThrownBy(() -> new InMemoryPlayerMatchService(playerMatchRepository, null, matchService))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("playerService must not be null");
        }

        @Test
        @DisplayName("should reject null matchService")
        void shouldRejectNullMatchService() {
            assertThatThrownBy(() -> new InMemoryPlayerMatchService(playerMatchRepository, playerService, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("matchService must not be null");
        }
    }

    @Nested
    @DisplayName("joinMatch")
    class JoinMatch {

        @Test
        @DisplayName("should create player-match when player and match exist")
        void shouldCreatePlayerMatchWhenBothExist() {
            when(playerService.playerExists(1L)).thenReturn(true);
            when(matchService.matchExists(100L)).thenReturn(true);
            when(playerMatchRepository.existsByPlayerAndMatch(1L, 100L)).thenReturn(false);
            when(playerMatchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PlayerMatch result = service.joinMatch(1L, 100L);

            assertThat(result.playerId()).isEqualTo(1L);
            assertThat(result.matchId()).isEqualTo(100L);
            verify(playerMatchRepository).save(any(PlayerMatch.class));
        }

        @Test
        @DisplayName("should throw when player does not exist")
        void shouldThrowWhenPlayerDoesNotExist() {
            when(playerService.playerExists(999L)).thenReturn(false);

            assertThatThrownBy(() -> service.joinMatch(999L, 100L))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Player 999 not found");
        }

        @Test
        @DisplayName("should throw when match does not exist")
        void shouldThrowWhenMatchDoesNotExist() {
            when(playerService.playerExists(1L)).thenReturn(true);
            when(matchService.matchExists(999L)).thenReturn(false);

            assertThatThrownBy(() -> service.joinMatch(1L, 999L))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Match 999 not found");
        }

        @Test
        @DisplayName("should throw when player is already in match")
        void shouldThrowWhenPlayerAlreadyInMatch() {
            when(playerService.playerExists(1L)).thenReturn(true);
            when(matchService.matchExists(100L)).thenReturn(true);
            when(playerMatchRepository.existsByPlayerAndMatch(1L, 100L)).thenReturn(true);

            assertThatThrownBy(() -> service.joinMatch(1L, 100L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Player 1 is already in match 100");
        }
    }

    @Nested
    @DisplayName("leaveMatch")
    class LeaveMatch {

        @Test
        @DisplayName("should delete player-match when exists")
        void shouldDeletePlayerMatchWhenExists() {
            when(playerMatchRepository.existsByPlayerAndMatch(1L, 100L)).thenReturn(true);

            service.leaveMatch(1L, 100L);

            verify(playerMatchRepository).deleteByPlayerAndMatch(1L, 100L);
        }

        @Test
        @DisplayName("should throw when player-match does not exist")
        void shouldThrowWhenPlayerMatchDoesNotExist() {
            when(playerMatchRepository.existsByPlayerAndMatch(999L, 999L)).thenReturn(false);

            assertThatThrownBy(() -> service.leaveMatch(999L, 999L))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Player 999 is not in match 999");
        }
    }

    @Nested
    @DisplayName("getPlayerMatch")
    class GetPlayerMatch {

        @Test
        @DisplayName("should return player-match when found")
        void shouldReturnPlayerMatchWhenFound() {
            PlayerMatch playerMatch = new PlayerMatch(1L, 100L);
            when(playerMatchRepository.findByPlayerAndMatch(1L, 100L)).thenReturn(Optional.of(playerMatch));

            Optional<PlayerMatch> result = service.getPlayerMatch(1L, 100L);

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(playerMatch);
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            when(playerMatchRepository.findByPlayerAndMatch(999L, 999L)).thenReturn(Optional.empty());

            Optional<PlayerMatch> result = service.getPlayerMatch(999L, 999L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getPlayerMatchOrThrow")
    class GetPlayerMatchOrThrow {

        @Test
        @DisplayName("should return player-match when found")
        void shouldReturnPlayerMatchWhenFound() {
            PlayerMatch playerMatch = new PlayerMatch(1L, 100L);
            when(playerMatchRepository.findByPlayerAndMatch(1L, 100L)).thenReturn(Optional.of(playerMatch));

            PlayerMatch result = service.getPlayerMatchOrThrow(1L, 100L);

            assertThat(result).isEqualTo(playerMatch);
        }

        @Test
        @DisplayName("should throw when not found")
        void shouldThrowWhenNotFound() {
            when(playerMatchRepository.findByPlayerAndMatch(999L, 999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getPlayerMatchOrThrow(999L, 999L))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Player 999 is not in match 999");
        }
    }

    @Nested
    @DisplayName("getPlayersInMatch")
    class GetPlayersInMatch {

        @Test
        @DisplayName("should delegate to repository")
        void shouldDelegateToRepository() {
            List<PlayerMatch> playerMatches = List.of(
                    new PlayerMatch(1L, 100L),
                    new PlayerMatch(2L, 100L)
            );
            when(playerMatchRepository.findByMatchId(100L)).thenReturn(playerMatches);

            List<PlayerMatch> result = service.getPlayersInMatch(100L);

            assertThat(result).hasSize(2);
            verify(playerMatchRepository).findByMatchId(100L);
        }
    }

    @Nested
    @DisplayName("getMatchesForPlayer")
    class GetMatchesForPlayer {

        @Test
        @DisplayName("should delegate to repository")
        void shouldDelegateToRepository() {
            List<PlayerMatch> playerMatches = List.of(
                    new PlayerMatch(1L, 100L),
                    new PlayerMatch(1L, 200L)
            );
            when(playerMatchRepository.findByPlayerId(1L)).thenReturn(playerMatches);

            List<PlayerMatch> result = service.getMatchesForPlayer(1L);

            assertThat(result).hasSize(2);
            verify(playerMatchRepository).findByPlayerId(1L);
        }
    }

    @Nested
    @DisplayName("isPlayerInMatch")
    class IsPlayerInMatch {

        @Test
        @DisplayName("should return true when player is in match")
        void shouldReturnTrueWhenPlayerInMatch() {
            when(playerMatchRepository.existsByPlayerAndMatch(1L, 100L)).thenReturn(true);

            assertThat(service.isPlayerInMatch(1L, 100L)).isTrue();
        }

        @Test
        @DisplayName("should return false when player is not in match")
        void shouldReturnFalseWhenPlayerNotInMatch() {
            when(playerMatchRepository.existsByPlayerAndMatch(999L, 999L)).thenReturn(false);

            assertThat(service.isPlayerInMatch(999L, 999L)).isFalse();
        }
    }
}
