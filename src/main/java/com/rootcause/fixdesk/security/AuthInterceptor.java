package com.rootcause.fixdesk.security;

import com.rootcause.fixdesk.user.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * First link of the authorization chain: is there a valid session?
 * No session: redirect to the login page. Account deactivated since login:
 * the session is ended on its next request.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final UserService userService;

    public AuthInterceptor(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false);
        Object attr = session == null ? null : session.getAttribute(SessionUser.ATTR);
        if (!(attr instanceof SessionUser user)) {
            response.sendRedirect(request.getContextPath() + "/login");
            return false;
        }
        if (!userService.isActive(user.id())) {
            session.invalidate();
            response.sendRedirect(request.getContextPath() + "/login");
            return false;
        }
        return true;
    }
}
