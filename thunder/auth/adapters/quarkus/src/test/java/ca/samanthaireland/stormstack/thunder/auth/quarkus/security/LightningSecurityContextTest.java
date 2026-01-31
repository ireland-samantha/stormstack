package ca.samanthaireland.stormstack.thunder.auth.quarkus.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LightningSecurityContextTest {

    @Test
    void getUserPrincipal_returnsPrincipal() {
        LightningPrincipal principal = new LightningPrincipal(
                "user-123",
                "testuser",
                Set.of("read"),
                "token-456"
        );
        LightningSecurityContext context = new LightningSecurityContext(principal, true);

        assertThat(context.getUserPrincipal()).isEqualTo(principal);
    }

    @Test
    void getLightningPrincipal_returnsPrincipal() {
        LightningPrincipal principal = new LightningPrincipal(
                "user-123",
                "testuser",
                Set.of("read"),
                "token-456"
        );
        LightningSecurityContext context = new LightningSecurityContext(principal, true);

        assertThat(context.getLightningPrincipal()).isEqualTo(principal);
    }

    @Test
    void isUserInRole_withMatchingScope_returnsTrue() {
        LightningPrincipal principal = new LightningPrincipal(
                "user-123",
                "testuser",
                Set.of("admin", "read"),
                "token-456"
        );
        LightningSecurityContext context = new LightningSecurityContext(principal, true);

        assertThat(context.isUserInRole("admin")).isTrue();
        assertThat(context.isUserInRole("read")).isTrue();
    }

    @Test
    void isUserInRole_withNonMatchingScope_returnsFalse() {
        LightningPrincipal principal = new LightningPrincipal(
                "user-123",
                "testuser",
                Set.of("read"),
                "token-456"
        );
        LightningSecurityContext context = new LightningSecurityContext(principal, true);

        assertThat(context.isUserInRole("admin")).isFalse();
        assertThat(context.isUserInRole("write")).isFalse();
    }

    @Test
    void isUserInRole_withNullPrincipal_returnsFalse() {
        LightningSecurityContext context = new LightningSecurityContext(null, true);

        assertThat(context.isUserInRole("admin")).isFalse();
    }

    @Test
    void isSecure_returnsConstructorValue() {
        LightningSecurityContext secureContext = new LightningSecurityContext(null, true);
        LightningSecurityContext insecureContext = new LightningSecurityContext(null, false);

        assertThat(secureContext.isSecure()).isTrue();
        assertThat(insecureContext.isSecure()).isFalse();
    }

    @Test
    void getAuthenticationScheme_returnsBearer() {
        LightningSecurityContext context = new LightningSecurityContext(null, true);

        assertThat(context.getAuthenticationScheme()).isEqualTo("Bearer");
    }
}
