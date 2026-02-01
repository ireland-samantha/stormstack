/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.stormstack.thunder.auth.service;

import ca.samanthaireland.stormstack.thunder.auth.config.AuthConfiguration;
import ca.samanthaireland.stormstack.thunder.auth.exception.AuthException;
import ca.samanthaireland.stormstack.thunder.auth.model.ApiToken;
import ca.samanthaireland.stormstack.thunder.auth.model.ApiTokenId;
import ca.samanthaireland.stormstack.thunder.auth.model.User;
import ca.samanthaireland.stormstack.thunder.auth.model.UserId;
import ca.samanthaireland.stormstack.thunder.auth.repository.ApiTokenRepository;
import ca.samanthaireland.stormstack.thunder.auth.repository.UserRepository;
import ca.samanthaireland.stormstack.thunder.auth.service.dto.CreateApiTokenRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ApiTokenServiceImpl")
class ApiTokenServiceImplTest {

    @Mock
    private ApiTokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordService passwordService;

    @Mock
    private AuthConfiguration config;

    @Mock
    private JwtTokenService jwtTokenService;

    private ApiTokenServiceImpl apiTokenService;

    private User testUser;
    private UserId testUserId;

    @BeforeEach
    void setUp() {
        when(config.apiTokenLengthBytes()).thenReturn(32);
        when(jwtTokenService.getIssuer()).thenReturn("https://test.lightningfirefly.com");
        when(jwtTokenService.createApiTokenSessionToken(any(), any(), any(), anyInt()))
                .thenReturn("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.mock-session-token");

        apiTokenService = new ApiTokenServiceImpl(tokenRepository, userRepository, passwordService, config, jwtTokenService);

        testUserId = UserId.generate();
        testUser = new User(
                testUserId,
                "testuser",
                "$2a$10$hashedpassword",
                Set.of(),
                Set.of(),
                Instant.now(),
                true
        );
    }

    @Nested
    @DisplayName("createToken")
    class CreateToken {

        @Test
        @DisplayName("should create token for valid user")
        void shouldCreateTokenForValidUser() {
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(passwordService.hashPassword(any())).thenReturn("$2a$10$hashedtoken");
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateApiTokenRequest request = new CreateApiTokenRequest(
                    testUserId, "test-token", Set.of("read", "write"), null);

            ApiTokenService.CreateTokenResult result = apiTokenService.createToken(request);

            assertThat(result.token()).isNotNull();
            assertThat(result.token().name()).isEqualTo("test-token");
            assertThat(result.token().userId()).isEqualTo(testUserId);
            assertThat(result.token().scopes()).containsExactlyInAnyOrder("read", "write");
            assertThat(result.plaintextToken()).startsWith("lat_");

            verify(tokenRepository).save(any(ApiToken.class));
        }

