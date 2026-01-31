/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.stormstack.thunder.auth.service;

import ca.samanthaireland.stormstack.thunder.auth.config.AuthConfiguration;
import ca.samanthaireland.stormstack.thunder.auth.exception.AuthException;
import ca.samanthaireland.stormstack.thunder.auth.model.MatchToken;
import ca.samanthaireland.stormstack.thunder.auth.model.MatchTokenId;
import ca.samanthaireland.stormstack.thunder.auth.model.UserId;
import ca.samanthaireland.stormstack.thunder.auth.repository.MatchTokenRepository;
import ca.samanthaireland.stormstack.thunder.auth.service.dto.IssueMatchTokenRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MatchTokenServiceImpl")
class MatchTokenServiceImplTest {

    @Mock
    private MatchTokenRepository tokenRepository;

    @Mock
    private AuthConfiguration config;

    private MatchTokenServiceImpl matchTokenService;

    private UserId testUserId;

    @BeforeEach
    void setUp() {
        when(config.jwtIssuer()).thenReturn("https://test.lightningfirefly.com");
        when(config.jwtSecret()).thenReturn(Optional.of("test-secret-key-for-unit-tests-only"));

        matchTokenService = new MatchTokenServiceImpl(tokenRepository, config);

        testUserId = UserId.generate();
    }

    @Nested
    @DisplayName("issueToken")
    class IssueToken {

        @Test
        @DisplayName("should issue token with all parameters")
        void shouldIssueTokenWithAllParameters() {
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            IssueMatchTokenRequest request = new IssueMatchTokenRequest(
                    "match-123",
                    "container-456",
                    "player-789",
                    testUserId,
                    "TestPlayer",
                    Set.of("submit_commands", "view_snapshots"),
                    Duration.ofHours(2)
            );

            MatchToken token = matchTokenService.issueToken(request);

            assertThat(token).isNotNull();
            assertThat(token.matchId()).isEqualTo("match-123");
            assertThat(token.containerId()).isEqualTo("container-456");
            assertThat(token.playerId()).isEqualTo("player-789");
            assertThat(token.playerName()).isEqualTo("TestPlayer");
            assertThat(token.scopes()).containsExactlyInAnyOrder("submit_commands", "view_snapshots");
            assertThat(token.jwtToken()).isNotBlank();
            assertThat(token.expiresAt()).isAfter(Instant.now());

            verify(tokenRepository).save(any(MatchToken.class));
        }

        @Test
        @DisplayName("should issue token with default scopes")
        void shouldIssueTokenWithDefaultScopes() {
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            IssueMatchTokenRequest request = IssueMatchTokenRequest.withDefaultScopes(
                    "match-123",
                    "container-456",
                    "player-789",
                    testUserId,
                    "TestPlayer",
                    Duration.ofHours(2)
            );

            MatchToken token = matchTokenService.issueToken(request);

            assertThat(token.scopes()).containsExactlyInAnyOrder(
                    MatchToken.SCOPE_SUBMIT_COMMANDS,
                    MatchToken.SCOPE_VIEW_SNAPSHOTS,
                    MatchToken.SCOPE_RECEIVE_ERRORS
            );
        }

        @Test
        @DisplayName("should reject null request")
        void shouldRejectNullRequest() {
            assertThatThrownBy(() -> matchTokenService.issueToken(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should allow null containerId")
        void shouldAllowNullContainerId() {
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            IssueMatchTokenRequest request = new IssueMatchTokenRequest(
                    "match-123",
                    null,
                    "player-789",
                    testUserId,
                    "TestPlayer",
                    Set.of("read"),
                    Duration.ofHours(1)
            );

            MatchToken token = matchTokenService.issueToken(request);

            assertThat(token.containerId()).isNull();
        }
    }

    @Nested
    @DisplayName("validateToken")
    class ValidateToken {

        @Test
        @DisplayName("should validate active token")
        void shouldValidateActiveToken() {
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            IssueMatchTokenRequest request = IssueMatchTokenRequest.withDefaultScopes(
                    "match-123",
                    "container-456",
                    "player-789",
                    testUserId,
                    "TestPlayer",
                    Duration.ofHours(2)
            );
            MatchToken issuedToken = matchTokenService.issueToken(request);

            MatchToken storedToken = issuedToken.withoutJwt();
            when(tokenRepository.findById(issuedToken.id())).thenReturn(Optional.of(storedToken));

            MatchToken validated = matchTokenService.validateToken(issuedToken.jwtToken());

            assertThat(validated.matchId()).isEqualTo("match-123");
            assertThat(validated.playerId()).isEqualTo("player-789");
        }

        @Test
        @DisplayName("should throw for invalid JWT")
        void shouldThrowForInvalidJwt() {
            assertThatThrownBy(() -> matchTokenService.validateToken("invalid.jwt.token"))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthException.ErrorCode.INVALID_TOKEN);
        }

        @Test
        @DisplayName("should throw for revoked token")
        void shouldThrowForRevokedToken() {
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            IssueMatchTokenRequest request = IssueMatchTokenRequest.withDefaultScopes(
                    "match-123",
                    "container-456",
                    "player-789",
                    testUserId,
                    "TestPlayer",
                    Duration.ofHours(2)
            );
            MatchToken issuedToken = matchTokenService.issueToken(request);

            MatchToken revokedToken = issuedToken.withoutJwt().revoke();
            when(tokenRepository.findById(issuedToken.id())).thenReturn(Optional.of(revokedToken));

            assertThatThrownBy(() -> matchTokenService.validateToken(issuedToken.jwtToken()))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthException.ErrorCode.INVALID_TOKEN);
        }
    }

