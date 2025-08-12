package com.lightningfirefly.engine.internal.core.impl;

import com.lightningfirefly.engine.core.exception.EntityNotFoundException;
import com.lightningfirefly.engine.internal.core.match.InMemoryMatchService;
import com.lightningfirefly.engine.core.match.MatchRepository;
import com.lightningfirefly.engine.core.match.Match;
import com.lightningfirefly.engine.ext.module.ModuleResolver;
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
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link InMemoryMatchService}.
 *
 * <p>Tests verify Service pattern compliance with business logic operations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InMemoryMatchService")
class InMemoryMatchServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private ModuleResolver moduleResolver;

    private InMemoryMatchService service;

    @BeforeEach
    void setUp() {
        service = new InMemoryMatchService(matchRepository, moduleResolver);
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("should reject null matchRepository")
        void shouldRejectNullMatchRepository() {
            assertThatThrownBy(() -> new InMemoryMatchService(null, moduleResolver))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("matchRepository must not be null");
        }

        @Test
        @DisplayName("should reject null moduleResolver")
        void shouldRejectNullModuleResolver() {
            assertThatThrownBy(() -> new InMemoryMatchService(matchRepository, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("moduleResolver must not be null");
        }
    }

    @Nested
    @DisplayName("createMatch")
    class CreateMatch {

        @Test
        @DisplayName("should delegate to repository when no modules")
        void shouldDelegateToRepositoryWhenNoModules() {
            Match match = new Match(1L, List.of());
            when(matchRepository.save(match)).thenReturn(match);

            Match result = service.createMatch(match);

            assertThat(result).isEqualTo(match);
            verify(matchRepository).save(match);
        }

        @Test
        @DisplayName("should throw when module not found")
        void shouldThrowWhenModuleNotFound() {
            Match match = new Match(1L, List.of("nonexistent"));
            when(moduleResolver.hasModule("nonexistent")).thenReturn(false);

            assertThatThrownBy(() -> service.createMatch(match))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Module nonexistent not found");
        }

        @Test
        @DisplayName("should create match when module exists")
        void shouldCreateMatchWhenModuleExists() {
            Match match = new Match(1L, List.of("existingModule"));
            when(matchRepository.save(match)).thenReturn(match);
            when(moduleResolver.hasModule("existingModule")).thenReturn(true);

            Match result = service.createMatch(match);

            assertThat(result).isEqualTo(match);
            verify(matchRepository).save(match);
        }

        @Test
        @DisplayName("should reject null match")
        void shouldRejectNullMatch() {
            assertThatThrownBy(() -> service.createMatch((Match) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("match must not be null");
        }
    }

    @Nested
    @DisplayName("deleteMatch")
    class DeleteMatch {

        @Test
        @DisplayName("should delete existing match")
        void shouldDeleteExistingMatch() {
            when(matchRepository.existsById(1L)).thenReturn(true);

            service.deleteMatch(1L);

            verify(matchRepository).deleteById(1L);
        }

        @Test
        @DisplayName("should throw when match does not exist")
        void shouldThrowWhenMatchDoesNotExist() {
            when(matchRepository.existsById(999L)).thenReturn(false);

            assertThatThrownBy(() -> service.deleteMatch(999L))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Match 999 not found");
        }
    }

    @Nested
    @DisplayName("getMatch")
    class GetMatch {

        @Test
        @DisplayName("should return match when found")
        void shouldReturnMatchWhenFound() {
            Match match = new Match(1L, List.of());
            when(matchRepository.findById(1L)).thenReturn(Optional.of(match));

            Optional<Match> result = service.getMatch(1L);

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(match);
            verify(matchRepository).findById(1L);
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            when(matchRepository.findById(999L)).thenReturn(Optional.empty());

            Optional<Match> result = service.getMatch(999L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getMatchOrThrow")
    class GetMatchOrThrow {

        @Test
        @DisplayName("should return match when found")
        void shouldReturnMatchWhenFound() {
            Match match = new Match(1L, List.of());
            when(matchRepository.findById(1L)).thenReturn(Optional.of(match));

            Match result = service.getMatchOrThrow(1L);

            assertThat(result).isEqualTo(match);
        }

        @Test
        @DisplayName("should throw when not found")
        void shouldThrowWhenNotFound() {
            when(matchRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getMatchOrThrow(999L))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Match 999 not found");
        }
    }

    @Nested
    @DisplayName("getAllMatches")
    class GetAllMatches {

        @Test
        @DisplayName("should delegate to repository")
        void shouldDelegateToRepository() {
            List<Match> matches = List.of(new Match(1L, List.of()), new Match(2L, List.of()));
            when(matchRepository.findAll()).thenReturn(matches);

            List<Match> result = service.getAllMatches();

            assertThat(result).hasSize(2);
            verify(matchRepository).findAll();
        }
    }

    @Nested
    @DisplayName("matchExists")
    class MatchExists {

        @Test
        @DisplayName("should return true when match exists")
        void shouldReturnTrueWhenMatchExists() {
            when(matchRepository.existsById(1L)).thenReturn(true);

            assertThat(service.matchExists(1L)).isTrue();
        }

        @Test
        @DisplayName("should return false when match does not exist")
        void shouldReturnFalseWhenMatchDoesNotExist() {
            when(matchRepository.existsById(999L)).thenReturn(false);

            assertThat(service.matchExists(999L)).isFalse();
        }
    }
}
