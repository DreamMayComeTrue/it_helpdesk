package com.rootcause.fixdesk.audit;

/** audit_log is append-only: insert and read, never update or delete. */
public interface AuditDao {

    void insert(AuditEntry entry);
}
