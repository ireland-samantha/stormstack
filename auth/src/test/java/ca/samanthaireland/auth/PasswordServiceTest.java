package ca.samanthaireland.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PasswordService")
class PasswordServiceTest {

    private PasswordService passwordService;

    @BeforeEach
    void setUp() {
        // Use lower cost for faster tests
        passwordService = new PasswordService(4);
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("should accept valid cost factor")
        void shouldAcceptValidCostFactor() {
            PasswordService service = new PasswordService(10);
            assertThat(service.getCost()).isEqualTo(10);
        }

        @Test
        @DisplayName("should use default cost when no argument")
        void shouldUseDefaultCost() {
            PasswordService service = new PasswordService();
            assertThat(service.getCost()).isEqualTo(12);
        }

        @Test
        @DisplayName("should reject cost below minimum")
        void shouldRejectCostBelowMinimum() {
            assertThatThrownBy(() -> new PasswordService(3))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("between 4 and 31");
        }

        @Test
        @DisplayName("should reject cost above maximum")
        void shouldRejectCostAboveMaximum() {
            assertThatThrownBy(() -> new PasswordService(32))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("between 4 and 31");
        }
    }

    @Nested
    @DisplayName("hashPassword")
    class HashPassword {

        @Test
        @DisplayName("should return BCrypt hash")
        void shouldReturnBCryptHash() {
            String hash = passwordService.hashPassword("password123");

            assertThat(hash).startsWith("$2");
            assertThat(hash).hasSize(60);
        }

        @Test
        @DisplayName("should produce different hashes for same password")
        void shouldProduceDifferentHashesForSamePassword() {
            String hash1 = passwordService.hashPassword("password");
            String hash2 = passwordService.hashPassword("password");

            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("should handle empty password")
        void shouldHandleEmptyPassword() {
            String hash = passwordService.hashPassword("");

            assertThat(hash).startsWith("$2");
        }

        @Test
        @DisplayName("should handle unicode password")
        void shouldHandleUnicodePassword() {
            String hash = passwordService.hashPassword("пароль密码🔐");

            assertThat(hash).startsWith("$2");
        }
    }

    @Nested
    @DisplayName("verifyPassword")
    class VerifyPassword {

        @Test
        @DisplayName("should return true for matching password")
        void shouldReturnTrueForMatchingPassword() {
            String password = "correctPassword";
            String hash = passwordService.hashPassword(password);

            boolean result = passwordService.verifyPassword(password, hash);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false for wrong password")
        void shouldReturnFalseForWrongPassword() {
            String hash = passwordService.hashPassword("correctPassword");

            boolean result = passwordService.verifyPassword("wrongPassword", hash);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false for similar password")
        void shouldReturnFalseForSimilarPassword() {
            String hash = passwordService.hashPassword("Password");

            boolean result = passwordService.verifyPassword("password", hash);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should handle empty password verification")
        void shouldHandleEmptyPasswordVerification() {
            String hash = passwordService.hashPassword("");

            assertThat(passwordService.verifyPassword("", hash)).isTrue();
            assertThat(passwordService.verifyPassword("notempty", hash)).isFalse();
        }
    }

    @Nested
    @DisplayName("needsRehash")
    class NeedsRehash {

        @Test
        @DisplayName("should return false when cost matches")
        void shouldReturnFalseWhenCostMatches() {
            String hash = passwordService.hashPassword("password");

            boolean result = passwordService.needsRehash(hash);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true when hash has lower cost")
        void shouldReturnTrueWhenHashHasLowerCost() {
            PasswordService higherCostService = new PasswordService(10);
            PasswordService lowerCostService = new PasswordService(4);

            String lowCostHash = lowerCostService.hashPassword("password");

            boolean result = higherCostService.needsRehash(lowCostHash);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true for invalid hash")
        void shouldReturnTrueForInvalidHash() {
            boolean result = passwordService.needsRehash("invalid-hash");

            assertThat(result).isTrue();
        }
    }
}
