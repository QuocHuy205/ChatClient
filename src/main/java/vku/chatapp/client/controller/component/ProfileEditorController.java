package vku.chatapp.client.controller.component;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import vku.chatapp.client.controller.BaseController;
import vku.chatapp.common.dto.UserDTO;
import vku.chatapp.common.enums.UserStatus;
import vku.chatapp.client.model.UserSession;
import vku.chatapp.client.service.UserService;
import vku.chatapp.common.model.User;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

public class ProfileEditorController extends BaseController {

    @FXML private Button backButton;
    @FXML private ImageView avatarImageView;
    @FXML private Button changeAvatarButton;
    @FXML private Label avatarSizeLabel;

    @FXML private TextField usernameField;
    @FXML private TextField fullNameField;
    @FXML private TextField emailField;

    @FXML private TextArea bioField;
    @FXML private Label bioCharCountLabel;
    @FXML private Label emailErrorLabel;

    @FXML private CheckBox changePasswordCheckbox;
    @FXML private VBox passwordSection;
    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label passwordErrorLabel;

    @FXML private ComboBox<UserStatus> statusComboBox;

    @FXML private Button saveButton;
    @FXML private Button cancelButton;
    @FXML private StackPane loadingOverlay;
    private Stage stage;

    private final UserService userService;
    private User currentUser;
    private String newAvatarUrl;
    private boolean avatarChanged = false;

    // Thư mục lưu avatar (có thể cấu hình)
    private static final String AVATAR_DIRECTORY = "avatars/";

    public ProfileEditorController() {
        this.userService = new UserService();
    }

    @FXML
    public void initialize() {
        setupStatusComboBox();
        setupEventHandlers();
        setupValidation();
        setupRealtimeValidation();
        loadUserProfile();

        // Tạo thư mục avatars nếu chưa có
        createAvatarDirectory();
    }

    // Thêm setter cho stage
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    private void createAvatarDirectory() {
        try {
            Files.createDirectories(Paths.get(AVATAR_DIRECTORY));
        } catch (IOException e) {
            System.err.println("Failed to create avatar directory: " + e.getMessage());
        }
    }

    private void setupStatusComboBox() {
        statusComboBox.getItems().addAll(
                UserStatus.ONLINE,
                UserStatus.AWAY,
                UserStatus.BUSY,
                UserStatus.OFFLINE
        );

        // Custom cell factory to display status with icons
        statusComboBox.setCellFactory(param -> new ListCell<UserStatus>() {
            @Override
            protected void updateItem(UserStatus status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                } else {
                    setText(getStatusText(status));
                    setStyle(getStatusStyle(status));
                }
            }
        });

