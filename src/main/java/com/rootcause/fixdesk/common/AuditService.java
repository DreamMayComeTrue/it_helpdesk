package com.rootcause.fixdesk.common;

/**
 * Every state-changing service method calls this. Security events (failed logins,
 * denied access, rejected CSRF tokens) are logged here too.
 * Never pass passwords, PINs or full tokens in {@code detail}.
 */
public interface AuditService {

    /**
     * @param actorId    the user who acted, or null for system or anonymous events
     * @param action     e.g. LOGIN_FAILED, TICKET_CREATED, AI_ACTION_APPROVED
     * @param entityType e.g. USER, TICKET; may be null
     * @param entityId   id of the affected record; may be null
     * @param detail     short free text; may be null
     * @param ip         caller's IP address; may be null for background jobs
     */
    void log(Long actorId, String action, String entityType, Long entityId, String detail, String ip);
}
