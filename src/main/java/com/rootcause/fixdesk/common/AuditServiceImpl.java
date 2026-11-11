package com.rootcause.fixdesk.common;

import com.rootcause.fixdesk.audit.AuditDao;
import com.rootcause.fixdesk.audit.AuditEntry;

import org.springframework.stereotype.Service;

@Service
public class AuditServiceImpl implements AuditService {

    private static final int ACTION_MAX = 60;
    private static final int ENTITY_TYPE_MAX = 40;
    private static final int DETAIL_MAX = 1000;
    private static final int IP_MAX = 45;

    private final AuditDao auditDao;

    public AuditServiceImpl(AuditDao auditDao) {
        this.auditDao = auditDao;
    }

    @Override
    public void log(Long actorId, String action, String entityType, Long entityId, String detail, String ip) {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("audit action is required");
        }
        auditDao.insert(new AuditEntry(null, actorId, cut(action, ACTION_MAX), cut(entityType, ENTITY_TYPE_MAX),
                entityId, cut(detail, DETAIL_MAX), cut(ip, IP_MAX), null));
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
