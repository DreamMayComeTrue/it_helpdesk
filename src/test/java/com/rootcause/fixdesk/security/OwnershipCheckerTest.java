package com.rootcause.fixdesk.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rootcause.fixdesk.common.ForbiddenException;
import com.rootcause.fixdesk.common.Role;

import org.junit.jupiter.api.Test;

class OwnershipCheckerTest {

    private static final long REPORTER = 10;
    private static final Long ASSIGNEE = 20L;

    private final OwnershipChecker checker = new OwnershipChecker();

    @Test
    void reporterAssigneeAndAdminMayAccess() {
        assertThat(checker.canAccessTicket(new SessionUser(10, "Owner", Role.USER), REPORTER, ASSIGNEE)).isTrue();
        assertThat(checker.canAccessTicket(new SessionUser(20, "Tech", Role.TECHNICIAN), REPORTER, ASSIGNEE)).isTrue();
        assertThat(checker.canAccessTicket(new SessionUser(99, "Admin", Role.ADMIN), REPORTER, ASSIGNEE)).isTrue();
    }

    @Test
    void anotherUserOrAnotherTechnicianMayNot() {
        assertThat(checker.canAccessTicket(new SessionUser(11, "Other", Role.USER), REPORTER, ASSIGNEE)).isFalse();
        assertThat(checker.canAccessTicket(new SessionUser(21, "Tech 2", Role.TECHNICIAN), REPORTER, ASSIGNEE)).isFalse();
    }

    @Test
    void unassignedTicketIsOnlyForReporterAndAdmin() {
        assertThat(checker.canAccessTicket(new SessionUser(20, "Tech", Role.TECHNICIAN), REPORTER, null)).isFalse();
        assertThat(checker.canAccessTicket(new SessionUser(10, "Owner", Role.USER), REPORTER, null)).isTrue();
    }

    @Test
    void noSessionUserMeansNoAccess() {
        assertThat(checker.canAccessTicket(null, REPORTER, ASSIGNEE)).isFalse();
    }

    @Test
    void requireThrowsForbiddenForOthers() {
        assertThatThrownBy(() ->
                checker.requireTicketAccess(new SessionUser(11, "Other", Role.USER), REPORTER, ASSIGNEE))
                .isInstanceOf(ForbiddenException.class);
        assertThatCode(() ->
                checker.requireTicketAccess(new SessionUser(10, "Owner", Role.USER), REPORTER, ASSIGNEE))
                .doesNotThrowAnyException();
    }
}
