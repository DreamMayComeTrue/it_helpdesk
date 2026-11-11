package com.rootcause.fixdesk.security;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcLoginAttemptDao implements LoginAttemptDao {

    private final JdbcTemplate jdbc;

    public JdbcLoginAttemptDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(String email, String ip, boolean success) {
        jdbc.update("INSERT INTO login_attempts (email, ip_address, success) VALUES (?, ?, ?)",
                email, ip, success);
    }

    @Override
    public int countRecentFailures(String email, String ip, LocalDateTime since) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM login_attempts
                WHERE email = ? AND ip_address = ? AND success = FALSE
                  AND attempted_at > ?
                  AND attempted_at > COALESCE(
                        (SELECT MAX(ok.attempted_at)
                         FROM login_attempts ok
                         WHERE ok.email = ? AND ok.ip_address = ? AND ok.success = TRUE),
                        '1970-01-01 00:00:00')
                """,
                Integer.class, email, ip, Timestamp.valueOf(since), email, ip);
        return count == null ? 0 : count;
    }
}