    @Nested
    @DisplayName("validateTokenForMatch")
    class ValidateTokenForMatch {

        @Test
        @DisplayName("should validate token for correct match")
        void shouldValidateTokenForCorrectMatch() {
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            IssueMatchTokenRequest request = IssueMatchTokenRequest.withDefaultScopes(
                    "match-123",
                    "container-456",
                    "player-789",
                    testUserId,
                    "TestPlayer",
                    Duration.ofHours(2)
            );
            MatchToken issuedToken = matchTokenService.issueToken(request);

            MatchToken storedToken = issuedToken.withoutJwt();
            when(tokenRepository.findById(issuedToken.id())).thenReturn(Optional.of(storedToken));

            MatchToken validated = matchTokenService.validateTokenForMatch(issuedToken.jwtToken(), "match-123");

            assertThat(validated.matchId()).isEqualTo("match-123");
        }

        @Test
        @DisplayName("should throw for wrong match")
        void shouldThrowForWrongMatch() {
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            IssueMatchTokenRequest request = IssueMatchTokenRequest.withDefaultScopes(
                    "match-123",
                    "container-456",
                    "player-789",
                    testUserId,
                    "TestPlayer",
                    Duration.ofHours(2)
            );
            MatchToken issuedToken = matchTokenService.issueToken(request);

            MatchToken storedToken = issuedToken.withoutJwt();
            when(tokenRepository.findById(issuedToken.id())).thenReturn(Optional.of(storedToken));

            assertThatThrownBy(() -> matchTokenService.validateTokenForMatch(issuedToken.jwtToken(), "wrong-match"))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthException.ErrorCode.PERMISSION_DENIED);
        }
    }

    @Nested
    @DisplayName("revokeToken")
    class RevokeToken {

        @Test
        @DisplayName("should revoke existing token")
        void shouldRevokeExistingToken() {
            MatchTokenId tokenId = MatchTokenId.generate();
            MatchToken token = MatchToken.create(
                    "match-123",
                    "container-456",
                    "player-789",
                    testUserId,
                    "TestPlayer",
                    Set.of("read"),
                    Instant.now().plus(2, ChronoUnit.HOURS)
            );

            MatchToken tokenWithId = new MatchToken(
                    tokenId,
                    token.matchId(),
                    token.containerId(),
                    token.playerId(),
                    token.userId(),
                    token.playerName(),
                    token.scopes(),
                    token.createdAt(),
                    token.expiresAt(),
                    null,
                    null
            );

            when(tokenRepository.findById(tokenId)).thenReturn(Optional.of(tokenWithId));
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MatchToken result = matchTokenService.revokeToken(tokenId);

            assertThat(result.isRevoked()).isTrue();
            verify(tokenRepository).save(any(MatchToken.class));
        }

        @Test
        @DisplayName("should throw for non-existent token")
        void shouldThrowForNonExistentToken() {
            MatchTokenId tokenId = MatchTokenId.generate();
            when(tokenRepository.findById(tokenId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> matchTokenService.revokeToken(tokenId))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthException.ErrorCode.INVALID_TOKEN);
        }
    }

    @Nested
    @DisplayName("revokeTokensForMatch")
    class RevokeTokensForMatch {

        @Test
        @DisplayName("should revoke all active tokens for match")
        void shouldRevokeAllActiveTokensForMatch() {
            MatchToken token1 = MatchToken.create(
                    "match-123", "container", "player1", testUserId, "Player1",
                    Set.of("read"), Instant.now().plus(1, ChronoUnit.HOURS));
            MatchToken token2 = MatchToken.create(
                    "match-123", "container", "player2", testUserId, "Player2",
                    Set.of("read"), Instant.now().plus(1, ChronoUnit.HOURS));

            when(tokenRepository.findActiveByMatchId("match-123")).thenReturn(List.of(token1, token2));
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            long count = matchTokenService.revokeTokensForMatch("match-123");

            assertThat(count).isEqualTo(2);
            verify(tokenRepository, times(2)).save(any(MatchToken.class));
        }

        @Test
        @DisplayName("should return 0 when no active tokens")
        void shouldReturnZeroWhenNoActiveTokens() {
            when(tokenRepository.findActiveByMatchId("match-123")).thenReturn(List.of());

            long count = matchTokenService.revokeTokensForMatch("match-123");

            assertThat(count).isZero();
            verify(tokenRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("should reject null tokenRepository")
        void shouldRejectNullTokenRepository() {
            assertThatThrownBy(() -> new MatchTokenServiceImpl(null, config))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null config")
        void shouldRejectNullConfig() {
            assertThatThrownBy(() -> new MatchTokenServiceImpl(tokenRepository, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should generate secret if not provided")
        void shouldGenerateSecretIfNotProvided() {
            when(config.jwtSecret()).thenReturn(Optional.empty());

            MatchTokenServiceImpl service = new MatchTokenServiceImpl(tokenRepository, config);

            assertThat(service).isNotNull();
        }
    }
}
