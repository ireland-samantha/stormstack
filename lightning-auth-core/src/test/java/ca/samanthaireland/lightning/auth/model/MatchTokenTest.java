/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.lightning.auth.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class MatchTokenTest {

    private static final String MATCH_ID = "match-123";
    private static final String CONTAINER_ID = "container-456";
    private static final String PLAYER_ID = "player-789";
    private static final String PLAYER_NAME = "TestPlayer";

    @Test
    void create_generatesIdAndTimestamps() {
        Instant expiresAt = Instant.now().plus(8, ChronoUnit.HOURS);

        MatchToken token = MatchToken.create(
                MATCH_ID,
                CONTAINER_ID,
                PLAYER_ID,
                null,
                PLAYER_NAME,
                Set.of(MatchToken.SCOPE_SUBMIT_COMMANDS),
                expiresAt
        );

        assertThat(token.id()).isNotNull();
        assertThat(token.createdAt()).isNotNull();
        assertThat(token.matchId()).isEqualTo(MATCH_ID);
        assertThat(token.containerId()).isEqualTo(CONTAINER_ID);
        assertThat(token.playerId()).isEqualTo(PLAYER_ID);
        assertThat(token.playerName()).isEqualTo(PLAYER_NAME);
        assertThat(token.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void create_withNullContainerId_succeeds() {
        MatchToken token = MatchToken.create(
                MATCH_ID,
                null, // null container ID allowed
                PLAYER_ID,
                null,
                PLAYER_NAME,
                Set.of(MatchToken.SCOPE_VIEW_SNAPSHOTS),
                Instant.now().plus(1, ChronoUnit.HOURS)
        );

        assertThat(token.containerId()).isNull();
    }

    @Test
    void create_withUserId_includesUserId() {
        UserId userId = UserId.generate();

        MatchToken token = MatchToken.create(
                MATCH_ID,
                CONTAINER_ID,
                PLAYER_ID,
                userId,
                PLAYER_NAME,
                Set.of(MatchToken.SCOPE_VIEW_SNAPSHOTS),
                Instant.now().plus(1, ChronoUnit.HOURS)
        );

        assertThat(token.userId()).isEqualTo(userId);
    }

    @Test
    void isActive_whenNotRevokedAndNotExpired_returnsTrue() {
        MatchToken token = MatchToken.create(
                MATCH_ID,
                CONTAINER_ID,
                PLAYER_ID,
                null,
                PLAYER_NAME,
                Set.of(MatchToken.SCOPE_VIEW_SNAPSHOTS),
                Instant.now().plus(1, ChronoUnit.HOURS)
        );

        assertThat(token.isActive()).isTrue();
    }

    @Test
    void isActive_whenRevoked_returnsFalse() {
        MatchToken token = MatchToken.create(
                MATCH_ID,
                CONTAINER_ID,
                PLAYER_ID,
                null,
                PLAYER_NAME,
                Set.of(MatchToken.SCOPE_VIEW_SNAPSHOTS),
                Instant.now().plus(1, ChronoUnit.HOURS)
        );

        MatchToken revoked = token.revoke();

        assertThat(revoked.isActive()).isFalse();
        assertThat(revoked.isRevoked()).isTrue();
        assertThat(revoked.revokedAt()).isNotNull();
    }

    @Test
    void isActive_whenExpired_returnsFalse() {
        MatchToken token = MatchToken.create(
                MATCH_ID,
                CONTAINER_ID,
                PLAYER_ID,
                null,
                PLAYER_NAME,
                Set.of(MatchToken.SCOPE_VIEW_SNAPSHOTS),
                Instant.now().minus(1, ChronoUnit.HOURS) // Expired
        );

        assertThat(token.isActive()).isFalse();
        assertThat(token.isExpired()).isTrue();
    }

    @Test
    void isValidForMatch_whenMatchesMatchId_returnsTrue() {
        MatchToken token = MatchToken.create(
                MATCH_ID,
                CONTAINER_ID,
                PLAYER_ID,
                null,
                PLAYER_NAME,
                Set.of(MatchToken.SCOPE_VIEW_SNAPSHOTS),
                Instant.now().plus(1, ChronoUnit.HOURS)
        );

        assertThat(token.isValidForMatch(MATCH_ID)).isTrue();
        assertThat(token.isValidForMatch("other-match")).isFalse();
    }

    @Test
    void isValidForMatchAndContainer_whenBothMatch_returnsTrue() {
        MatchToken token = MatchToken.create(
                MATCH_ID,
                CONTAINER_ID,
                PLAYER_ID,
                null,
                PLAYER_NAME,
                Set.of(MatchToken.SCOPE_VIEW_SNAPSHOTS),
                Instant.now().plus(1, ChronoUnit.HOURS)
        );

        assertThat(token.isValidForMatchAndContainer(MATCH_ID, CONTAINER_ID)).isTrue();
        assertThat(token.isValidForMatchAndContainer(MATCH_ID, "other-container")).isFalse();
        assertThat(token.isValidForMatchAndContainer("other-match", CONTAINER_ID)).isFalse();
    }

    @Test
    void isValidForMatchAndContainer_whenNoContainerId_allowsAnyContainer() {
        MatchToken token = MatchToken.create(
                MATCH_ID,
                null, // No container restriction
                PLAYER_ID,
                null,
                PLAYER_NAME,
                Set.of(MatchToken.SCOPE_VIEW_SNAPSHOTS),
                Instant.now().plus(1, ChronoUnit.HOURS)
        );

        assertThat(token.isValidForMatchAndContainer(MATCH_ID, "any-container")).isTrue();
    }

    @Test
    void hasScope_checksForScope() {
        MatchToken token = MatchToken.create(
                MATCH_ID,
                CONTAINER_ID,
                PLAYER_ID,
                null,
                PLAYER_NAME,
                Set.of(MatchToken.SCOPE_SUBMIT_COMMANDS, MatchToken.SCOPE_VIEW_SNAPSHOTS),
                Instant.now().plus(1, ChronoUnit.HOURS)
        );

        assertThat(token.hasScope(MatchToken.SCOPE_SUBMIT_COMMANDS)).isTrue();
        assertThat(token.hasScope(MatchToken.SCOPE_VIEW_SNAPSHOTS)).isTrue();
        assertThat(token.hasScope(MatchToken.SCOPE_RECEIVE_ERRORS)).isFalse();
    }

    @Test
    void withJwt_addsJwtToken() {
        MatchToken token = MatchToken.create(
                MATCH_ID,
                CONTAINER_ID,
                PLAYER_ID,
                null,
                PLAYER_NAME,
                Set.of(MatchToken.SCOPE_VIEW_SNAPSHOTS),
                Instant.now().plus(1, ChronoUnit.HOURS)
        );

        assertThat(token.jwtToken()).isNull();

        MatchToken withJwt = token.withJwt("jwt-token-string");

        assertThat(withJwt.jwtToken()).isEqualTo("jwt-token-string");
        assertThat(withJwt.id()).isEqualTo(token.id());
    }

    @Test
    void withoutJwt_removesJwtToken() {
        MatchToken token = MatchToken.create(
                MATCH_ID,
                CONTAINER_ID,
                PLAYER_ID,
                null,
                PLAYER_NAME,
                Set.of(MatchToken.SCOPE_VIEW_SNAPSHOTS),
                Instant.now().plus(1, ChronoUnit.HOURS)
        ).withJwt("jwt-token-string");

        assertThat(token.jwtToken()).isNotNull();

        MatchToken withoutJwt = token.withoutJwt();

        assertThat(withoutJwt.jwtToken()).isNull();
        assertThat(withoutJwt.id()).isEqualTo(token.id());
    }

    @Test
    void createWithDefaultScopes_includesAllDefaultScopes() {
        MatchToken token = MatchToken.createWithDefaultScopes(
                MATCH_ID,
                CONTAINER_ID,
                PLAYER_ID,
                null,
                PLAYER_NAME,
                Instant.now().plus(1, ChronoUnit.HOURS)
        );

        assertThat(token.scopes()).containsExactlyInAnyOrder(
                MatchToken.SCOPE_SUBMIT_COMMANDS,
                MatchToken.SCOPE_VIEW_SNAPSHOTS,
                MatchToken.SCOPE_RECEIVE_ERRORS
        );
    }
}
