package com.rootcause.fixdesk.user;

import java.util.Locale;
import java.util.Optional;

import com.rootcause.fixdesk.common.AuditService;
import com.rootcause.fixdesk.common.Role;
import com.rootcause.fixdesk.security.LoginRateLimiter;
import com.rootcause.fixdesk.security.PasswordService;
import com.rootcause.fixdesk.security.SessionUser;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private final UserDao userDao;
    private final PasswordService passwordService;
    private final LoginRateLimiter rateLimiter;
    private final AuditService auditService;

    public UserServiceImpl(UserDao userDao, PasswordService passwordService, LoginRateLimiter rateLimiter,
                           AuditService auditService) {
        this.userDao = userDao;
        this.passwordService = passwordService;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
    }

    @Override
    public Optional<SessionUser> authenticate(String email, String password, String ip) {
        String key = normaliseEmail(email);
        if (rateLimiter.isLocked(key, ip)) {
            auditService.log(null, "LOGIN_LOCKED", "USER", null, key, ip);
            return Optional.empty();
        }

        Optional<User> found = userDao.findByEmail(key);
        boolean passwordOk;
        if (found.isPresent()) {
            passwordOk = passwordService.matches(password, found.get().passwordHash());
        } else {
            passwordService.matchesDummy(password);
            passwordOk = false;
        }
        boolean success = passwordOk && found.get().active();

        rateLimiter.record(key, ip, success);
        if (!success) {
            Long knownId = found.map(User::id).orElse(null);
            auditService.log(knownId, "LOGIN_FAILED", "USER", knownId, key, ip);
            return Optional.empty();
        }
        User user = found.get();
        auditService.log(user.id(), "LOGIN_SUCCESS", "USER", user.id(), null, ip);
        return Optional.of(new SessionUser(user.id(), user.name(), user.role()));
    }

    @Override
    public long register(String name, String email, String password, String ip) {
        String key = normaliseEmail(email);
        if (passwordService.validate(password).isPresent()) {
            throw new IllegalArgumentException("Password breaks the length rule");
        }
        if (userDao.existsByEmail(key)) {
            throw new EmailAlreadyUsedException();
        }
        long id;
        try {
            id = userDao.insert(name.trim(), key, passwordService.hash(password), Role.USER);
        } catch (DuplicateKeyException e) {
            // Two registrations with the same email at the same moment.
            throw new EmailAlreadyUsedException();
        }
        auditService.log(id, "USER_REGISTERED", "USER", id, key, ip);
        return id;
    }

    @Override
    public boolean isActive(long userId) {
        return userDao.isActive(userId);
    }

    @Override
    public void recordLogout(SessionUser user, String ip) {
        auditService.log(user.id(), "LOGOUT", "USER", user.id(), null, ip);
    }

    private static String normaliseEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
