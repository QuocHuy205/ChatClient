package vku.chatapp.client.service;

import vku.chatapp.common.dto.*;
import vku.chatapp.client.rmi.RMIClient;

public class AuthService {
    private final RMIClient rmiClient;

    public AuthService() {
        this.rmiClient = RMIClient.getInstance();
    }

    public AuthResponse login(String email, String password, boolean rememberMe) {
        try {
            LoginRequest request = new LoginRequest(email, password, rememberMe);
            return rmiClient.getAuthService().login(request);
        } catch (Exception e) {
            e.printStackTrace();
            return new AuthResponse(false, "Connection error: " + e.getMessage());
        }
    }

    public AuthResponse register(String username, String email, String password, String displayName) {
        try {
            RegisterRequest request = new RegisterRequest(username, email, password, displayName);
            return rmiClient.getAuthService().register(request);
        } catch (Exception e) {
            e.printStackTrace();
            return new AuthResponse(false, "Connection error: " + e.getMessage());
        }
    }

    public boolean logout(String sessionToken) {
        try {
            return rmiClient.getAuthService().logout(sessionToken);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean verifyEmail(String email, String otp) {
        try {
            return rmiClient.getAuthService().verifyEmail(email, otp);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean sendPasswordResetOtp(String email) {
        try {
            return rmiClient.getAuthService().sendPasswordResetOtp(email);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean resetPassword(String email, String otp, String newPassword) {
        try {
            return rmiClient.getAuthService().resetPassword(email, otp, newPassword);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}