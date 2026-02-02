/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.stormstack.thunder.auth.service;

import ca.samanthaireland.stormstack.thunder.auth.exception.AuthException;
import ca.samanthaireland.stormstack.thunder.auth.model.GrantType;
import ca.samanthaireland.stormstack.thunder.auth.model.MatchToken;
import ca.samanthaireland.stormstack.thunder.auth.model.OAuth2TokenResponse;
import ca.samanthaireland.stormstack.thunder.auth.model.ServiceClient;
import ca.samanthaireland.stormstack.thunder.auth.service.dto.IssueMatchTokenRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchTokenGrantHandlerTest {

    @Mock
    private MatchTokenService matchTokenService;

    private MatchTokenGrantHandler handler;

    @BeforeEach
    void setUp() {
        handler = new MatchTokenGrantHandler(matchTokenService);
    }

    @Test
    void getGrantType_returnsMatchToken() {
        assertThat(handler.getGrantType()).isEqualTo(GrantType.MATCH_TOKEN);
    }

    @Test
    void handle_withValidRequest_issuesToken() {
        ServiceClient client = ServiceClient.createConfidential(
                "control-plane",
                "secret-hash",
                "Control Plane",
                Set.of("service.match-token.issue"),
                Set.of(GrantType.CLIENT_CREDENTIALS, GrantType.MATCH_TOKEN)
        );

        MatchToken issuedToken = MatchToken.createWithDefaultScopes(
                "match-123",
                "container-456",
                "player-789",
                null,
                "TestPlayer",
                Instant.now().plus(8, ChronoUnit.HOURS)
        ).withJwt("test.match.jwt.token");

        when(matchTokenService.issueToken(any(IssueMatchTokenRequest.class)))
                .thenReturn(issuedToken);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("grant_type", "urn:stormstack:grant-type:match-token");
        parameters.put("match_id", "match-123");
        parameters.put("container_id", "container-456");
        parameters.put("player_id", "player-789");
        parameters.put("player_name", "TestPlayer");
        parameters.put("valid_for_hours", "8");

        OAuth2TokenResponse response = handler.handle(client, parameters);

        assertThat(response.accessToken()).isEqualTo("test.match.jwt.token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(8 * 60 * 60);  // 8 hours in seconds
        assertThat(response.refreshToken()).isNull();
    }

    @Test
    void handle_withDefaultValidForHours_usesDefault() {
        ServiceClient client = ServiceClient.createConfidential(
                "control-plane",
                "secret-hash",
                "Control Plane",
                Set.of("service.match-token.issue"),
                Set.of(GrantType.MATCH_TOKEN)
        );

        MatchToken issuedToken = MatchToken.createWithDefaultScopes(
                "match-123",
                null,
                "player-789",
                null,
                "TestPlayer",
                Instant.now().plus(8, ChronoUnit.HOURS)
        ).withJwt("test.jwt.token");

        ArgumentCaptor<IssueMatchTokenRequest> requestCaptor = ArgumentCaptor.forClass(IssueMatchTokenRequest.class);
        when(matchTokenService.issueToken(requestCaptor.capture()))
                .thenReturn(issuedToken);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("match_id", "match-123");
        parameters.put("player_id", "player-789");
        parameters.put("player_name", "TestPlayer");
        // No valid_for_hours - should default to 8

        OAuth2TokenResponse response = handler.handle(client, parameters);

        IssueMatchTokenRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.validFor()).isEqualTo(Duration.ofHours(8));
    }

    @Test
    void handle_withoutClient_throwsInvalidClient() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("match_id", "match-123");
        parameters.put("player_id", "player-789");
        parameters.put("player_name", "TestPlayer");

        assertThatThrownBy(() -> handler.handle(null, parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_CLIENT);
                });
    }

    @Test
    void handle_withoutRequiredScope_throwsInvalidScope() {
        ServiceClient client = ServiceClient.createConfidential(
                "test-client",
                "secret-hash",
                "Test Client",
                Set.of("other.scope"),  // Missing service.match-token.issue
                Set.of(GrantType.MATCH_TOKEN)
        );

        Map<String, String> parameters = new HashMap<>();
        parameters.put("match_id", "match-123");
        parameters.put("player_id", "player-789");
        parameters.put("player_name", "TestPlayer");

        assertThatThrownBy(() -> handler.handle(client, parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_SCOPE);
                });
    }

    @Test
    void handle_withWildcardScope_succeeds() {
        ServiceClient client = ServiceClient.createConfidential(
                "admin-client",
                "secret-hash",
                "Admin Client",
                Set.of("*"),  // Wildcard allows all
                Set.of(GrantType.MATCH_TOKEN)
        );

        MatchToken issuedToken = MatchToken.createWithDefaultScopes(
                "match-123",
                null,
                "player-789",
                null,
                "TestPlayer",
                Instant.now().plus(8, ChronoUnit.HOURS)
        ).withJwt("admin.jwt.token");

        when(matchTokenService.issueToken(any())).thenReturn(issuedToken);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("match_id", "match-123");
        parameters.put("player_id", "player-789");
        parameters.put("player_name", "TestPlayer");

        OAuth2TokenResponse response = handler.handle(client, parameters);

        assertThat(response.accessToken()).isEqualTo("admin.jwt.token");
    }

    @Test
    void validateRequest_withMissingMatchId_throwsInvalidRequest() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("player_id", "player-789");
        parameters.put("player_name", "TestPlayer");

        assertThatThrownBy(() -> handler.validateRequest(parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_REQUEST);
                    assertThat(ae.getMessage()).contains("match_id");
                });
    }

    @Test
    void validateRequest_withMissingPlayerId_throwsInvalidRequest() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("match_id", "match-123");
        parameters.put("player_name", "TestPlayer");

        assertThatThrownBy(() -> handler.validateRequest(parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_REQUEST);
                    assertThat(ae.getMessage()).contains("player_id");
                });
    }

    @Test
    void validateRequest_withMissingPlayerName_throwsInvalidRequest() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("match_id", "match-123");
        parameters.put("player_id", "player-789");

        assertThatThrownBy(() -> handler.validateRequest(parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_REQUEST);
                    assertThat(ae.getMessage()).contains("player_name");
                });
    }

    @Test
    void validateRequest_withInvalidValidForHours_throwsInvalidRequest() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("match_id", "match-123");
        parameters.put("player_id", "player-789");
        parameters.put("player_name", "TestPlayer");
        parameters.put("valid_for_hours", "0");  // Invalid - must be positive

        assertThatThrownBy(() -> handler.validateRequest(parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_REQUEST);
                    assertThat(ae.getMessage()).contains("valid_for_hours");
                });
    }

    @Test
    void validateRequest_withTooLongValidForHours_throwsInvalidRequest() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("match_id", "match-123");
        parameters.put("player_id", "player-789");
        parameters.put("player_name", "TestPlayer");
        parameters.put("valid_for_hours", "200");  // Max is 168 (7 days)

        assertThatThrownBy(() -> handler.validateRequest(parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_REQUEST);
                    assertThat(ae.getMessage()).contains("valid_for_hours");
                });
    }

    @Test
    void validateRequest_withNonNumericValidForHours_throwsInvalidRequest() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("match_id", "match-123");
        parameters.put("player_id", "player-789");
        parameters.put("player_name", "TestPlayer");
        parameters.put("valid_for_hours", "not-a-number");

        assertThatThrownBy(() -> handler.validateRequest(parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_REQUEST);
                    assertThat(ae.getMessage()).contains("valid_for_hours");
                });
    }

    @Test
    void validateRequest_withValidParameters_succeeds() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("match_id", "match-123");
        parameters.put("player_id", "player-789");
        parameters.put("player_name", "TestPlayer");
        parameters.put("valid_for_hours", "24");

        // Should not throw
        handler.validateRequest(parameters);
    }

    @Test
    void handle_withCustomScopes_passesToService() {
        ServiceClient client = ServiceClient.createConfidential(
                "control-plane",
                "secret-hash",
                "Control Plane",
                Set.of("service.match-token.issue"),
                Set.of(GrantType.MATCH_TOKEN)
        );

        MatchToken issuedToken = MatchToken.create(
                "match-123",
                null,
                "player-789",
                null,
                "TestPlayer",
                Set.of("view_snapshots"),  // Only view, no submit
                Instant.now().plus(8, ChronoUnit.HOURS)
        ).withJwt("limited.jwt.token");

        ArgumentCaptor<IssueMatchTokenRequest> requestCaptor = ArgumentCaptor.forClass(IssueMatchTokenRequest.class);
        when(matchTokenService.issueToken(requestCaptor.capture()))
                .thenReturn(issuedToken);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("match_id", "match-123");
        parameters.put("player_id", "player-789");
        parameters.put("player_name", "TestPlayer");
        parameters.put("scopes", "[\"view_snapshots\"]");

        OAuth2TokenResponse response = handler.handle(client, parameters);

        IssueMatchTokenRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.scopes()).containsExactly("view_snapshots");
    }
}
