package com.rootcause.fixdesk.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAuditDao implements AuditDao {

    private final JdbcTemplate jdbc;

    public JdbcAuditDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(AuditEntry entry) {
        jdbc.update("""
                INSERT INTO audit_log (actor_id, action, entity_type, entity_id, detail, ip_address)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                entry.actorId(), entry.action(), entry.entityType(), entry.entityId(),
                entry.detail(), entry.ipAddress());
    }
}
