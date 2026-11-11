package com.rootcause.fixdesk.user;

import java.util.Optional;

import com.rootcause.fixdesk.security.CsrfTokens;
import com.rootcause.fixdesk.security.PasswordService;
import com.rootcause.fixdesk.security.SessionUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Login, self-registration and logout (Module 1). */
@Controller
public class AuthController {

    private final UserService userService;
    private final PasswordService passwordService;

    public AuthController(UserService userService, PasswordService passwordService) {
        this.userService = userService;
        this.passwordService = passwordService;
    }

    @GetMapping("/login")
    public String loginPage(HttpSession session, Model model) {
        if (session.getAttribute(SessionUser.ATTR) instanceof SessionUser user) {
            return "redirect:" + user.role().startPage();
        }
        model.addAttribute("form", new LoginForm());
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute("form") LoginForm form, BindingResult errors,
                        HttpServletRequest request, Model model) {
        if (errors.hasErrors()) {
            return "auth/login";
        }
        Optional<SessionUser> user =
                userService.authenticate(form.getEmail(), form.getPassword(), request.getRemoteAddr());
        if (user.isEmpty()) {
            // One message for every failure: never say whether the email or the password was wrong.
            model.addAttribute("loginFailed", true);
            return "auth/login";
        }
        // New session id on login prevents session fixation; the old CSRF token is replaced too.
        request.changeSessionId();
        HttpSession session = request.getSession();
        session.setAttribute(SessionUser.ATTR, user.get());
        CsrfTokens.rotate(session);
        return "redirect:" + user.get().role().startPage();
    }

    @GetMapping("/register")
    public String registerPage(HttpSession session, Model model) {
        if (session.getAttribute(SessionUser.ATTR) instanceof SessionUser user) {
            return "redirect:" + user.role().startPage();
        }
        model.addAttribute("form", new RegisterForm());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterForm form, BindingResult errors,
                           HttpServletRequest request, RedirectAttributes redirect) {
        if (!errors.hasFieldErrors("password")) {
            passwordService.validate(form.getPassword())
                    .ifPresent(message -> errors.rejectValue("password", "password.length", message));
        }
        if (!errors.hasFieldErrors("confirmPassword") && !form.getConfirmPassword().equals(form.getPassword())) {
            errors.rejectValue("confirmPassword", "password.mismatch", "The two passwords do not match.");
        }
        if (errors.hasErrors()) {
            return "auth/register";
        }
        try {
            userService.register(form.getName(), form.getEmail(), form.getPassword(), request.getRemoteAddr());
        } catch (EmailAlreadyUsedException e) {
            errors.rejectValue("email", "email.taken", "This email already has an account.");
            return "auth/register";
        }
        redirect.addFlashAttribute("success", "Account created. You can log in now.");
        return "redirect:/login";
    }

    @PostMapping("/logout")
    public String logout(HttpServletRequest request, RedirectAttributes redirect) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            if (session.getAttribute(SessionUser.ATTR) instanceof SessionUser user) {
                userService.recordLogout(user, request.getRemoteAddr());
            }
            session.invalidate();
        }
        redirect.addFlashAttribute("success", "You have been logged out.");
        return "redirect:/login";
    }
}
