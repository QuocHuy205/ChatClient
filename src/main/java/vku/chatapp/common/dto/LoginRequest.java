package vku.chatapp.common.dto;

import java.io.Serializable;

public class LoginRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String email;
    private String password;
    private boolean rememberMe;


    public LoginRequest() {}

    public LoginRequest(String email, String password, boolean rememberMe) {
        this.email = email;
        this.password = password;
        this.rememberMe = rememberMe;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isRememberMe() { return rememberMe; }
    public void setRememberMe(boolean rememberMe) { this.rememberMe = rememberMe; }
}