        @Test
        @DisplayName("should throw for non-existent user")
        void shouldThrowForNonExistentUser() {
            when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

            CreateApiTokenRequest request = new CreateApiTokenRequest(
                    testUserId, "test-token", Set.of("read"), null);

            assertThatThrownBy(() -> apiTokenService.createToken(request))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthException.ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("should create token with expiry")
        void shouldCreateTokenWithExpiry() {
            Instant expiry = Instant.now().plus(30, ChronoUnit.DAYS);
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(passwordService.hashPassword(any())).thenReturn("$2a$10$hashedtoken");
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateApiTokenRequest request = new CreateApiTokenRequest(
                    testUserId, "expiring-token", Set.of("read"), expiry);

            ApiTokenService.CreateTokenResult result = apiTokenService.createToken(request);

            assertThat(result.token().expiresAt()).isEqualTo(expiry);
        }

        @Test
        @DisplayName("should reject null request")
        void shouldRejectNullRequest() {
            assertThatThrownBy(() -> apiTokenService.createToken(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("validateToken")
    class ValidateToken {

        @Test
        @DisplayName("should validate active token")
        void shouldValidateActiveToken() {
            ApiToken activeToken = ApiToken.create(testUserId, "test", "$2a$10$hash", Set.of("read"), null);
            when(tokenRepository.findAllActive()).thenReturn(List.of(activeToken));
            when(passwordService.verifyPassword("lat_validtoken", "$2a$10$hash")).thenReturn(true);
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ApiToken result = apiTokenService.validateToken("lat_validtoken", "127.0.0.1");

            assertThat(result).isNotNull();
            assertThat(result.userId()).isEqualTo(testUserId);

            ArgumentCaptor<ApiToken> captor = ArgumentCaptor.forClass(ApiToken.class);
            verify(tokenRepository).save(captor.capture());
            assertThat(captor.getValue().lastUsedAt()).isNotNull();
        }

        @Test
        @DisplayName("should throw for invalid token format")
        void shouldThrowForInvalidTokenFormat() {
            assertThatThrownBy(() -> apiTokenService.validateToken("invalid_format", "127.0.0.1"))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthException.ErrorCode.INVALID_API_TOKEN);
        }

        @Test
        @DisplayName("should throw for null token")
        void shouldThrowForNullToken() {
            assertThatThrownBy(() -> apiTokenService.validateToken(null, "127.0.0.1"))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthException.ErrorCode.INVALID_API_TOKEN);
        }

        @Test
        @DisplayName("should throw for expired token")
        void shouldThrowForExpiredToken() {
            Instant pastExpiry = Instant.now().minus(1, ChronoUnit.DAYS);
            ApiToken expiredToken = new ApiToken(
                    ApiTokenId.generate(),
                    testUserId,
                    "test",
                    "$2a$10$hash",
                    Set.of("read"),
                    Instant.now().minus(7, ChronoUnit.DAYS),
                    pastExpiry,
                    null,
                    null,
                    null
            );
            when(tokenRepository.findAllActive()).thenReturn(List.of(expiredToken));
            when(passwordService.verifyPassword("lat_expiredtoken", "$2a$10$hash")).thenReturn(true);

            assertThatThrownBy(() -> apiTokenService.validateToken("lat_expiredtoken", "127.0.0.1"))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthException.ErrorCode.API_TOKEN_EXPIRED);
        }

        @Test
        @DisplayName("should throw for revoked token")
        void shouldThrowForRevokedToken() {
            ApiToken revokedToken = new ApiToken(
                    ApiTokenId.generate(),
                    testUserId,
                    "test",
                    "$2a$10$hash",
                    Set.of("read"),
                    Instant.now().minus(7, ChronoUnit.DAYS),
                    null,
                    Instant.now().minus(1, ChronoUnit.DAYS), // revoked
                    null,
                    null
            );
            when(tokenRepository.findAllActive()).thenReturn(List.of(revokedToken));
            when(passwordService.verifyPassword("lat_revokedtoken", "$2a$10$hash")).thenReturn(true);

            assertThatThrownBy(() -> apiTokenService.validateToken("lat_revokedtoken", "127.0.0.1"))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthException.ErrorCode.API_TOKEN_REVOKED);
        }

        @Test
        @DisplayName("should throw when no matching token found")
        void shouldThrowWhenNoMatchingTokenFound() {
            when(tokenRepository.findAllActive()).thenReturn(List.of());

            assertThatThrownBy(() -> apiTokenService.validateToken("lat_unknown", "127.0.0.1"))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthException.ErrorCode.INVALID_API_TOKEN);
        }
    }

    @Nested
    @DisplayName("revokeToken")
    class RevokeToken {

        @Test
        @DisplayName("should revoke existing token")
        void shouldRevokeExistingToken() {
            ApiTokenId tokenId = ApiTokenId.generate();
            ApiToken token = ApiToken.create(testUserId, "test", "$2a$10$hash", Set.of("read"), null);
            ApiToken tokenWithId = new ApiToken(
                    tokenId,
                    token.userId(),
                    token.name(),
                    token.tokenHash(),
                    token.scopes(),
                    token.createdAt(),
                    token.expiresAt(),
                    null,
                    null,
                    null
            );

            when(tokenRepository.findById(tokenId)).thenReturn(Optional.of(tokenWithId));
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ApiToken result = apiTokenService.revokeToken(tokenId);

            assertThat(result.isRevoked()).isTrue();
            verify(tokenRepository).save(any(ApiToken.class));
        }

