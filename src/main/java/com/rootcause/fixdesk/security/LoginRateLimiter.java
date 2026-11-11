package com.rootcause.fixdesk.security;

import java.time.Clock;
import java.time.LocalDateTime;

import com.rootcause.fixdesk.config.AppProperties;

import org.springframework.stereotype.Service;

/**
 * Locks an email + IP pair after repeated failed logins inside a time window.
 * The lock lifts by itself once the failures fall out of the window, and a
 * successful login clears the count.
 */
@Service
public class LoginRateLimiter {

    private final LoginAttemptDao attempts;
    private final Clock clock;
    private final int maxFailures;
    private final int windowMinutes;

    public LoginRateLimiter(LoginAttemptDao attempts, Clock clock, AppProperties properties) {
        this.attempts = attempts;
        this.clock = clock;
        this.maxFailures = properties.security().loginMaxFailures();
        this.windowMinutes = properties.security().loginWindowMinutes();
    }

    public boolean isLocked(String email, String ip) {
        LocalDateTime since = LocalDateTime.now(clock).minusMinutes(windowMinutes);
        return attempts.countRecentFailures(email, ip, since) >= maxFailures;
    }

    public void record(String email, String ip, boolean success) {
        attempts.insert(email, ip, success);
    }
}
