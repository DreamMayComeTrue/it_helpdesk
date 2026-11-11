package com.rootcause.fixdesk.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.rootcause.fixdesk.config.AppProperties;

import org.junit.jupiter.api.Test;

class PasswordServiceTest {

    private final PasswordService passwords = new PasswordService(
            new AppProperties(new AppProperties.Uploads("uploads"), new AppProperties.Security(5, 15, 8)));

    @Test
    void hashIsBcryptAndNeverThePlainPassword() {
        String hash = passwords.hash("correct horse");

        assertThat(hash).startsWith("$2").doesNotContain("correct horse");
        assertThat(passwords.matches("correct horse", hash)).isTrue();
        assertThat(passwords.matches("wrong horse", hash)).isFalse();
    }

    @Test
    void samePasswordHashesDifferentlyEachTime() {
        assertThat(passwords.hash("correct horse")).isNotEqualTo(passwords.hash("correct horse"));
    }

    @Test
    void matchesIsFalseForMissingValues() {
        assertThat(passwords.matches(null, passwords.hash("x"))).isFalse();
        assertThat(passwords.matches("x", null)).isFalse();
    }

    @Test
    void lengthRuleUsesTheConfiguredMinimum() {
        assertThat(passwords.validate("1234567")).isPresent();
        assertThat(passwords.validate(null)).isPresent();
        assertThat(passwords.validate("12345678")).isEmpty();
    }

    @Test
    void passwordsLongerThanBcryptReadsAreRefused() {
        assertThat(passwords.validate("a".repeat(72))).isEmpty();
        assertThat(passwords.validate("a".repeat(73))).isPresent();
    }
}
