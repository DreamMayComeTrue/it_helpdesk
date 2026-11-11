package com.rootcause.fixdesk.config;

import com.rootcause.fixdesk.security.AuthInterceptor;
import com.rootcause.fixdesk.security.CsrfInterceptor;
import com.rootcause.fixdesk.security.RoleGuard;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the authorization chain in order:
 * AuthInterceptor (valid session?) -> RoleGuard (role allowed for this URL?)
 * -> CsrfInterceptor (state-changing request carries the token?).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Served without any check. */
    private static final String[] STATIC = {"/css/**", "/js/**", "/vendor/**", "/sw.js", "/favicon.ico"};

    /** No session needed. /api/agent/** is checked by its own secret (Module 3). */
    private static final String[] NO_SESSION = {"/login", "/register", "/error", "/api/agent/**"};

    private final AuthInterceptor authInterceptor;
    private final RoleGuard roleGuard;
    private final CsrfInterceptor csrfInterceptor;

    public WebConfig(AuthInterceptor authInterceptor, RoleGuard roleGuard, CsrfInterceptor csrfInterceptor) {
        this.authInterceptor = authInterceptor;
        this.roleGuard = roleGuard;
        this.csrfInterceptor = csrfInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).order(1)
                .excludePathPatterns(STATIC).excludePathPatterns(NO_SESSION);
        registry.addInterceptor(roleGuard).order(2)
                .excludePathPatterns(STATIC).excludePathPatterns(NO_SESSION);
        // Also runs on /login and /register: their forms are POSTs and need a token.
        registry.addInterceptor(csrfInterceptor).order(3)
                .excludePathPatterns(STATIC).excludePathPatterns("/api/agent/**");
    }
}
