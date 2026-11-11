package com.rootcause.fixdesk.audit;

import java.time.LocalDateTime;

/** One row of audit_log. {@code id} and {@code createdAt} are null before insert. */
public record AuditEntry(Long id, Long actorId, String action, String entityType, Long entityId,
                         String detail, String ipAddress, LocalDateTime createdAt) {
}
