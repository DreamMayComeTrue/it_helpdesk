package com.rootcause.fixdesk.security;

import com.rootcause.fixdesk.common.ForbiddenException;

import org.springframework.stereotype.Component;

/**
 * The ownership rule for tickets, used by every service method that reads or
 * changes a ticket: the reporter, the assigned technician, or an admin.
 */
@Component
public class OwnershipChecker {

    public boolean canAccessTicket(SessionUser user, long reporterId, Long assigneeId) {
        if (user == null) {
            return false;
        }
        return user.isAdmin()
                || user.id() == reporterId
                || (assigneeId != null && user.id() == assigneeId);
    }

    /** Throws {@link ForbiddenException} (generic 403 page) unless access is allowed. */
    public void requireTicketAccess(SessionUser user, long reporterId, Long assigneeId) {
        if (!canAccessTicket(user, reporterId, assigneeId)) {
            throw new ForbiddenException("No access to this ticket");
        }
    }
}
