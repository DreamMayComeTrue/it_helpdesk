package com.rootcause.fixdesk.security;

import java.util.List;
import java.util.Set;

import com.rootcause.fixdesk.common.AuditService;
import com.rootcause.fixdesk.common.Role;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Second link of the authorization chain: may this role open this URL?
 * The first matching rule wins. A URL with no rule is open to every logged-in
 * role; the service layer then checks ownership (owner, assignee or admin).
 * This is a coarse gate only: finer rules (for example "technician sees only
 * their own performance") belong in the service.
 */
@Component
public class RoleGuard implements HandlerInterceptor {

    private static final Set<Role> ADMIN_ONLY = Set.of(Role.ADMIN);
    private static final Set<Role> STAFF = Set.of(Role.TECHNICIAN, Role.ADMIN);

    private record Rule(PathPattern pattern, Set<Role> allowed) {
    }

    /** Ordered: put the more specific pattern first. Module owners add their rules here. */
    private static final List<Rule> RULES = List.of(
            // Module 1: user management
            rule("/admin/**", ADMIN_ONLY),
            rule("/duty/**", STAFF),
            // Module 3: AI triage and assignment
            rule("/triage/*/override", ADMIN_ONLY),
            rule("/triage/*/assign", ADMIN_ONLY),
            rule("/triage/**", STAFF),
            rule("/ai-actions/**", STAFF),
            // Module 4: status and resolution
            rule("/incidents/**", STAFF),
            // Module 5: alerts and voice call
            rule("/alerts/**", STAFF),
            rule("/call/**", STAFF),
            // Module 6: knowledge base management
            rule("/kb/manage/**", STAFF),
            // Module 7: reports
            rule("/reports/**", STAFF),
            // Module 8: audit
            rule("/audit/**", ADMIN_ONLY));

    private final AuditService auditService;

    public RoleGuard(AuditService auditService) {
        this.auditService = auditService;
    }

    private static Rule rule(String pattern, Set<Role> allowed) {
        return new Rule(PathPatternParser.defaultInstance.parse(pattern), allowed);
    }

    /** True when {@code role} may open {@code path} (a path inside the application, e.g. "/audit/search"). */
    public static boolean isAllowed(Role role, PathContainer path) {
        for (Rule rule : RULES) {
            if (rule.pattern().matches(path)) {
                return rule.allowed().contains(role);
            }
        }
        return true;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false);
        Object attr = session == null ? null : session.getAttribute(SessionUser.ATTR);
        if (!(attr instanceof SessionUser user)) {
            // AuthInterceptor runs first, so this should not happen; deny rather than guess.
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        PathContainer path = ServletRequestPathUtils.parseAndCache(request).pathWithinApplication();
        if (isAllowed(user.role(), path)) {
            return true;
        }
        auditService.log(user.id(), "ACCESS_DENIED", null, null,
                request.getMethod() + " " + path.value(), request.getRemoteAddr());
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
        return false;
    }
}
