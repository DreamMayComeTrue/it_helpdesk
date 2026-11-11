package com.rootcause.fixdesk.user;

import java.time.LocalDateTime;

import com.rootcause.fixdesk.common.Role;

/** One row of the users table. Never sent to a view: pages use form objects or SessionUser. */
public record User(long id, String name, String email, String passwordHash, Role role, boolean active,
                   String dutyStatus, LocalDateTime createdAt) {
}
