package com.rootcause.fixdesk.user;

import java.util.Optional;

import com.rootcause.fixdesk.common.Role;

public interface UserDao {

    Optional<User> findByEmail(String email);

    Optional<User> findById(long id);

    boolean existsByEmail(String email);

    /** False when the user is deactivated or does not exist. */
    boolean isActive(long id);

    /** Inserts an active user and returns the new id. */
    long insert(String name, String email, String passwordHash, Role role);
}
