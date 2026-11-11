package com.rootcause.fixdesk.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import jakarta.servlet.http.HttpSession;

/** Creates and checks the per-session CSRF token. */
public final class CsrfTokens {

    /** Session attribute name. Templates read it as {@code session.csrfToken}. */
    public static final String ATTR = "csrfToken";
    /** Hidden form field name. */
    public static final String PARAM = "_csrf";
    /** Header for fetch/XHR calls. */
    public static final String HEADER = "X-CSRF-Token";

    private static final SecureRandom RANDOM = new SecureRandom();

    private CsrfTokens() {
    }

    /** Returns the session's token, creating one if the session has none yet. */
    public static String ensure(HttpSession session) {
        Object existing = session.getAttribute(ATTR);
        if (existing instanceof String token && !token.isEmpty()) {
            return token;
        }
        return rotate(session);
    }

    /** Replaces the token; called after login so the pre-login token stops working. */
    public static String rotate(HttpSession session) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute(ATTR, token);
        return token;
    }

    /** Constant-time comparison of the supplied value with the session's token. */
    public static boolean matches(HttpSession session, String supplied) {
        Object expected = session.getAttribute(ATTR);
        if (!(expected instanceof String token) || supplied == null) {
            return false;
        }
        return MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }
}
