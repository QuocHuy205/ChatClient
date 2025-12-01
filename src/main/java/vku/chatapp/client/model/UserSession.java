package vku.chatapp.client.model;

import vku.chatapp.common.model.User;

public class UserSession {
    private static UserSession instance;
    private User currentUser;
    private String sessionToken;

    private UserSession() {}

    public static UserSession getInstance() {
        if (instance == null) {
            synchronized (UserSession.class) {
                if (instance == null) {
                    instance = new UserSession();
                }
            }
        }
        return instance;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User currentUser) {
        this.currentUser = currentUser;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public void clear() {
        currentUser = null;
        sessionToken = null;
    }

    public boolean isLoggedIn() {
        return currentUser != null && sessionToken != null;
    }
}