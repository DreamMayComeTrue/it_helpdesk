package com.rootcause.fixdesk.common;

/** The three roles stored in users.role. "Staff" means TECHNICIAN or ADMIN; it is not a role. */
public enum Role {

    USER("/tickets/mine"),
    TECHNICIAN("/triage/queue"),
    ADMIN("/reports");

    private final String startPage;

    Role(String startPage) {
        this.startPage = startPage;
    }

    /** Where this role lands after login. */
    public String startPage() {
        return startPage;
    }

    public boolean isStaff() {
        return this == TECHNICIAN || this == ADMIN;
    }
}