        @Test
        @DisplayName("should throw for non-existent token")
        void shouldThrowForNonExistentToken() {
            ApiTokenId tokenId = ApiTokenId.generate();
            when(tokenRepository.findById(tokenId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> apiTokenService.revokeToken(tokenId))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthException.ErrorCode.API_TOKEN_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("deleteToken")
    class DeleteToken {

        @Test
        @DisplayName("should delete existing token")
        void shouldDeleteExistingToken() {
            ApiTokenId tokenId = ApiTokenId.generate();
            ApiToken token = ApiToken.create(testUserId, "test", "$2a$10$hash", Set.of("read"), null);

            when(tokenRepository.findById(tokenId)).thenReturn(Optional.of(token));
            when(tokenRepository.deleteById(tokenId)).thenReturn(true);

            boolean result = apiTokenService.deleteToken(tokenId);

            assertThat(result).isTrue();
            verify(tokenRepository).deleteById(tokenId);
        }

        @Test
        @DisplayName("should throw for non-existent token")
        void shouldThrowForNonExistentToken() {
            ApiTokenId tokenId = ApiTokenId.generate();
            when(tokenRepository.findById(tokenId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> apiTokenService.deleteToken(tokenId))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthException.ErrorCode.API_TOKEN_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("findByUserId")
    class FindByUserId {

        @Test
        @DisplayName("should return tokens for user")
        void shouldReturnTokensForUser() {
            ApiToken token1 = ApiToken.create(testUserId, "token1", "$2a$10$hash1", Set.of("read"), null);
            ApiToken token2 = ApiToken.create(testUserId, "token2", "$2a$10$hash2", Set.of("write"), null);

            when(tokenRepository.findByUserId(testUserId)).thenReturn(List.of(token1, token2));

            List<ApiToken> result = apiTokenService.findByUserId(testUserId);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(ApiToken::name).containsExactlyInAnyOrder("token1", "token2");
        }

        @Test
        @DisplayName("should return empty list for user with no tokens")
        void shouldReturnEmptyListForUserWithNoTokens() {
            when(tokenRepository.findByUserId(testUserId)).thenReturn(List.of());

            List<ApiToken> result = apiTokenService.findByUserId(testUserId);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("exchangeToken")
    class ExchangeToken {

        @Test
        @DisplayName("should exchange valid API token for session JWT")
        void shouldExchangeValidApiTokenForSessionJwt() {
            ApiToken activeToken = ApiToken.create(testUserId, "test", "$2a$10$hash", Set.of("read", "write"), null);
            when(tokenRepository.findAllActive()).thenReturn(List.of(activeToken));
            when(passwordService.verifyPassword("lat_validtoken", "$2a$10$hash")).thenReturn(true);
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

            ApiTokenService.TokenExchangeResult result = apiTokenService.exchangeToken("lat_validtoken", "127.0.0.1");

            assertThat(result).isNotNull();
            assertThat(result.sessionToken()).isNotBlank();
            assertThat(result.expiresAt()).isAfter(Instant.now());
            assertThat(result.scopes()).containsExactlyInAnyOrder("read", "write");
        }

        @Test
        @DisplayName("should throw for invalid API token")
        void shouldThrowForInvalidApiToken() {
            when(tokenRepository.findAllActive()).thenReturn(List.of());

            assertThatThrownBy(() -> apiTokenService.exchangeToken("lat_invalid", "127.0.0.1"))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthException.ErrorCode.INVALID_API_TOKEN);
        }

        @Test
        @DisplayName("should throw for expired API token")
        void shouldThrowForExpiredApiToken() {
            Instant pastExpiry = Instant.now().minus(1, ChronoUnit.DAYS);
            ApiToken expiredToken = new ApiToken(
                    ApiTokenId.generate(),
                    testUserId,
                    "test",
                    "$2a$10$hash",
                    Set.of("read"),
                    Instant.now().minus(7, ChronoUnit.DAYS),
                    pastExpiry,
                    null,
                    null,
                    null
            );
            when(tokenRepository.findAllActive()).thenReturn(List.of(expiredToken));
            when(passwordService.verifyPassword("lat_expiredtoken", "$2a$10$hash")).thenReturn(true);

            assertThatThrownBy(() -> apiTokenService.exchangeToken("lat_expiredtoken", "127.0.0.1"))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthException.ErrorCode.API_TOKEN_EXPIRED);
        }

        @Test
        @DisplayName("should throw when user not found during exchange")
        void shouldThrowWhenUserNotFoundDuringExchange() {
            ApiToken activeToken = ApiToken.create(testUserId, "test", "$2a$10$hash", Set.of("read"), null);
            when(tokenRepository.findAllActive()).thenReturn(List.of(activeToken));
            when(passwordService.verifyPassword("lat_validtoken", "$2a$10$hash")).thenReturn(true);
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> apiTokenService.exchangeToken("lat_validtoken", "127.0.0.1"))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthException.ErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("should reject null tokenRepository")
        void shouldRejectNullTokenRepository() {
            assertThatThrownBy(() -> new ApiTokenServiceImpl(null, userRepository, passwordService, config, jwtTokenService))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null userRepository")
        void shouldRejectNullUserRepository() {
            assertThatThrownBy(() -> new ApiTokenServiceImpl(tokenRepository, null, passwordService, config, jwtTokenService))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null passwordService")
        void shouldRejectNullPasswordService() {
            assertThatThrownBy(() -> new ApiTokenServiceImpl(tokenRepository, userRepository, null, config, jwtTokenService))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null config")
        void shouldRejectNullConfig() {
            assertThatThrownBy(() -> new ApiTokenServiceImpl(tokenRepository, userRepository, passwordService, null, jwtTokenService))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null jwtTokenService")
        void shouldRejectNullJwtTokenService() {
            assertThatThrownBy(() -> new ApiTokenServiceImpl(tokenRepository, userRepository, passwordService, config, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
