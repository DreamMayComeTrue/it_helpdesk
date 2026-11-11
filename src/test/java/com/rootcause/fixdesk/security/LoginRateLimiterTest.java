package com.rootcause.fixdesk.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.rootcause.fixdesk.config.AppProperties;

import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kuala_Lumpur");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-11T02:00:00Z"), ZONE);
    private static final LocalDateTime WINDOW_START = LocalDateTime.now(CLOCK).minusMinutes(15);

    private final LoginAttemptDao dao = mock(LoginAttemptDao.class);
    private final LoginRateLimiter limiter = new LoginRateLimiter(dao, CLOCK,
            new AppProperties(new AppProperties.Uploads("uploads"), new AppProperties.Security(5, 15, 8)));

    @Test
    void notLockedBelowTheLimit() {
        when(dao.countRecentFailures("a@utm.my", "10.0.0.1", WINDOW_START)).thenReturn(4);

        assertThat(limiter.isLocked("a@utm.my", "10.0.0.1")).isFalse();
    }

    @Test
    void lockedAtTheLimit() {
        when(dao.countRecentFailures("a@utm.my", "10.0.0.1", WINDOW_START)).thenReturn(5);

        assertThat(limiter.isLocked("a@utm.my", "10.0.0.1")).isTrue();
    }

    @Test
    void lockIsPerEmailAndIpPair() {
        when(dao.countRecentFailures("a@utm.my", "10.0.0.1", WINDOW_START)).thenReturn(9);

        assertThat(limiter.isLocked("a@utm.my", "10.0.0.2")).isFalse();
        assertThat(limiter.isLocked("b@utm.my", "10.0.0.1")).isFalse();
    }

    @Test
    void recordStoresTheAttempt() {
        limiter.record("a@utm.my", "10.0.0.1", false);

        verify(dao).insert("a@utm.my", "10.0.0.1", false);
    }
}
