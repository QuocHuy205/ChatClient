package vku.chatapp.client.controller.component;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import vku.chatapp.client.controller.BaseController;
import vku.chatapp.client.service.AuthService;

public class ResetPasswordController extends BaseController {

    @FXML private Label emailLabel;
    @FXML private TextField otpField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button resendButton;
    @FXML private Button resetButton;
    @FXML private Label timerLabel;
    @FXML private Label messageLabel;

    private AuthService authService;
    private String email;
    private Timeline resendTimer;
    private int remainingSeconds = 60;

    @FXML
    public void initialize() {
        authService = new AuthService();

        // Giới hạn OTP chỉ nhập số, tối đa 6 ký tự
        otpField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*")) {
                otpField.setText(oldVal);
            }
            if (newVal.length() > 6) {
                otpField.setText(newVal.substring(0, 6));
            }
        });

        // Enter để submit
        newPasswordField.setOnAction(e -> confirmPasswordField.requestFocus());
        confirmPasswordField.setOnAction(e -> handleResetPassword());
    }

    /**
     * Set email từ màn hình trước (LoginController)
     */
    public void setEmail(String email) {
        this.email = email;
        emailLabel.setText("Email: " + email);
        startResendTimer();
    }

    /**
     * Xử lý đặt lại mật khẩu
     */
    @FXML
    private void handleResetPassword() {
        String otp = otpField.getText().trim();
        String newPassword = newPasswordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        // Validation
        if (otp.isEmpty()) {
            showMessage("Vui lòng nhập mã OTP", "error");
            otpField.requestFocus();
            return;
        }

        if (otp.length() != 6) {
            showMessage("Mã OTP phải có 6 chữ số", "error");
            otpField.requestFocus();
            return;
        }

        if (newPassword.isEmpty()) {
            showMessage("Vui lòng nhập mật khẩu mới", "error");
            newPasswordField.requestFocus();
            return;
        }

        if (newPassword.length() < 6) {
            showMessage("Mật khẩu phải có ít nhất 6 ký tự", "error");
            newPasswordField.requestFocus();
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            showMessage("Mật khẩu xác nhận không khớp", "error");
            confirmPasswordField.requestFocus();
            return;
        }

        // Disable button để tránh spam
        resetButton.setDisable(true);
        showMessage("Đang xử lý...", "info");

        new Thread(() -> {
            boolean success = authService.resetPassword(email, otp, newPassword);

            Platform.runLater(() -> {
                resetButton.setDisable(false);

                if (success) {
                    showMessage("✅ Đặt lại mật khẩu thành công!", "success");

                    // Delay 2 giây rồi quay về login
                    Timeline delay = new Timeline(new KeyFrame(Duration.seconds(2), e -> {
                        handleBackToLogin();
                    }));
                    delay.play();
                } else {
                    showMessage("❌ Mã OTP không hợp lệ hoặc đã hết hạn", "error");
                    otpField.clear();
                    otpField.requestFocus();
                }
            });
        }).start();
    }

    /**
     * Gửi lại OTP
     */
    @FXML
    private void handleResendOtp() {
        if (remainingSeconds > 0) {
            showMessage("Vui lòng đợi " + remainingSeconds + " giây để gửi lại", "warning");
            return;
        }

        resendButton.setDisable(true);
        showMessage("Đang gửi lại mã OTP...", "info");

        new Thread(() -> {
            boolean success = authService.sendPasswordResetOtp(email);

            Platform.runLater(() -> {
                resendButton.setDisable(false);

                if (success) {
                    showMessage("✅ Đã gửi lại mã OTP. Vui lòng kiểm tra email!", "success");
                    remainingSeconds = 60;
                    startResendTimer();
                    otpField.clear();
                    otpField.requestFocus();
                } else {
                    showMessage("❌ Không thể gửi lại OTP. Vui lòng thử lại!", "error");
                }
            });
        }).start();
    }

    /**
     * Bộ đếm thời gian cho nút "Gửi lại"
     */
    private void startResendTimer() {
        if (resendTimer != null) {
            resendTimer.stop();
        }

        resendButton.setDisable(true);
        remainingSeconds = 60;

        resendTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            remainingSeconds--;

            if (remainingSeconds > 0) {
                timerLabel.setText("Bạn có thể gửi lại sau: " + remainingSeconds + "s");
                timerLabel.setStyle("-fx-text-fill: #dc3545;");
            } else {
                timerLabel.setText("Bạn có thể gửi lại mã OTP");
                timerLabel.setStyle("-fx-text-fill: #28a745;");
                resendButton.setDisable(false);
                resendTimer.stop();
            }
        }));

        resendTimer.setCycleCount(60);
        resendTimer.play();
    }

    /**
     * Quay về màn hình login
     */
    @FXML
    private void handleBackToLogin() {
        if (resendTimer != null) {
            resendTimer.stop();
        }

        if (stage == null) {
            stage = (Stage) otpField.getScene().getWindow();
        }
        switchScene("/view/login.fxml", 1200, 800);
    }

    /**
     * Hiển thị message với styling
     */
    private void showMessage(String message, String type) {
        messageLabel.setText(message);
        messageLabel.setVisible(true);

        switch (type) {
            case "success" -> messageLabel.setStyle(
                    "-fx-background-color: #d4edda; -fx-text-fill: #155724; " +
                            "-fx-border-color: #c3e6cb; -fx-border-width: 1; -fx-border-radius: 5; " +
                            "-fx-background-radius: 5; -fx-padding: 10; -fx-font-weight: bold;"
            );
            case "error" -> messageLabel.setStyle(
                    "-fx-background-color: #f8d7da; -fx-text-fill: #721c24; " +
                            "-fx-border-color: #f5c6cb; -fx-border-width: 1; -fx-border-radius: 5; " +
                            "-fx-background-radius: 5; -fx-padding: 10; -fx-font-weight: bold;"
            );
            case "warning" -> messageLabel.setStyle(
                    "-fx-background-color: #fff3cd; -fx-text-fill: #856404; " +
                            "-fx-border-color: #ffeaa7; -fx-border-width: 1; -fx-border-radius: 5; " +
                            "-fx-background-radius: 5; -fx-padding: 10; -fx-font-weight: bold;"
            );
            case "info" -> messageLabel.setStyle(
                    "-fx-background-color: #d1ecf1; -fx-text-fill: #0c5460; " +
                            "-fx-border-color: #bee5eb; -fx-border-width: 1; -fx-border-radius: 5; " +
                            "-fx-background-radius: 5; -fx-padding: 10; -fx-font-weight: bold;"
            );
        }
    }
}