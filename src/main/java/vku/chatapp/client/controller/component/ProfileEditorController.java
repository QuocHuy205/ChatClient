package vku.chatapp.client.controller.component;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import vku.chatapp.client.controller.BaseController;
import vku.chatapp.common.dto.UserDTO;
import vku.chatapp.common.enums.UserStatus;
import vku.chatapp.client.model.UserSession;
import vku.chatapp.client.service.UserService;
import vku.chatapp.common.model.User;

import java.io.File;
import java.util.concurrent.CompletableFuture;

public class ProfileEditorController extends BaseController {

    @FXML private Button backButton;
    @FXML private ImageView avatarImageView;
    @FXML private Button changeAvatarButton;
    @FXML private Label avatarSizeLabel;
    @FXML private Circle avatarBorder;

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


    @FXML private Button saveButton;
    @FXML private Button cancelButton;
    @FXML private StackPane loadingOverlay;
    private Stage stage;

    private final UserService userService;
    private User currentUser;
    private String newAvatarUrl;
    private String newAvatarLocalPath;  // Local path for preview
    private boolean avatarChanged = false;

    public ProfileEditorController() {
        this.userService = new UserService();
    }

    @FXML
    public void initialize() {
        setupAvatarClip();
        setupEventHandlers();
        setupValidation();
        setupRealtimeValidation();
        loadUserProfile();
    }

    public void setStage(Stage stage) {
        this.stage = stage;
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

            if (currentUser.getAvatarUrl() != null && !currentUser.getAvatarUrl().isEmpty()) {
                loadAvatar(currentUser.getAvatarUrl());
            }

            bioCharCountLabel.setText(bioField.getText().length() + " / 500");
        }
    }

    private void loadDefaultAvatar() {
        try {
            // Tạo một vùng chứa hình vuông với màu nền bạn muốn
            Region colorRegion = new Region();
            colorRegion.setPrefSize(120, 120); // Kích thước bằng với ImageView của bạn
            colorRegion.setStyle("-fx-background-color: #3498db; -fx-background-radius: 50;"); // Màu xanh, bo tròn

            // Chụp ảnh vùng màu đó và set vào ImageView
            SnapshotParameters sp = new SnapshotParameters();
            sp.setFill(Color.TRANSPARENT);
            Image defaultImage = colorRegion.snapshot(sp, null);

            avatarImageView.setImage(defaultImage);
        } catch (Exception e) {
            System.err.println("Error setting color avatar: " + e.getMessage());
        }
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
            showError("Error", errors.toString());
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

    private void handleSaveProfile() {
        if (!validateForm()) {
            return;
        }

        showLoading(true);

        CompletableFuture.runAsync(() -> {
            try {
                String avatarUrl = currentUser.getAvatarUrl();

                // Upload avatar if changed
                if (avatarChanged && newAvatarLocalPath != null) {
                    System.out.println("📤 Uploading new avatar...");
                    avatarSizeLabel.setText("Uploading to Google Drive...");

                    String uploadedUrl = userService.uploadAvatar(currentUser.getId(), newAvatarLocalPath);

                    if (uploadedUrl != null) {
                        avatarUrl = uploadedUrl;
                        System.out.println("✅ Avatar uploaded: " + uploadedUrl);
                    } else {
                        Platform.runLater(() -> {
                            showLoading(false);
                            showError("Upload Failed", "Failed to upload avatar to Google Drive");
                        });
                        return;
                    }
                }

                // Update profile
                String displayName = fullNameField.getText().trim();
                String bio = bioField.getText().trim();

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

                // Handle password change
                if (changePasswordCheckbox.isSelected()) {
                    Platform.runLater(() -> {
                        showInfo("Coming Soon", "Password change feature will be available soon");
                    });
                }

                // Reload user data
                UserDTO updatedUser = userService.getUserById(currentUser.getId());
                if (updatedUser != null) {
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

    private void handleBack() {
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

        return displayNameChanged || emailChanged || bioChanged || avatarChanged;
    }

    private void loadAvatar(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            loadDefaultAvatar();
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                Image image;

                // Check if it's a URL (Cloudinary, Google Drive, or any online source)
                if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) {
                    image = new Image(avatarUrl, true); // true = background loading
                } else {
                    // Try loading from local file or resources
                    File avatarFile = new File(avatarUrl);
                    if (avatarFile.exists()) {
                        image = new Image(avatarFile.toURI().toString());
                    } else {
                        image = new Image(getClass().getResourceAsStream(avatarUrl));
                    }
                }

                Platform.runLater(() -> {
                    if (image.isError()) {
                        System.err.println("Error loading avatar: " + image.getException().getMessage());
                        loadDefaultAvatar();
                    } else {
                        avatarImageView.setImage(image);
                        System.out.println("✅ Avatar loaded successfully");
                    }
                });

            } catch (Exception e) {
                System.err.println("Error loading avatar: " + e.getMessage());
                Platform.runLater(this::loadDefaultAvatar);
            }
        });
    }


    private void setupRealtimeValidation() {
        emailField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) {
                String email = emailField.getText().trim();
                if (!email.isEmpty() && !isValidEmail(email)) {
                    emailErrorLabel.setText("⚠ Invalid email format");
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

        emailField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (emailErrorLabel.isVisible()) {
                emailField.setStyle("");
            }
        });
    }

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
                // Check file size (max 5MB)
                long fileSizeInBytes = selectedFile.length();
                long fileSizeInMB = fileSizeInBytes / (1024 * 1024);

                if (fileSizeInMB > 5) {
                    showError("File Too Large",
                            "Avatar size must be less than 5MB\nCurrent size: " + fileSizeInMB + "MB");
                    return;
                }

                // Validate image
                Image testImage = new Image(selectedFile.toURI().toString());
                if (testImage.isError()) {
                    showError("Invalid Image", "Selected file is not a valid image");
                    return;
                }

                // Save local path for upload later
                newAvatarLocalPath = selectedFile.getAbsolutePath();
                avatarChanged = true;

                // Display preview
                loadAvatar(selectedFile.toURI().toString());

                avatarSizeLabel.setText("✅ Avatar changed (will be uploaded on save)");
                avatarSizeLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");

                System.out.println("✅ Avatar preview loaded: " + newAvatarLocalPath);

            } catch (Exception e) {
                showError("Error", "Failed to load image: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    private void setupAvatarClip() {
        Circle clip = new Circle();

        // Tâm hình tròn
        clip.centerXProperty().bind(avatarImageView.fitWidthProperty().divide(2));
        clip.centerYProperty().bind(avatarImageView.fitHeightProperty().divide(2));

        // Bán kính
        clip.radiusProperty().bind(
                avatarImageView.fitWidthProperty().divide(2)
        );

        avatarImageView.setClip(clip);
    }

}