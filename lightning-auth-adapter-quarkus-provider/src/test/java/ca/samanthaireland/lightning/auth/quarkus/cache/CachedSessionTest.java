package ca.samanthaireland.lightning.auth.quarkus.cache;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CachedSessionTest {

    @Test
    void isExpired_withFutureExpiry_returnsFalse() {
        CachedSession session = new CachedSession(
                "session.jwt",
                Instant.now().plusSeconds(3600),
                Set.of("read")
        );

        assertThat(session.isExpired()).isFalse();
    }

    @Test
    void isExpired_withPastExpiry_returnsTrue() {
        CachedSession session = new CachedSession(
                "session.jwt",
                Instant.now().minusSeconds(1),
                Set.of("read")
        );

        assertThat(session.isExpired()).isTrue();
    }

    @Test
    void isExpired_withCurrentTime_returnsTrue() {
        // Expiry at current instant should be considered expired
        CachedSession session = new CachedSession(
                "session.jwt",
                Instant.now(),
                Set.of("read")
        );

        // Give a small buffer for test execution
        assertThat(session.isExpired()).isTrue();
    }

    @Test
    void recordFields_areAccessible() {
        Instant expiry = Instant.now().plusSeconds(3600);
        Set<String> scopes = Set.of("read", "write");

        CachedSession session = new CachedSession("session.jwt", expiry, scopes);

        assertThat(session.sessionToken()).isEqualTo("session.jwt");
        assertThat(session.expiresAt()).isEqualTo(expiry);
        assertThat(session.scopes()).isEqualTo(scopes);
    }
}
