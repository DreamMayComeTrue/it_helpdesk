package com.rootcause.fixdesk.user;

/** Registration refused because the email already has an account. */
public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException() {
        super("Email already registered");
    }
}
