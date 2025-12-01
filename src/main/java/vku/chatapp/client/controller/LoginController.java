// FILE: vku/chatapp/client/controller/LoginController.java
// ✅ FIX: Thêm cập nhật status ONLINE sau khi login

package vku.chatapp.client.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.event.ActionEvent;
import vku.chatapp.common.dto.AuthResponse;
import vku.chatapp.common.enums.UserStatus;
import vku.chatapp.client.model.UserSession;
import vku.chatapp.client.rmi.ServiceLocator;
import vku.chatapp.client.service.AuthService;
import vku.chatapp.client.rmi.RMIClient;

public class LoginController extends BaseController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private CheckBox rememberMeCheckbox;
    @FXML private Button loginButton;
    @FXML private Hyperlink registerLink;
    @FXML private Hyperlink forgotPasswordLink;
    @FXML private Label errorLabel;

    private AuthService authService;

    @FXML
    public void initialize() {
        try {
            ServiceLocator.getInstance().initialize();
            authService = new AuthService();
            errorLabel.setVisible(false);
        } catch (Exception e) {
            showError("Connection Error", "Unable to connect to server: " + e.getMessage());
        }
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        boolean rememberMe = rememberMeCheckbox.isSelected();

        if (username.isEmpty() || password.isEmpty()) {
            showErrorMessage("Please enter both username and password");
            return;
        }

        loginButton.setDisable(true);

        new Thread(() -> {
            AuthResponse response = authService.login(username, password, rememberMe);

            javafx.application.Platform.runLater(() -> {
                loginButton.setDisable(false);

                if (response.isSuccess()) {
                    UserSession.getInstance().setCurrentUser(response.getUser());
                    UserSession.getInstance().setSessionToken(response.getSessionToken());

                    // ✅ FIX 1: CẬP NHẬT STATUS THÀNH ONLINE
                    try {
                        boolean statusUpdated = RMIClient.getInstance()
                                .getUserService()
                                .updateStatus(response.getUser().getId(), UserStatus.ONLINE);

                        if (statusUpdated) {
                            System.out.println("✅ User status updated to ONLINE");
                        } else {
                            System.err.println("⚠️ Failed to update user status");
                        }
                    } catch (Exception e) {
                        System.err.println("❌ Error updating status: " + e.getMessage());
                    }

                    // Get stage from current scene
                    if (stage == null) {
                        stage = (Stage) usernameField.getScene().getWindow();
                    }
                    switchScene("/view/main.fxml", 1400, 900);
                } else {
                    showErrorMessage(response.getMessage());
                }
            });
        }).start();
    }

    @FXML
    private void handleRegister(ActionEvent event) {
        if (stage == null) {
            stage = getStageFromEvent(event);
        }
        switchScene("/view/register.fxml", 1200, 800);
    }

    @FXML
    private void handleForgotPassword(ActionEvent event) {
        if (stage == null) {
            stage = getStageFromEvent(event);
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Forgot Password");
        dialog.setHeaderText("Reset Password");
        dialog.setContentText("Enter your email address:");

        dialog.showAndWait().ifPresent(email -> {
            if (email.isEmpty()) {
                showError("Error", "Please enter your email address");
                return;
            }

            new Thread(() -> {
                boolean success = authService.sendPasswordResetOtp(email);

                javafx.application.Platform.runLater(() -> {
                    if (success) {
                        showResetPasswordDialog(email);
                    } else {
                        showError("Error", "Failed to send reset code. Please check your email address.");
                    }
                });
            }).start();
        });
    }

    private void showResetPasswordDialog(String email) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Reset Password");
        dialog.setHeaderText("Enter reset code and new password");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField otpField = new TextField();
        otpField.setPromptText("6-digit code");
        PasswordField newPasswordField = new PasswordField();
        newPasswordField.setPromptText("New password");
        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Confirm password");

        grid.add(new Label("Email:"), 0, 0);
        grid.add(new Label(email), 1, 0);
        grid.add(new Label("Reset Code:"), 0, 1);
        grid.add(otpField, 1, 1);
        grid.add(new Label("New Password:"), 0, 2);
        grid.add(newPasswordField, 1, 2);
        grid.add(new Label("Confirm:"), 0, 3);
        grid.add(confirmPasswordField, 1, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                String otp = otpField.getText().trim();
                String newPassword = newPasswordField.getText();
                String confirmPassword = confirmPasswordField.getText();

                if (otp.isEmpty() || newPassword.isEmpty()) {
                    showError("Error", "Please fill in all fields");
                    return;
                }

                if (!newPassword.equals(confirmPassword)) {
                    showError("Error", "Passwords do not match");
                    return;
                }

                if (newPassword.length() < 6) {
                    showError("Error", "Password must be at least 6 characters");
                    return;
                }

                new Thread(() -> {
                    boolean success = authService.resetPassword(email, otp, newPassword);

                    javafx.application.Platform.runLater(() -> {
                        if (success) {
                            showInfo("Success", "Password reset successfully! You can now login.");
                        } else {
                            showError("Error", "Failed to reset password. Invalid code or expired.");
                        }
                    });
                }).start();
            }
        });
    }

    private void showErrorMessage(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}