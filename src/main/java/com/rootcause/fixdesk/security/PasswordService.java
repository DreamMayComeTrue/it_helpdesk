package com.rootcause.fixdesk.security;

import java.util.Optional;
import java.util.UUID;

import com.rootcause.fixdesk.config.AppProperties;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/** BCrypt hashing and the password length rule. Passwords are never logged. */
@Service
public class PasswordService {

    /** BCrypt only reads the first 72 bytes, so longer passwords are refused. */
    public static final int MAX_LENGTH = 72;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final int minLength;
    /** A real hash nobody knows the password of; see {@link #matchesDummy}. */
    private final String dummyHash;

    public PasswordService(AppProperties properties) {
        this.minLength = properties.security().passwordMinLength();
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String hash) {
        return rawPassword != null && hash != null && encoder.matches(rawPassword, hash);
    }

    /**
     * Spends the same time as a real check. Called when the email is unknown,
     * so response time does not reveal whether an account exists.
     */
    public void matchesDummy(String rawPassword) {
        matches(rawPassword, dummyHash);
    }

    /** Returns a message when the password breaks the length rule, or empty when it is fine. */
    public Optional<String> validate(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < minLength) {
            return Optional.of("Password must be at least " + minLength + " characters.");
        }
        if (rawPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_LENGTH) {
            return Optional.of("Password must be at most " + MAX_LENGTH + " characters.");
        }
        return Optional.empty();
    }
}
