package com.rootcause.fixdesk.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.rootcause.fixdesk.common.AuditService;
import com.rootcause.fixdesk.common.Role;

import jakarta.servlet.DispatcherType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CsrfInterceptorTest {

    private final AuditService audit = mock(AuditService.class);
    private final CsrfInterceptor interceptor = new CsrfInterceptor(audit);
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void getCreatesATokenAndPasses() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(request.getSession(false).getAttribute(CsrfTokens.ATTR))
                .isInstanceOf(String.class).asString().hasSizeGreaterThanOrEqualTo(40);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    void stateChangingRequestWithoutTokenIsRejectedAndAudited(String method) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/tickets/5/cancel");

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        verify(audit).log(isNull(), eq("CSRF_REJECTED"), any(), any(),
                eq(method + " /tickets/5/cancel"), eq("127.0.0.1"));
    }

    @Test
    void wrongTokenIsRejected() throws Exception {
        MockHttpServletRequest request = postWithSessionToken("real-token");
        request.setParameter(CsrfTokens.PARAM, "forged-token");

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void tokenFromAnotherSessionIsRejected() throws Exception {
        MockHttpServletRequest other = new MockHttpServletRequest("GET", "/login");
        interceptor.preHandle(other, new MockHttpServletResponse(), new Object());
        String othersToken = (String) other.getSession(false).getAttribute(CsrfTokens.ATTR);

        MockHttpServletRequest request = postWithSessionToken("my-token");
        request.setParameter(CsrfTokens.PARAM, othersToken);

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
    }

    @Test
    void correctFormFieldPasses() throws Exception {
        MockHttpServletRequest request = postWithSessionToken("real-token");
        request.setParameter(CsrfTokens.PARAM, "real-token");

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        verifyNoInteractions(audit);
    }

    @Test
    void correctHeaderPasses() throws Exception {
        MockHttpServletRequest request = postWithSessionToken("real-token");
        request.addHeader(CsrfTokens.HEADER, "real-token");

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void rejectionRecordsTheLoggedInUser() throws Exception {
        MockHttpServletRequest request = postWithSessionToken("real-token");
        request.getSession(false).setAttribute(SessionUser.ATTR, new SessionUser(7, "Tech", Role.TECHNICIAN));

        interceptor.preHandle(request, response, new Object());

        verify(audit).log(eq(7L), eq("CSRF_REJECTED"), any(), any(), any(), any());
    }

    @Test
    void errorPageDispatchIsNotCheckedAgain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/error");
        request.setDispatcherType(DispatcherType.ERROR);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    private static MockHttpServletRequest postWithSessionToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/tickets/5/cancel");
        request.getSession(true).setAttribute(CsrfTokens.ATTR, token);
        return request;
    }
}
