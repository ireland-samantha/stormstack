/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.lightning.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PasswordServiceImplTest {

    private PasswordServiceImpl passwordService;

    @BeforeEach
    void setUp() {
        // Use lower cost for faster tests
        passwordService = new PasswordServiceImpl(4);
    }

    @Test
    void hashPassword_producesValidBcryptHash() {
        String hash = passwordService.hashPassword("testpassword");

        assertThat(hash).startsWith("$2");
        assertThat(hash).hasSize(60);
    }

    @Test
    void hashPassword_producesUniqueHashesForSamePassword() {
        String hash1 = passwordService.hashPassword("testpassword");
        String hash2 = passwordService.hashPassword("testpassword");

        assertThat(hash1).isNotEqualTo(hash2); // Different salts
    }

    @Test
    void hashPassword_rejectsNullPassword() {
        assertThatThrownBy(() -> passwordService.hashPassword(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hashPassword_rejectsEmptyPassword() {
        assertThatThrownBy(() -> passwordService.hashPassword(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verifyPassword_returnsTrueForCorrectPassword() {
        String hash = passwordService.hashPassword("testpassword");

        assertThat(passwordService.verifyPassword("testpassword", hash)).isTrue();
    }

    @Test
    void verifyPassword_returnsFalseForIncorrectPassword() {
        String hash = passwordService.hashPassword("testpassword");

        assertThat(passwordService.verifyPassword("wrongpassword", hash)).isFalse();
    }

    @Test
    void verifyPassword_returnsFalseForNullPassword() {
        String hash = passwordService.hashPassword("testpassword");

        assertThat(passwordService.verifyPassword(null, hash)).isFalse();
    }

    @Test
    void verifyPassword_returnsFalseForNullHash() {
        assertThat(passwordService.verifyPassword("testpassword", null)).isFalse();
    }

    @Test
    void needsRehash_returnsTrueForLowerCostHash() {
        // Create service with cost 4, then check against cost 12
        String lowCostHash = passwordService.hashPassword("testpassword");
        PasswordServiceImpl highCostService = new PasswordServiceImpl(12);

        assertThat(highCostService.needsRehash(lowCostHash)).isTrue();
    }

    @Test
    void needsRehash_returnsFalseForCurrentCostHash() {
        String hash = passwordService.hashPassword("testpassword");

        assertThat(passwordService.needsRehash(hash)).isFalse();
    }

    @Test
    void needsRehash_returnsTrueForInvalidHash() {
        assertThat(passwordService.needsRehash("invalid")).isTrue();
        assertThat(passwordService.needsRehash("")).isTrue();
        assertThat(passwordService.needsRehash(null)).isTrue();
    }

    @Test
    void constructor_rejectsCostBelowMinimum() {
        assertThatThrownBy(() -> new PasswordServiceImpl(3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cost");
    }

    @Test
    void constructor_rejectsCostAboveMaximum() {
        assertThatThrownBy(() -> new PasswordServiceImpl(32))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cost");
    }

    @Test
    void getCost_returnsConfiguredCost() {
        assertThat(passwordService.getCost()).isEqualTo(4);
    }
}
