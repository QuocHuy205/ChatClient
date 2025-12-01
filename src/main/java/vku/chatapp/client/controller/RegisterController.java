package vku.chatapp.client.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.event.ActionEvent;
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

    @FXML
    private void handleRegister() {
        String username = usernameField.getText().trim();
        String email = emailField.getText().trim();
        String displayName = displayNameField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

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

        new Thread(() -> {
            AuthResponse response = authService.register(username, email, password, displayName);

            javafx.application.Platform.runLater(() -> {
                registerButton.setDisable(false);

                if (response.isSuccess()) {
                    // Get stage if not set
                    if (stage == null) {
                        stage = (Stage) usernameField.getScene().getWindow();
                    }

                    showInfo("Registration Successful",
                            "Your account has been created successfully!\n\n" +
                                    "You can now login with your credentials.");

                    switchScene("/view/login.fxml", 1200, 800);
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