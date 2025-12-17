// FILE: vku/chatapp/client/controller/LoginController.java
// ✅ OPTIMIZED: Async status update, faster login

package vku.chatapp.client.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.event.ActionEvent;
import vku.chatapp.client.controller.component.ResetPasswordController;
import vku.chatapp.common.dto.AuthResponse;
import vku.chatapp.common.enums.UserStatus;
import vku.chatapp.client.model.UserSession;
import vku.chatapp.client.rmi.ServiceLocator;
import vku.chatapp.client.service.AuthService;
import vku.chatapp.client.rmi.RMIClient;

public class LoginController extends BaseController {
    @FXML private TextField emailField;
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
        String email = emailField.getText().trim();
        String password = passwordField.getText();
        boolean rememberMe = rememberMeCheckbox.isSelected();

        if (email.isEmpty() || password.isEmpty()) {
            showErrorMessage("Please enter both email and password");
            return;
        }

        loginButton.setDisable(true);

        new Thread(() -> {
            AuthResponse response = authService.login(email, password, rememberMe);

            javafx.application.Platform.runLater(() -> {
                loginButton.setDisable(false);

                if (response.isSuccess()) {
                    UserSession.getInstance().setCurrentUser(response.getUser());
                    UserSession.getInstance().setSessionToken(response.getSessionToken());

                    // ✅ OPTIMIZED: Update status ASYNC (non-blocking)
                    new Thread(() -> {
                        try {
                            RMIClient.getInstance()
                                    .getUserService()
                                    .updateStatus(response.getUser().getId(), UserStatus.ONLINE);
                            System.out.println("✅ User status updated to ONLINE");
                        } catch (Exception e) {
                            System.err.println("⚠️ Failed to update status: " + e.getMessage());
                        }
                    }).start();

                    // Switch scene immediately without waiting
                    if (stage == null) {
                        stage = (Stage) emailField.getScene().getWindow();
                    }
                    switchScene("/view/main.fxml", 1200, 800);
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
        dialog.setTitle("Quên Mật Khẩu");
        dialog.setHeaderText("Đặt lại mật khẩu");
        dialog.setContentText("Nhập địa chỉ email của bạn:");

        dialog.showAndWait().ifPresent(email -> {
            if (email.trim().isEmpty()) {
                showError("Lỗi", "Vui lòng nhập địa chỉ email");
                return;
            }

            if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                showError("Lỗi", "Email không hợp lệ");
                return;
            }

            Alert loadingAlert = new Alert(Alert.AlertType.INFORMATION);
            loadingAlert.setTitle("Đang xử lý");
            loadingAlert.setHeaderText("Đang gửi mã OTP...");
            loadingAlert.setContentText("Vui lòng đợi...");
            loadingAlert.show();

            new Thread(() -> {
                boolean success = authService.sendPasswordResetOtp(email);

                javafx.application.Platform.runLater(() -> {
                    loadingAlert.close();

                    if (success) {
                        try {
                            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/reset-password.fxml"));
                            Parent root = loader.load();

                            ResetPasswordController controller = loader.getController();
                            controller.setEmail(email);
                            controller.setStage(stage);

                            Scene scene = new Scene(root, 1200, 800);
                            stage.setScene(scene);
                            stage.setTitle("Đặt Lại Mật Khẩu - Chat App");
                            stage.show();
                            stage.centerOnScreen();

                        } catch (Exception e) {
                            e.printStackTrace();
                            showError("Lỗi", "Không thể mở màn hình đặt lại mật khẩu: " + e.getMessage());
                        }
                    } else {
                        showError("Lỗi", "Không thể gửi mã OTP. Vui lòng kiểm tra email và thử lại.");
                    }
                });
            }).start();
        });
    }

    private void showErrorMessage(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}