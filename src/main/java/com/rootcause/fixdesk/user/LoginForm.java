package com.rootcause.fixdesk.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginForm {

    @NotBlank(message = "Enter your email.")
    @Size(max = 150, message = "Email is too long.")
    private String email;

    @NotBlank(message = "Enter your password.")
    @Size(max = 200, message = "Password is too long.")
    private String password;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
