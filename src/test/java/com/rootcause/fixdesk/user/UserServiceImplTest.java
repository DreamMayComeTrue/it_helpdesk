package com.rootcause.fixdesk.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import com.rootcause.fixdesk.common.AuditService;
import com.rootcause.fixdesk.common.Role;
import com.rootcause.fixdesk.security.LoginRateLimiter;
import com.rootcause.fixdesk.security.PasswordService;
import com.rootcause.fixdesk.security.SessionUser;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class UserServiceImplTest {

    private static final String IP = "10.0.0.1";
    private static final String EMAIL = "aina@utm.my";

    private final UserDao userDao = mock(UserDao.class);
    private final PasswordService passwords = mock(PasswordService.class);
    private final LoginRateLimiter limiter = mock(LoginRateLimiter.class);
    private final AuditService audit = mock(AuditService.class);
    private final UserServiceImpl service = new UserServiceImpl(userDao, passwords, limiter, audit);

    private static User user(boolean active, Role role) {
        return new User(5, "Aina", EMAIL, "stored-hash", role, active, "OFF_DUTY", LocalDateTime.now());
    }

    // ---- authenticate ----

    @Test
    void correctPasswordLogsInAndIsAudited() {
        when(userDao.findByEmail(EMAIL)).thenReturn(Optional.of(user(true, Role.TECHNICIAN)));
        when(passwords.matches("secret-pw", "stored-hash")).thenReturn(true);

        Optional<SessionUser> result = service.authenticate(EMAIL, "secret-pw", IP);

        assertThat(result).contains(new SessionUser(5, "Aina", Role.TECHNICIAN));
        verify(limiter).record(EMAIL, IP, true);
        verify(audit).log(5L, "LOGIN_SUCCESS", "USER", 5L, null, IP);
    }

    @Test
    void emailIsTrimmedAndLowerCased() {
        when(userDao.findByEmail(EMAIL)).thenReturn(Optional.of(user(true, Role.USER)));
        when(passwords.matches("secret-pw", "stored-hash")).thenReturn(true);

        assertThat(service.authenticate("  Aina@UTM.my ", "secret-pw", IP)).isPresent();
        verify(limiter).isLocked(EMAIL, IP);
    }

    @Test
    void wrongPasswordFailsAndIsRecorded() {
        when(userDao.findByEmail(EMAIL)).thenReturn(Optional.of(user(true, Role.USER)));
        when(passwords.matches("bad", "stored-hash")).thenReturn(false);

        assertThat(service.authenticate(EMAIL, "bad", IP)).isEmpty();
        verify(limiter).record(EMAIL, IP, false);
        verify(audit).log(5L, "LOGIN_FAILED", "USER", 5L, EMAIL, IP);
    }

    @Test
    void unknownEmailFailsTheSameWayAndStillSpendsHashingTime() {
        when(userDao.findByEmail("nobody@utm.my")).thenReturn(Optional.empty());

        assertThat(service.authenticate("nobody@utm.my", "whatever", IP)).isEmpty();
        verify(passwords).matchesDummy("whatever");
        verify(limiter).record("nobody@utm.my", IP, false);
        verify(audit).log(isNull(), eq("LOGIN_FAILED"), eq("USER"), isNull(), eq("nobody@utm.my"), eq(IP));
    }

    @Test
    void deactivatedAccountCannotLogInEvenWithTheRightPassword() {
        when(userDao.findByEmail(EMAIL)).thenReturn(Optional.of(user(false, Role.USER)));
        when(passwords.matches("secret-pw", "stored-hash")).thenReturn(true);

        assertThat(service.authenticate(EMAIL, "secret-pw", IP)).isEmpty();
        verify(limiter).record(EMAIL, IP, false);
        verify(audit).log(5L, "LOGIN_FAILED", "USER", 5L, EMAIL, IP);
    }

    @Test
    void lockedPairIsRefusedBeforeThePasswordIsEvenChecked() {
        when(limiter.isLocked(EMAIL, IP)).thenReturn(true);

        assertThat(service.authenticate(EMAIL, "secret-pw", IP)).isEmpty();
        verify(userDao, never()).findByEmail(anyString());
        verify(passwords, never()).matches(anyString(), anyString());
        verify(audit).log(isNull(), eq("LOGIN_LOCKED"), eq("USER"), isNull(), eq(EMAIL), eq(IP));
    }

    @Test
    void passwordNeverReachesTheAuditLog() {
        when(userDao.findByEmail(EMAIL)).thenReturn(Optional.of(user(true, Role.USER)));

        service.authenticate(EMAIL, "top-secret-password", IP);

        verify(audit).log(any(), anyString(), any(), any(), eq(EMAIL), eq(IP));
        verifyNoMoreInteractions(audit);
    }

    // ---- register ----

    @Test
    void registerAlwaysCreatesAUserRoleAccountWithAHashedPassword() {
        when(passwords.validate("long-enough-pw")).thenReturn(Optional.empty());
        when(passwords.hash("long-enough-pw")).thenReturn("new-hash");
        when(userDao.insert("Aina Binti Ali", EMAIL, "new-hash", Role.USER)).thenReturn(31L);

        long id = service.register("  Aina Binti Ali ", " Aina@UTM.my", "long-enough-pw", IP);

        assertThat(id).isEqualTo(31L);
        verify(userDao).insert("Aina Binti Ali", EMAIL, "new-hash", Role.USER);
        verify(audit).log(31L, "USER_REGISTERED", "USER", 31L, EMAIL, IP);
    }

    @Test
    void registerRefusesAnEmailThatAlreadyHasAnAccount() {
        when(passwords.validate(anyString())).thenReturn(Optional.empty());
        when(userDao.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> service.register("Aina", EMAIL, "long-enough-pw", IP))
                .isInstanceOf(EmailAlreadyUsedException.class);
        verify(userDao, never()).insert(anyString(), anyString(), anyString(), any());
        verify(audit, never()).log(any(), anyString(), any(), any(), any(), any());
    }

    @Test
    void registerTurnsADuplicateKeyRaceIntoTheSameError() {
        when(passwords.validate(anyString())).thenReturn(Optional.empty());
        when(passwords.hash(anyString())).thenReturn("new-hash");
        when(userDao.insert(anyString(), anyString(), anyString(), any()))
                .thenThrow(new DuplicateKeyException("uk email"));

        assertThatThrownBy(() -> service.register("Aina", EMAIL, "long-enough-pw", IP))
                .isInstanceOf(EmailAlreadyUsedException.class);
    }

    @Test
    void registerRefusesAPasswordThatBreaksTheLengthRule() {
        when(passwords.validate("short")).thenReturn(Optional.of("too short"));

        assertThatThrownBy(() -> service.register("Aina", EMAIL, "short", IP))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userDao, never()).insert(anyString(), anyString(), anyString(), any());
    }

    // ---- other ----

    @Test
    void isActiveAsksTheDao() {
        when(userDao.isActive(5)).thenReturn(true);

        assertThat(service.isActive(5)).isTrue();
        assertThat(service.isActive(6)).isFalse();
        verify(userDao).isActive(5);
    }

    @Test
    void logoutIsAudited() {
        service.recordLogout(new SessionUser(5, "Aina", Role.USER), IP);

        verify(audit).log(5L, "LOGOUT", "USER", 5L, null, IP);
    }
}
