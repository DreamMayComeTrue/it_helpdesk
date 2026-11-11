package com.rootcause.fixdesk.user;

import java.util.Optional;

import com.rootcause.fixdesk.security.SessionUser;

public interface UserService {

    /**
     * Checks the credentials. Empty for every kind of failure (unknown email, wrong
     * password, deactivated account, locked email + IP pair), so the caller cannot
     * tell which one it was. Every attempt is recorded and audited.
     */
    Optional<SessionUser> authenticate(String email, String password, String ip);

    /**
     * Self-registration: always creates a USER. Staff accounts are made by an admin.
     *
     * @return the new user's id
     * @throws EmailAlreadyUsedException when the email already has an account
     */
    long register(String name, String email, String password, String ip);

    /** False when the account was deactivated or deleted; used to end stale sessions. */
    boolean isActive(long userId);

    /** Writes the logout to the audit log. The controller invalidates the session. */
    void recordLogout(SessionUser user, String ip);
}
