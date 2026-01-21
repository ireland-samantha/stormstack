package ca.samanthaireland.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthBootstrap")
class AuthBootstrapTest {

    @Nested
    @DisplayName("createDefault")
    class CreateDefault {

        @Test
        @DisplayName("should create all services")
        void shouldCreateAllServices() {
            AuthBootstrap bootstrap = AuthBootstrap.createDefault();

            assertThat(bootstrap.getUserService()).isNotNull();
            assertThat(bootstrap.getPasswordService()).isNotNull();
            assertThat(bootstrap.getUserRepository()).isNotNull();
            assertThat(bootstrap.getAuthService()).isNotNull();
            assertThat(bootstrap.getRoleService()).isNotNull();
            assertThat(bootstrap.getRoleRepository()).isNotNull();
        }

        @Test
        @DisplayName("should create default admin user")
        void shouldCreateDefaultAdminUser() {
            AuthBootstrap bootstrap = AuthBootstrap.createDefault();

            Optional<User> admin = bootstrap.getUserRepository().findByUsername("admin");

            assertThat(admin).isPresent();
            assertThat(admin.get().username()).isEqualTo("admin");
            assertThat(admin.get().roles()).containsExactly("admin");
            assertThat(admin.get().enabled()).isTrue();
        }

        @Test
        @DisplayName("should create default roles")
        void shouldCreateDefaultRoles() {
            AuthBootstrap bootstrap = AuthBootstrap.createDefault();

            assertThat(bootstrap.getRoleRepository().existsByName("admin")).isTrue();
            assertThat(bootstrap.getRoleRepository().existsByName("command_manager")).isTrue();
            assertThat(bootstrap.getRoleRepository().existsByName("view_only")).isTrue();
        }

        @Test
        @DisplayName("admin should be able to login with configured password")
        void adminShouldBeAbleToLoginWithConfiguredPassword() {
            // Set a known password via system property for testing
            String testPassword = "test-admin-password";
            System.setProperty("admin.initial.password", testPassword);
            try {
                AuthBootstrap bootstrap = AuthBootstrap.createDefault();

                AuthToken token = bootstrap.getAuthService().login("admin", testPassword);

                assertThat(token).isNotNull();
                assertThat(token.username()).isEqualTo("admin");
                assertThat(token.isAdmin()).isTrue();
            } finally {
                System.clearProperty("admin.initial.password");
            }
        }
    }

    @Nested
    @DisplayName("initializeDefaults")
    class InitializeDefaults {

        @Test
        @DisplayName("should not duplicate admin user on multiple calls")
        void shouldNotDuplicateAdminUser() {
            AuthBootstrap bootstrap = AuthBootstrap.createDefault();
            long countBefore = bootstrap.getUserRepository().count();

            bootstrap.initializeDefaults();
            bootstrap.initializeDefaults();

            long countAfter = bootstrap.getUserRepository().count();
            assertThat(countAfter).isEqualTo(countBefore);
        }

        @Test
        @DisplayName("should not duplicate roles on multiple calls")
        void shouldNotDuplicateRoles() {
            AuthBootstrap bootstrap = AuthBootstrap.createDefault();
            long countBefore = bootstrap.getRoleRepository().count();

            bootstrap.initializeDefaults();
            bootstrap.initializeDefaults();

            long countAfter = bootstrap.getRoleRepository().count();
            assertThat(countAfter).isEqualTo(countBefore);
        }
    }
}
