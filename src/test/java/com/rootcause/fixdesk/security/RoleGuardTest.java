package com.rootcause.fixdesk.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.rootcause.fixdesk.common.AuditService;
import com.rootcause.fixdesk.common.Role;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.server.PathContainer;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RoleGuardTest {

    private final AuditService audit = mock(AuditService.class);
    private final RoleGuard guard = new RoleGuard(audit);

    @ParameterizedTest
    @CsvSource({
            // path,                 USER,  TECHNICIAN, ADMIN
            "/admin/users,           false, false, true",
            "/admin/locations/3,     false, false, true",
            "/audit,                 false, false, true",
            "/audit/search,          false, false, true",
            "/triage/queue,          false, true,  true",
            "/triage/7/override,     false, false, true",
            "/triage/7/assign,       false, false, true",
            "/ai-actions,            false, true,  true",
            "/incidents/2,           false, true,  true",
            "/duty,                  false, true,  true",
            "/alerts/4/ack,          false, true,  true",
            "/call/9,                false, true,  true",
            "/kb/manage,             false, true,  true",
            "/reports,               false, true,  true",
            "/reports/export,        false, true,  true",
            "/kb,                    true,  true,  true",
            "/kb/12,                 true,  true,  true",
            "/tickets/mine,          true,  true,  true",
            "/tickets/5,             true,  true,  true",
            "/profile,               true,  true,  true",
            "/notifications,         true,  true,  true",
    })
    void rulesFollowTheAccessTable(String path, boolean user, boolean technician, boolean admin) {
        PathContainer container = PathContainer.parsePath(path);

        assertThat(RoleGuard.isAllowed(Role.USER, container)).as("USER " + path).isEqualTo(user);
        assertThat(RoleGuard.isAllowed(Role.TECHNICIAN, container)).as("TECHNICIAN " + path).isEqualTo(technician);
        assertThat(RoleGuard.isAllowed(Role.ADMIN, container)).as("ADMIN " + path).isEqualTo(admin);
    }

    @Test
    void wrongRoleGets403AndIsAudited() throws Exception {
        MockHttpServletRequest request = requestAs(new SessionUser(42, "Aina", Role.USER), "/audit");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(guard.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        verify(audit).log(eq(42L), eq("ACCESS_DENIED"), any(), any(), eq("GET /audit"), eq("127.0.0.1"));
    }

    @Test
    void allowedRolePassesWithoutAudit() throws Exception {
        MockHttpServletRequest request = requestAs(new SessionUser(1, "Admin", Role.ADMIN), "/audit");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(guard.preHandle(request, response, new Object())).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(audit);
    }

    @Test
    void pathParametersCannotDodgeARule() throws Exception {
        MockHttpServletRequest request = requestAs(new SessionUser(42, "Aina", Role.USER), "/admin;x=1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(guard.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void missingSessionUserIsDenied() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/kb");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(guard.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    private static MockHttpServletRequest requestAs(SessionUser user, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.getSession(true).setAttribute(SessionUser.ATTR, user);
        return request;
    }
}
