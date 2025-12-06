package vku.chatapp.client.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.event.ActionEvent;
import vku.chatapp.client.controller.component.VerifyOtpController;
import vku.chatapp.common.dto.AuthResponse;
import vku.chatapp.client.service.AuthService;

public class RegisterController extends BaseController {
    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private TextField displayNameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button registerButton;
    @FXML private Hyperlink loginLink;
    @FXML private Label errorLabel;

    private AuthService authService;

    @FXML
    public void initialize() {
        authService = new AuthService();
        errorLabel.setVisible(false);
    }

//    @FXML
//    private void handleRegister() {
//        String username = usernameField.getText().trim();
//        String email = emailField.getText().trim();
//        String displayName = displayNameField.getText().trim();
//        String password = passwordField.getText();
//        String confirmPassword = confirmPasswordField.getText();
//
//        if (username.isEmpty() || email.isEmpty() || displayName.isEmpty() || password.isEmpty()) {
//            showErrorMessage("Please fill in all fields");
//            return;
//        }
//
//        if (!password.equals(confirmPassword)) {
//            showErrorMessage("Passwords do not match");
//            return;
//        }
//
//        if (password.length() < 6) {
//            showErrorMessage("Password must be at least 6 characters");
//            return;
//        }
//
//        registerButton.setDisable(true);
//
//        new Thread(() -> {
//            AuthResponse response = authService.register(username, email, password, displayName);
//
//            javafx.application.Platform.runLater(() -> {
//                registerButton.setDisable(false);
//
//                if (response.isSuccess()) {
//                    // Get stage if not set
//                    if (stage == null) {
//                        stage = (Stage) usernameField.getScene().getWindow();
//                    }
//
//                    showInfo("Registration Successful",
//                            "Your account has been created successfully!\n\n" +
//                                    "You can now login with your credentials.");
//
//                    switchScene("/view/login.fxml", 1200, 800);
//                } else {
//                    showErrorMessage(response.getMessage());
//                }
//            });
//        }).start();
//    }

    @FXML
    private void handleRegister() {
        String username = usernameField.getText().trim();
        String email = emailField.getText().trim();
        String displayName = displayNameField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        // --- Validate Input (Giữ nguyên) ---
        if (username.isEmpty() || email.isEmpty() || displayName.isEmpty() || password.isEmpty()) {
            showErrorMessage("Please fill in all fields");
            return;
        }
        if (!password.equals(confirmPassword)) {
            showErrorMessage("Passwords do not match");
            return;
        }
        if (password.length() < 6) {
            showErrorMessage("Password must be at least 6 characters");
            return;
        }

        registerButton.setDisable(true);

        // --- Gọi Server ---
        new Thread(() -> {
            // Giả sử authService.register đã gọi RMI như các bước trước
            AuthResponse response = authService.register(username, email, password, displayName);

            javafx.application.Platform.runLater(() -> {
                registerButton.setDisable(false);

                if (response.isSuccess()) {
                    // === THAY ĐỔI Ở ĐÂY ===
                    try {
                        // 1. Load giao diện OTP
                        javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/view/verify-otp.fxml"));
                        javafx.scene.Parent root = loader.load();

                        // 2. Lấy Controller của màn hình OTP để truyền Email sang
                        // Lưu ý: Bạn cần import VerifyOtpController
                        VerifyOtpController otpController = loader.getController();
                        otpController.setTargetEmail(email); // Truyền email để người dùng biết code gửi về đâu

                        // 3. Chuyển cảnh
                        if (stage == null) {
                            stage = (Stage) registerButton.getScene().getWindow();
                        }

                        // Tạo scene mới và set vào stage
                        javafx.scene.Scene scene = new javafx.scene.Scene(root);
                        stage.setScene(scene);
                        stage.setTitle("Verify OTP");
                        stage.centerOnScreen();

                    } catch (java.io.IOException e) {
                        e.printStackTrace();
                        showErrorMessage("Error loading OTP screen: " + e.getMessage());
                    }
                } else {
                    showErrorMessage(response.getMessage());
                }
            });
        }).start();
    }

    @FXML
    private void handleBackToLogin(ActionEvent event) {
        // Get stage if not set
        if (stage == null) {
            stage = getStageFromEvent(event);
        }
        switchScene("/view/login.fxml", 1200, 800);
    }

    private void showErrorMessage(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}