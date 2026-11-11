package com.rootcause.fixdesk.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.Optional;

import com.rootcause.fixdesk.common.AuditService;
import com.rootcause.fixdesk.common.ComingSoonController;
import com.rootcause.fixdesk.common.HomeController;
import com.rootcause.fixdesk.common.Role;
import com.rootcause.fixdesk.security.CsrfTokens;
import com.rootcause.fixdesk.security.PasswordService;
import com.rootcause.fixdesk.security.SessionUser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Login, register, logout and the interceptor chain, through MockMvc with mocked services. */
@WebMvcTest(controllers = {AuthController.class, HomeController.class, ComingSoonController.class})
class AuthControllerTest {

    private static final String TOKEN = "test-csrf-token";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserService userService;
    @MockitoBean
    private PasswordService passwordService;
    @MockitoBean
    private AuditService auditService;

    @BeforeEach
    void activeAccountsAndValidPasswords() {
        when(userService.isActive(anyLong())).thenReturn(true);
        when(passwordService.validate(anyString())).thenReturn(Optional.empty());
    }

    private static MockHttpSession session(SessionUser user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CsrfTokens.ATTR, TOKEN);
        if (user != null) {
            session.setAttribute(SessionUser.ATTR, user);
        }
        return session;
    }

    private static final SessionUser USER = new SessionUser(10, "Aina", Role.USER);
    private static final SessionUser TECH = new SessionUser(20, "Farid", Role.TECHNICIAN);
    private static final SessionUser ADMIN = new SessionUser(1, "Admin", Role.ADMIN);

    // ---- login ----

    @Test
    void loginPageRendersWithACsrfField() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/login").param("email", "aina@utm.my").param("password", "pw"))
                .andExpect(status().isForbidden());

        verify(userService, never()).authenticate(anyString(), anyString(), anyString());
    }

    @Test
    void failedLoginShowsOneGenericMessage() throws Exception {
        when(userService.authenticate("aina@utm.my", "wrong", "127.0.0.1")).thenReturn(Optional.empty());

        mvc.perform(post("/login").session(session(null)).param("_csrf", TOKEN)
                        .param("email", "aina@utm.my").param("password", "wrong"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"))
                .andExpect(model().attribute("loginFailed", true))
                .andExpect(content().string(containsString("Email or password is incorrect")))
                .andExpect(content().string(not(containsString("wrong\""))));
    }

    @Test
    void successfulLoginStartsAFreshSessionAndGoesToTheRoleStartPage() throws Exception {
        when(userService.authenticate("farid@utm.my", "right-pw", "127.0.0.1")).thenReturn(Optional.of(TECH));
        MockHttpSession session = session(null);
        String idBefore = session.getId();

        mvc.perform(post("/login").session(session).param("_csrf", TOKEN)
                        .param("email", "farid@utm.my").param("password", "right-pw"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/triage/queue"));

        assertThat(session.getId()).as("session id changes on login").isNotEqualTo(idBefore);
        assertThat(session.getAttribute(SessionUser.ATTR)).isEqualTo(TECH);
        assertThat(session.getAttribute(CsrfTokens.ATTR)).as("CSRF token is replaced").isNotEqualTo(TOKEN);
    }

    @Test
    void blankLoginFieldsAreValidationErrorsNotLoginAttempts() throws Exception {
        mvc.perform(post("/login").session(session(null)).param("_csrf", TOKEN)
                        .param("email", " ").param("password", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"))
                .andExpect(model().attributeHasFieldErrors("form", "email", "password"));

        verify(userService, never()).authenticate(anyString(), anyString(), anyString());
    }

    @Test
    void loggedInUserOpeningLoginIsSentToTheirStartPage() throws Exception {
        mvc.perform(get("/login").session(session(ADMIN)))
                .andExpect(redirectedUrl("/reports"));
    }

    // ---- authorization chain ----

    @Test
    void protectedPageWithoutSessionRedirectsToLogin() throws Exception {
        mvc.perform(get("/tickets/mine")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void homeSendsEachRoleToItsStartPage() throws Exception {
        mvc.perform(get("/").session(session(USER))).andExpect(redirectedUrl("/tickets/mine"));
        mvc.perform(get("/").session(session(TECH))).andExpect(redirectedUrl("/triage/queue"));
        mvc.perform(get("/").session(session(ADMIN))).andExpect(redirectedUrl("/reports"));
    }

    @Test
    void wrongRoleGets403() throws Exception {
        mvc.perform(get("/audit").session(session(USER))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/users").session(session(TECH))).andExpect(status().isForbidden());
        mvc.perform(get("/triage/queue").session(session(USER))).andExpect(status().isForbidden());

        verify(auditService).log(eq(10L), eq("ACCESS_DENIED"), any(), any(), eq("GET /audit"), anyString());
    }

    @Test
    void deactivatedAccountLosesItsSessionOnTheNextRequest() throws Exception {
        when(userService.isActive(10L)).thenReturn(false);
        MockHttpSession session = session(USER);

        mvc.perform(get("/tickets/mine").session(session)).andExpect(redirectedUrl("/login"));

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void menuShowsOnlyTheLinksOfTheRole() throws Exception {
        mvc.perform(get("/tickets/mine").session(session(USER)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("New Ticket")))
                .andExpect(content().string(not(containsString("Audit Log"))))
                .andExpect(content().string(not(containsString("Ticket Queue"))));

        mvc.perform(get("/triage/queue").session(session(TECH)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Duty Status")))
                .andExpect(content().string(not(containsString("Audit Log"))));

        mvc.perform(get("/reports").session(session(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Audit Log")))
                .andExpect(content().string(containsString("Users")));
    }

    @Test
    void userTextInTheLayoutIsEscaped() throws Exception {
        SessionUser script = new SessionUser(11, "<script>alert(1)</script>", Role.USER);

        mvc.perform(get("/tickets/mine").session(session(script)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")));
    }

    // ---- register ----

    @Test
    void registerCreatesTheAccountAndReturnsToLogin() throws Exception {
        mvc.perform(post("/register").session(session(null)).param("_csrf", TOKEN)
                        .param("name", "Aina Ali").param("email", "aina@utm.my")
                        .param("password", "long-enough-pw").param("confirmPassword", "long-enough-pw"))
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("success"));

        verify(userService).register("Aina Ali", "aina@utm.my", "long-enough-pw", "127.0.0.1");
    }

    @Test
    void registerShowsValidationErrors() throws Exception {
        when(passwordService.validate("short")).thenReturn(Optional.of("Password must be at least 8 characters."));

        mvc.perform(post("/register").session(session(null)).param("_csrf", TOKEN)
                        .param("name", "").param("email", "not-an-email")
                        .param("password", "short").param("confirmPassword", "different"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().attributeHasFieldErrors("form", "name", "email", "password", "confirmPassword"))
                .andExpect(content().string(containsString("Password must be at least 8 characters.")));

        verify(userService, never()).register(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void registerReportsAnEmailThatIsAlreadyUsed() throws Exception {
        when(userService.register(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new EmailAlreadyUsedException());

        mvc.perform(post("/register").session(session(null)).param("_csrf", TOKEN)
                        .param("name", "Aina Ali").param("email", "aina@utm.my")
                        .param("password", "long-enough-pw").param("confirmPassword", "long-enough-pw"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().attributeHasFieldErrors("form", "email"));
    }

    @Test
    void registerWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/register").param("name", "Aina").param("email", "aina@utm.my")
                        .param("password", "long-enough-pw").param("confirmPassword", "long-enough-pw"))
                .andExpect(status().isForbidden());

        verify(userService, never()).register(anyString(), anyString(), anyString(), anyString());
    }

    // ---- logout ----

    @Test
    void logoutEndsTheSessionAndIsAudited() throws Exception {
        MockHttpSession session = session(USER);

        mvc.perform(post("/logout").session(session).param("_csrf", TOKEN))
                .andExpect(redirectedUrl("/login"));

        assertThat(session.isInvalid()).isTrue();
        verify(userService).recordLogout(USER, "127.0.0.1");
    }

    @Test
    void logoutWithoutCsrfTokenIsRejected() throws Exception {
        MockHttpSession session = session(USER);

        mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());

        assertThat(session.isInvalid()).isFalse();
    }
}
