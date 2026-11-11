package com.rootcause.fixdesk.security;

import java.util.Set;

import com.rootcause.fixdesk.common.AuditService;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Third link of the authorization chain. Every state-changing request (anything
 * except GET, HEAD, OPTIONS, TRACE) must carry the session's CSRF token, as the
 * hidden {@code _csrf} form field or the {@code X-CSRF-Token} header.
 * Also makes sure every session has a token, so forms can always render one.
 */
@Component
public class CsrfInterceptor implements HandlerInterceptor {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final AuditService auditService;

    public CsrfInterceptor(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (request.getDispatcherType() == DispatcherType.ERROR) {
            // The error page of a request that was already checked (or rejected).
            return true;
        }
        HttpSession session = request.getSession(true);
        CsrfTokens.ensure(session);
        if (SAFE_METHODS.contains(request.getMethod())) {
            return true;
        }
        String supplied = request.getParameter(CsrfTokens.PARAM);
        if (supplied == null) {
            supplied = request.getHeader(CsrfTokens.HEADER);
        }
        if (CsrfTokens.matches(session, supplied)) {
            return true;
        }
        Long actorId = session.getAttribute(SessionUser.ATTR) instanceof SessionUser user ? user.id() : null;
        auditService.log(actorId, "CSRF_REJECTED", null, null,
                request.getMethod() + " " + request.getRequestURI(), request.getRemoteAddr());
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
        return false;
    }
}
