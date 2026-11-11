package com.rootcause.fixdesk.security;

import java.time.LocalDateTime;

/** login_attempts records every login attempt by email and IP, for unknown emails too. */
public interface LoginAttemptDao {

    void insert(String email, String ip, boolean success);

    /**
     * Failed attempts for this email + IP pair after {@code since}, counting only
     * those made after the pair's most recent successful login.
     */
    int countRecentFailures(String email, String ip, LocalDateTime since);
}