        statusComboBox.setButtonCell(new ListCell<UserStatus>() {
            @Override
            protected void updateItem(UserStatus status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                } else {
                    setText(getStatusText(status));
                }
            }
        });
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> handleBack());
        changeAvatarButton.setOnAction(e -> handleChangeAvatar());
        saveButton.setOnAction(e -> handleSaveProfile());
        cancelButton.setOnAction(e -> handleBack());

        changePasswordCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            passwordSection.setVisible(newVal);
            passwordSection.setManaged(newVal);
            if (!newVal) {
                clearPasswordFields();
            }
        });

        bioField.textProperty().addListener((obs, oldVal, newVal) -> {
            int length = newVal != null ? newVal.length() : 0;
            bioCharCountLabel.setText(length + " / 500");

            if (length > 500) {
                bioField.setText(oldVal);
            }
        });
    }

    private void setupValidation() {
        // Email validation
        emailField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty() && !isValidEmail(newVal)) {
                emailErrorLabel.setText("Invalid email format");
                emailErrorLabel.setVisible(true);
                emailErrorLabel.setManaged(true);
            } else {
                emailErrorLabel.setVisible(false);
                emailErrorLabel.setManaged(false);
            }
        });

        // Password validation
        newPasswordField.textProperty().addListener((obs, oldVal, newVal) -> {
            validatePasswordFields();
        });

        confirmPasswordField.textProperty().addListener((obs, oldVal, newVal) -> {
            validatePasswordFields();
        });
    }

    private void loadUserProfile() {
        currentUser = UserSession.getInstance().getCurrentUser();

        if (currentUser != null) {
            usernameField.setText(currentUser.getUsername());
            fullNameField.setText(currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "");
            emailField.setText(currentUser.getEmail() != null ? currentUser.getEmail() : "");
            bioField.setText(currentUser.getBio() != null ? currentUser.getBio() : "");
            statusComboBox.setValue(currentUser.getStatus());

            if (currentUser.getAvatarUrl() != null && !currentUser.getAvatarUrl().isEmpty()) {
                loadAvatar(currentUser.getAvatarUrl());
            }

            bioCharCountLabel.setText(bioField.getText().length() + " / 500");
        }
    }


    private void loadDefaultAvatar() {
        try {
            Image defaultImage = new Image(getClass().getResourceAsStream("/images/default-avatar.png"));
            avatarImageView.setImage(defaultImage);
        } catch (Exception e) {
            System.err.println("Error loading default avatar: " + e.getMessage());
        }
    }

    private String getFileExtension(String fileName) {
        int lastIndexOf = fileName.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return ""; // empty extension
        }
        return fileName.substring(lastIndexOf);
    }


    private boolean validateForm() {
        StringBuilder errors = new StringBuilder();

        if (fullNameField.getText().trim().isEmpty()) {
            errors.append("Display name is required\n");
        }

        String email = emailField.getText().trim();
        if (!email.isEmpty() && !isValidEmail(email)) {
            errors.append("Invalid email format\n");
        }

        if (changePasswordCheckbox.isSelected()) {
            if (currentPasswordField.getText().isEmpty()) {
                errors.append("Current password is required\n");
            }

            if (newPasswordField.getText().isEmpty()) {
                errors.append("New password is required\n");
            } else if (newPasswordField.getText().length() < 6) {
                errors.append("New password must be at least 6 characters\n");
            }

            if (!newPasswordField.getText().equals(confirmPasswordField.getText())) {
                errors.append("Passwords do not match\n");
            }
        }

        if (errors.length() > 0) {
            showError("error", errors.toString());
            return false;
        }

        return true;
    }

    private void validatePasswordFields() {
        if (newPasswordField.getText().isEmpty()) {
            passwordErrorLabel.setVisible(false);
            passwordErrorLabel.setManaged(false);
            return;
        }

        if (newPasswordField.getText().length() < 6) {
            passwordErrorLabel.setText("Password must be at least 6 characters");
            passwordErrorLabel.setVisible(true);
            passwordErrorLabel.setManaged(true);
            return;
        }

        if (!confirmPasswordField.getText().isEmpty() &&
                !newPasswordField.getText().equals(confirmPasswordField.getText())) {
            passwordErrorLabel.setText("Passwords do not match");
            passwordErrorLabel.setVisible(true);
            passwordErrorLabel.setManaged(true);
            return;
        }

        passwordErrorLabel.setVisible(false);
        passwordErrorLabel.setManaged(false);
    }

    private void clearPasswordFields() {
        currentPasswordField.clear();
        newPasswordField.clear();
        confirmPasswordField.clear();
        passwordErrorLabel.setVisible(false);
        passwordErrorLabel.setManaged(false);
    }


    private void showLoading(boolean show) {
        loadingOverlay.setVisible(show);
        loadingOverlay.setManaged(show);
        saveButton.setDisable(show);
        cancelButton.setDisable(show);
    }

    private boolean isValidEmail(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }

    private String getStatusText(UserStatus status) {
        return switch (status) {
            case ONLINE -> "🟢 Online";
            case AWAY -> "🟡 Away";
            case BUSY -> "🔴 Busy";
            case OFFLINE -> "⚫ Offline";
            default -> status.name();
        };
    }

    private String getStatusStyle(UserStatus status) {
        return switch (status) {
            case ONLINE -> "-fx-text-fill: #4CAF50;";
            case AWAY -> "-fx-text-fill: #FFC107;";
            case BUSY -> "-fx-text-fill: #F44336;";
            case OFFLINE -> "-fx-text-fill: #9E9E9E;";
            default -> "";
        };
    }

    private void handleSaveProfile() {
        if (!validateForm()) {
            return;
        }

        showLoading(true);

        CompletableFuture.runAsync(() -> {
            try {
                // Cập nhật thông tin cơ bản
                String displayName = fullNameField.getText().trim();
                String email = emailField.getText().trim();
                String bio = bioField.getText().trim();
                String avatarUrl = avatarChanged ? newAvatarUrl : currentUser.getAvatarUrl();

                boolean profileUpdated = userService.updateProfile(
                        currentUser.getId(),
                        displayName,
                        bio,
                        avatarUrl
                );

                if (!profileUpdated) {
                    Platform.runLater(() -> {
                        showLoading(false);
                        showError("Update Failed", "Failed to update profile");
                    });
                    return;
                }

                // Cập nhật status nếu thay đổi
                UserStatus newStatus = statusComboBox.getValue();
                if (newStatus != null && newStatus != currentUser.getStatus()) {
                    boolean statusUpdated = userService.updateStatus(currentUser.getId(), newStatus);
                    if (statusUpdated) {
                        System.out.println("✅ Status updated to: " + newStatus);
                    }
                }

                // Xử lý đổi mật khẩu nếu có
                if (changePasswordCheckbox.isSelected()) {
                    String currentPassword = currentPasswordField.getText();
                    String newPassword = newPasswordField.getText();

                    // TODO: Implement password change when server API is ready
                    // boolean passwordChanged = authService.changePassword(
                    //     currentUser.getId(),
                    //     currentPassword,
                    //     newPassword
                    // );

                    Platform.runLater(() -> {
                        showInfo("Coming Soon", "Password change feature will be available soon");
                    });
                }

                // Reload user data from server
                UserDTO updatedUser = userService.getUserById(currentUser.getId());
                if (updatedUser != null) {
                    // Cập nhật session với thông tin mới
                    User sessionUser = UserSession.getInstance().getCurrentUser();
                    sessionUser.setDisplayName(updatedUser.getDisplayName());
                    sessionUser.setBio(updatedUser.getBio());
                    sessionUser.setAvatarUrl(updatedUser.getAvatarUrl());
                    sessionUser.setEmail(updatedUser.getEmail());
                    sessionUser.setStatus(updatedUser.getStatus());

                    currentUser = sessionUser;
                }

                Platform.runLater(() -> {
                    showLoading(false);
                    showInfo("Success", "Profile updated successfully!");
                    avatarChanged = false;
                    avatarSizeLabel.setText("Click to upload photo (max 5MB)");
                    avatarSizeLabel.setStyle("");

                    // Đóng dialog sau 1.5 giây
                    new Thread(() -> {
                        try {
                            Thread.sleep(1500);
                            Platform.runLater(this::handleBack);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }).start();
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    showLoading(false);
                    showError("Error", "Failed to update profile: " + e.getMessage());
                });
                e.printStackTrace();
            }
        });
    }

    // Cải thiện handleBack
    private void handleBack() {
        // Kiểm tra nếu có thay đổi chưa lưu
        if (hasUnsavedChanges()) {
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Unsaved Changes");
            confirmAlert.setHeaderText("You have unsaved changes");
            confirmAlert.setContentText("Do you want to discard your changes?");

            ButtonType discardButton = new ButtonType("Discard");
            ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            confirmAlert.getButtonTypes().setAll(discardButton, cancelButton);

            confirmAlert.showAndWait().ifPresent(response -> {
                if (response == discardButton) {
                    closeWindow();
                }
            });
        } else {
            closeWindow();
        }
    }

    private void closeWindow() {
        if (stage != null) {
            stage.close();
        } else {
            Stage currentStage = (Stage) backButton.getScene().getWindow();
            currentStage.close();
        }
    }

    // Kiểm tra có thay đổi chưa lưu không
    private boolean hasUnsavedChanges() {
        if (currentUser == null) return false;

        boolean displayNameChanged = !fullNameField.getText().trim().equals(
                currentUser.getDisplayName() != null ? currentUser.getDisplayName() : ""
        );

        boolean emailChanged = !emailField.getText().trim().equals(
                currentUser.getEmail() != null ? currentUser.getEmail() : ""
        );

        boolean bioChanged = !bioField.getText().trim().equals(
                currentUser.getBio() != null ? currentUser.getBio() : ""
        );

        boolean statusChanged = statusComboBox.getValue() != currentUser.getStatus();

        return displayNameChanged || emailChanged || bioChanged || statusChanged || avatarChanged;
    }

    // Cải thiện loadAvatar với xử lý lỗi tốt hơn
    private void loadAvatar(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            loadDefaultAvatar();
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                Image image;
                File avatarFile = new File(avatarUrl);

                // Kiểm tra nếu là đường dẫn local
                if (avatarFile.exists()) {
                    image = new Image(avatarFile.toURI().toString());
                } else if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) {
                    // Nếu là URL
                    image = new Image(avatarUrl, true); // true = load in background
                } else {
                    // Thử load từ resources
                    image = new Image(getClass().getResourceAsStream(avatarUrl));
                }

                Platform.runLater(() -> {
                    if (image.isError()) {
                        System.err.println("Error loading avatar: " + image.getException().getMessage());
                        loadDefaultAvatar();
                    } else {
                        avatarImageView.setImage(image);
                    }
                });

            } catch (Exception e) {
                System.err.println("Error loading avatar: " + e.getMessage());
                Platform.runLater(this::loadDefaultAvatar);
            }
        });
    }

    // Thêm validation realtime cho email
    private void setupRealtimeValidation() {
        emailField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) { // Lost focus
                String email = emailField.getText().trim();
                if (!email.isEmpty() && !isValidEmail(email)) {
                    emailErrorLabel.setText("❌ Invalid email format");
                    emailErrorLabel.setVisible(true);
                    emailErrorLabel.setManaged(true);
                    emailField.setStyle("-fx-border-color: red;");
                } else {
                    emailErrorLabel.setVisible(false);
                    emailErrorLabel.setManaged(false);
                    emailField.setStyle("");
                }
            }
        });

        // Clear error when user starts typing
        emailField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (emailErrorLabel.isVisible()) {
                emailField.setStyle("");
            }
        });
    }

    // Cải thiện handleChangeAvatar với preview tốt hơn
    private void handleChangeAvatar() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Profile Picture");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files",
                        "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp")
        );

        Stage currentStage = stage != null ? stage : (Stage) changeAvatarButton.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(currentStage);

        if (selectedFile != null) {
            try {
                // Kiểm tra kích thước file (max 5MB)
                long fileSizeInBytes = selectedFile.length();
                long fileSizeInMB = fileSizeInBytes / (1024 * 1024);

                if (fileSizeInMB > 5) {
                    showError("File Too Large",
                            "Avatar size must be less than 5MB\nCurrent size: " + fileSizeInMB + "MB");
                    return;
                }

                // Kiểm tra xem có phải file ảnh hợp lệ không
                Image testImage = new Image(selectedFile.toURI().toString());
                if (testImage.isError()) {
                    showError("Invalid Image", "Selected file is not a valid image");
                    return;
                }

                // Copy file to avatars directory with unique name
                String fileExtension = getFileExtension(selectedFile.getName());
                String newFileName = "avatar_" + currentUser.getId() + "_" +
                        System.currentTimeMillis() + fileExtension;
                String newFilePath = AVATAR_DIRECTORY + newFileName;

                // Tạo thư mục nếu chưa có
                Files.createDirectories(Paths.get(AVATAR_DIRECTORY));

                Files.copy(selectedFile.toPath(), Paths.get(newFilePath),
                        StandardCopyOption.REPLACE_EXISTING);

                newAvatarUrl = newFilePath;
                avatarChanged = true;

                // Display preview
                loadAvatar(newFilePath);

                avatarSizeLabel.setText("✅ Avatar changed (not saved yet)");
                avatarSizeLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");

                System.out.println("✅ Avatar preview loaded: " + newFilePath);

            } catch (IOException e) {
                showError("Error", "Failed to load image: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}