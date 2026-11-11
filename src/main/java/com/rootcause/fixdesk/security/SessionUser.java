package com.rootcause.fixdesk.security;

import java.io.Serializable;

import com.rootcause.fixdesk.common.Role;

/** The small identity kept in the HTTP session after login. Nothing sensitive goes here. */
public record SessionUser(long id, String name, Role role) implements Serializable {

    /** Session attribute name. Templates read it as {@code session.currentUser}. */
    public static final String ATTR = "currentUser";

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isTechnician() {
        return role == Role.TECHNICIAN;
    }

    public boolean isStaff() {
        return role.isStaff();
    }
}
