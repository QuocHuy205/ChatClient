package vku.chatapp.client.controller.component;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import vku.chatapp.client.controller.BaseController;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.Duration;
import vku.chatapp.client.rmi.RMIClient;

public class VerifyOtpController extends BaseController {

    @FXML private TextField otp1, otp2, otp3, otp4, otp5, otp6;
    @FXML private Label timerLabel;
    @FXML private Label lblMessage;
    @FXML private Button resendButton;

    private String targetEmail;
    private int timeSeconds = 300; // 5 phút
    private Timeline timeline;

    public void initialize() {
        setupOtpInputs();
        startTimer();
    }

    // Nhận email từ màn hình Register truyền sang
    public void setTargetEmail(String email) {
        this.targetEmail = email;
        lblMessage.setText("Mã xác thực đã gửi đến: " + email);
    }

    // Logic để khi nhập số tự nhảy sang ô tiếp theo
    private void setupOtpInputs() {
        TextField[] otps = {otp1, otp2, otp3, otp4, otp5, otp6};

        for (int i = 0; i < otps.length; i++) {
            final int index = i;

            otps[i].textProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.length() > 1) otps[index].setText(newVal.substring(0, 1));
                if (!newVal.matches("\\d*")) otps[index].setText(newVal.replaceAll("[^\\d]", ""));

                if (!newVal.isEmpty() && index < 5) {
                    otps[index + 1].requestFocus();
                }
            });

            otps[i].setOnKeyPressed(event -> {
                if (event.getCode().toString().equals("BACK_SPACE")) {
                    if (otps[index].getText().isEmpty() && index > 0) {
                        otps[index - 1].requestFocus();
                    }
                }
            });
        }
    }

    private void startTimer() {
        timeline = new Timeline();
        timeline.setCycleCount(Timeline.INDEFINITE);

        timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(1), event -> {
            timeSeconds--;
            int minutes = timeSeconds / 60;
            int seconds = timeSeconds % 60;
            timerLabel.setText(String.format("%02d:%02d", minutes, seconds));

            if (timeSeconds <= 0) {
                timeline.stop();
                timerLabel.setText("Mã hết hạn");
                resendButton.setDisable(false);
            }
        }));
        timeline.playFromStart();
    }

    @FXML
    private void handleVerify() {
        String otpCode = otp1.getText() + otp2.getText() + otp3.getText() +
                otp4.getText() + otp5.getText() + otp6.getText();

        if (otpCode.length() < 6) {
            showAlert("Lỗi", "Vui lòng nhập đủ 6 số OTP");
            return;
        }

        try {
            boolean isVerified = RMIClient.getInstance()
                    .getAuthService()
                    .verifyEmail(targetEmail, otpCode);

            if (isVerified) {
                if (timeline != null) timeline.stop();
                showAlert("Thành công", "Xác thực thành công!");

                // 👉 Dùng đúng hàm chuyển cảnh
                handleBackToLogin();

            } else {
                showAlert("Thất bại", "Mã OTP sai hoặc hết hạn.");

                otp1.setText(""); otp2.setText(""); otp3.setText("");
                otp4.setText(""); otp5.setText(""); otp6.setText("");

                otp1.requestFocus();
            }

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Lỗi kết nối", "Không thể kết nối đến Server: " + e.getMessage());
        }
    }

    @FXML
    private void handleResend() {
        try {
            timeSeconds = 300;
            resendButton.setDisable(true);
            timeline.playFromStart();
            showAlert("Thông báo", "Đã gửi lại mã mới!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleBackToLogin() {

        if (timeline != null) timeline.stop();

        if (stage == null) {
            stage = (Stage) otp1.getScene().getWindow();
        }

        // Đây là hàm chuẩn trong BaseController → không mất CSS
        switchScene("/view/login.fxml", 1200, 800);
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
