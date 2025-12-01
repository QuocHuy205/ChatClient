// FILE: vku/chatapp/client/controller/MainController.java
// ✅ FIX: Thêm real-time status updates

package vku.chatapp.client.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import vku.chatapp.client.model.CallSession;
import vku.chatapp.client.model.UserSession;
import vku.chatapp.client.p2p.P2PServer;
import vku.chatapp.client.p2p.P2PMessageHandler;
import vku.chatapp.client.p2p.PeerRegistry;
import vku.chatapp.client.service.FriendService;
import vku.chatapp.client.service.StatusUpdateService;
import vku.chatapp.common.dto.PeerInfo;
import vku.chatapp.common.dto.UserDTO;
import vku.chatapp.common.enums.CallType;
import vku.chatapp.common.enums.UserStatus;
import vku.chatapp.common.protocol.P2PMessage;
import vku.chatapp.common.protocol.P2PMessageType;
import vku.chatapp.client.rmi.RMIClient;

import java.io.IOException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainController extends BaseController {
    @FXML private Label usernameLabel;
    @FXML private Label statusLabel;
    @FXML private TextField searchFriendField;
    @FXML private VBox searchResultBox;
    @FXML private Label searchResultName;
    @FXML private Label searchResultUsername;
    @FXML private Button addFriendButton;
    @FXML private TextField filterFriendField;
    @FXML private ListView<UserDTO> friendListView;
    @FXML private ListView<String> chatListView;
    @FXML private VBox chatAreaContainer;

    private P2PServer p2pServer;
    private P2PMessageHandler messageHandler;
    private ChatController chatController;
    private FriendService friendService;
    private UserDTO selectedFriend;
    private UserDTO searchedUser;
    private ObservableList<UserDTO> friendList;
    private ObservableList<UserDTO> allFriends;
    private Timer heartbeatTimer;
    private StatusUpdateService statusUpdateService; // ✅ NEW

    @FXML
    public void initialize() {
        usernameLabel.setText(UserSession.getInstance().getCurrentUser().getDisplayName());
        statusLabel.setText("Online");

        friendService = new FriendService();
        statusUpdateService = StatusUpdateService.getInstance(); // ✅ NEW
        friendList = FXCollections.observableArrayList();
        allFriends = FXCollections.observableArrayList();
        friendListView.setItems(friendList);
        friendListView.setCellFactory(lv -> new FriendListCell());

        initializeP2PServer();
        registerPeerWithServer();
        loadFriendList();
        setupChatArea();
        setupMessageHandlers();
        setupFriendSelection();
        setupFriendFilter();
        startHeartbeat();
        startStatusPolling(); // ✅ NEW
    }

    private void initializeP2PServer() {
        try {
            messageHandler = new P2PMessageHandler();
            p2pServer = new P2PServer(messageHandler);
            p2pServer.start(5000);
            System.out.println("✅ P2P Server started on port: " + p2pServer.getPort());
        } catch (IOException e) {
            System.err.println("❌ Failed to start P2P server: " + e.getMessage());
        }
    }

    private void registerPeerWithServer() {
        try {
            if (p2pServer != null && p2pServer.getPort() > 0) {
                Long userId = UserSession.getInstance().getCurrentUser().getId();

                PeerInfo peerInfo = new PeerInfo(
                        userId,
                        "localhost",
                        p2pServer.getPort()
                );

                boolean registered = RMIClient.getInstance()
                        .getPeerDiscoveryService()
                        .registerPeer(peerInfo);

                if (registered) {
                    System.out.println("✅ Peer registered successfully");

                    boolean statusUpdated = RMIClient.getInstance()
                            .getUserService()
                            .updateStatus(userId, UserStatus.ONLINE);

                    if (statusUpdated) {
                        System.out.println("✅ User status updated to ONLINE");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to register peer: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void startHeartbeat() {
        heartbeatTimer = new Timer(true);
        heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    Long userId = UserSession.getInstance().getCurrentUser().getId();
                    RMIClient.getInstance()
                            .getPeerDiscoveryService()
                            .updateHeartbeat(userId);
                } catch (Exception e) {
                    System.err.println("❌ Heartbeat failed: " + e.getMessage());
                }
            }
        }, 10000, 30000);
    }

    // ✅ NEW: Start polling for status updates
    private void startStatusPolling() {
        statusUpdateService.addListener(this::handleStatusUpdate);
        statusUpdateService.startPolling();
    }

    // ✅ NEW: Handle status updates
    private void handleStatusUpdate(Long userId, UserStatus newStatus) {
        // Find friend in list and update status
        for (UserDTO friend : allFriends) {
            if (friend.getId().equals(userId)) {
                friend.setStatus(newStatus);
                System.out.println("🔄 Friend " + friend.getDisplayName() + " is now " + newStatus);
                break;
            }
        }

        // Refresh the ListView
        friendListView.refresh();

        // Show notification
        if (newStatus == UserStatus.ONLINE) {
            UserDTO friend = allFriends.stream()
                    .filter(f -> f.getId().equals(userId))
                    .findFirst()
                    .orElse(null);

            if (friend != null) {
                System.out.println("✅ " + friend.getDisplayName() + " came online");
            }
        }
    }

    private void loadFriendList() {
        new Thread(() -> {
            try {
                Long currentUserId = UserSession.getInstance().getCurrentUser().getId();
                List<UserDTO> friends = friendService.getFriendList(currentUserId);

                Platform.runLater(() -> {
                    allFriends.clear();
                    friendList.clear();

                    for (UserDTO friend : friends) {
                        if (friend.getStatus() == null) {
                            friend.setStatus(UserStatus.OFFLINE);
                        }
                    }

                    allFriends.addAll(friends);
                    friendList.addAll(friends);

                    updatePeerRegistry();

                    System.out.println("✅ Loaded " + friends.size() + " friends");
                });

            } catch (Exception e) {
                System.err.println("❌ Error loading friends: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private void updatePeerRegistry() {
        new Thread(() -> {
            try {
                Long userId = UserSession.getInstance().getCurrentUser().getId();
                List<PeerInfo> onlineFriends = RMIClient.getInstance()
                        .getPeerDiscoveryService()
                        .getOnlineFriends(userId);

                for (PeerInfo peerInfo : onlineFriends) {
                    PeerRegistry.getInstance().addPeer(peerInfo);
                }

                Platform.runLater(() -> {
                    for (UserDTO friend : allFriends) {
                        PeerInfo peerInfo = PeerRegistry.getInstance().getPeerInfo(friend.getId());
                        if (peerInfo != null) {
                            friend.setStatus(UserStatus.ONLINE);
                            friend.setP2pAddress(peerInfo.getAddress());
                            friend.setP2pPort(peerInfo.getPort());
                        } else {
                            friend.setStatus(UserStatus.OFFLINE);
                        }
                    }
                    friendListView.refresh();
                });

            } catch (Exception e) {
                System.err.println("❌ Error updating peer registry: " + e.getMessage());
            }
        }).start();
    }

    private void setupFriendSelection() {
        friendListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                onFriendSelected(newVal);
            }
        });
    }

    private void setupFriendFilter() {
        filterFriendField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) {
                friendList.setAll(allFriends);
            } else {
                String filter = newVal.toLowerCase();
                friendList.setAll(allFriends.filtered(friend ->
                        friend.getDisplayName().toLowerCase().contains(filter) ||
                                friend.getUsername().toLowerCase().contains(filter)
                ));
            }
        });
    }

    private void setupChatArea() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/chat.fxml"));
            VBox chatView = loader.load();
            chatController = loader.getController();
            chatController.setMessageHandler(messageHandler);

            chatView.setVisible(false);
            chatView.setManaged(false);

            chatAreaContainer.getChildren().add(chatView);
            VBox.setVgrow(chatView, Priority.ALWAYS);

            System.out.println("✅ Chat view loaded");

        } catch (Exception e) {
            System.err.println("❌ Error loading chat view: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupMessageHandlers() {
        messageHandler.addListener(this::handleIncomingMessage);
    }

    private void onFriendSelected(UserDTO friend) {
        selectedFriend = friend;

        if (chatAreaContainer.getChildren().size() > 1) {
            chatAreaContainer.getChildren().get(0).setVisible(false);
            chatAreaContainer.getChildren().get(0).setManaged(false);
            chatAreaContainer.getChildren().get(1).setVisible(true);
            chatAreaContainer.getChildren().get(1).setManaged(true);
        }

        if (chatController != null) {
            chatController.openChat(friend);
        }

        System.out.println("✅ Chat opened with: " + friend.getDisplayName());
    }

    @FXML
    private void handleSearchUser() {
        String username = searchFriendField.getText().trim();

        if (username.isEmpty()) {
            searchResultBox.setVisible(false);
            searchResultBox.setManaged(false);
            return;
        }

        boolean alreadyFriend = allFriends.stream()
                .anyMatch(f -> f.getUsername().equalsIgnoreCase(username));

        if (alreadyFriend) {
            searchResultBox.setVisible(false);
            searchResultBox.setManaged(false);
            showInfo("Already Friends", "You are already friends with " + username);
            return;
        }

        new Thread(() -> {
            try {
                UserDTO foundUser = RMIClient.getInstance().getUserService()
                        .getUserByUsername(username);

                Platform.runLater(() -> {
                    if (foundUser == null) {
                        searchResultBox.setVisible(false);
                        searchResultBox.setManaged(false);
                        showError("Not Found", "User '" + username + "' not found");
                    } else if (foundUser.getId().equals(UserSession.getInstance().getCurrentUser().getId())) {
                        searchResultBox.setVisible(false);
                        searchResultBox.setManaged(false);
                        showInfo("That's You!", "You cannot add yourself as a friend");
                    } else {
                        searchedUser = foundUser;
                        searchResultName.setText(foundUser.getDisplayName());
                        searchResultUsername.setText("@" + foundUser.getUsername());
                        searchResultBox.setVisible(true);
                        searchResultBox.setManaged(true);
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    searchResultBox.setVisible(false);
                    searchResultBox.setManaged(false);
                    showError("Search Error", "Failed to search: " + e.getMessage());
                });
            }
        }).start();
    }

    @FXML
    private void handleAddFriendFromSearch() {
        if (searchedUser == null) return;

        addFriendButton.setDisable(true);

        new Thread(() -> {
            Long currentUserId = UserSession.getInstance().getCurrentUser().getId();
            boolean success = friendService.sendFriendRequest(currentUserId, searchedUser.getId());

            Platform.runLater(() -> {
                addFriendButton.setDisable(false);

                if (success) {
                    showInfo("Friend Added",
                            searchedUser.getDisplayName() + " has been added to your friends!");

                    searchFriendField.clear();
                    searchResultBox.setVisible(false);
                    searchResultBox.setManaged(false);
                    searchedUser = null;

                    loadFriendList();
                } else {
                    showError("Failed", "Failed to add friend. Please try again.");
                }
            });
        }).start();
    }

    private void handleIncomingMessage(P2PMessage message) {
        Platform.runLater(() -> {
            if (message.getType() == P2PMessageType.CALL_OFFER) {
                handleIncomingCall(message);
            }
        });
    }

    private void handleIncomingCall(P2PMessage message) {
        CallType callType = CallType.valueOf(message.getContent());

        UserDTO caller = allFriends.stream()
                .filter(f -> f.getId().equals(message.getSenderId()))
                .findFirst()
                .orElseGet(() -> {
                    UserDTO unknown = new UserDTO();
                    unknown.setId(message.getSenderId());
                    unknown.setDisplayName("Unknown");
                    return unknown;
                });

        CallSession callSession = new CallSession(
                message.getMessageId(),
                caller,
                callType,
                false
        );

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Incoming Call");
        alert.setHeaderText("📞 " + caller.getDisplayName() + " is calling");
        alert.setContentText("Type: " + callType);

        ButtonType answerButton = new ButtonType("Answer", ButtonBar.ButtonData.OK_DONE);
        ButtonType rejectButton = new ButtonType("Reject", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(answerButton, rejectButton);

        alert.showAndWait().ifPresent(response -> {
            if (response == answerButton) {
                showCallWindow(callSession);
            }
        });
    }

    private void showCallWindow(CallSession callSession) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/video-call.fxml"));
            VBox callView = loader.load();

            VideoCallController callController = loader.getController();
            callController.setMessageHandler(messageHandler);

            Stage callStage = new Stage();
            callController.setStage(callStage);

            Scene scene = new Scene(callView, 800, 600);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

            callStage.setTitle("Call - " + callSession.getPeer().getDisplayName());
            callStage.setScene(scene);
            callStage.initModality(Modality.NONE);
            callStage.show();

            if (callSession.isCaller()) {
                callController.initiateCall(callSession.getPeer(), callSession.getCallType());
            } else {
                callController.receiveCall(callSession);
            }

        } catch (Exception e) {
            System.err.println("❌ Error showing call window: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleSettings() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Settings");
        alert.setHeaderText("⚙ Account Settings");

        ButtonType refreshButton = new ButtonType("🔄 Refresh Friends");
        ButtonType profileButton = new ButtonType("👤 Profile");
        ButtonType logoutButton = new ButtonType("🚪 Logout");
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(refreshButton, profileButton, logoutButton, cancelButton);

        alert.showAndWait().ifPresent(response -> {
            if (response == logoutButton) {
                handleLogout();
            } else if (response == profileButton) {
                showInfo("Profile", "Profile editor coming soon!");
            } else if (response == refreshButton) {
                loadFriendList();
                showInfo("Refreshed", "✅ Friend list refreshed!");
            }
        });
    }

    private void handleLogout() {
        try {
            Long userId = UserSession.getInstance().getCurrentUser().getId();

            // Stop status polling
            statusUpdateService.stopPolling(); // ✅ NEW
            statusUpdateService.removeListener(this::handleStatusUpdate); // ✅ NEW

            // Update status to OFFLINE
            try {
                RMIClient.getInstance()
                        .getUserService()
                        .updateStatus(userId, UserStatus.OFFLINE);
                System.out.println("✅ User status updated to OFFLINE");
            } catch (Exception e) {
                System.err.println("⚠️ Error updating status to OFFLINE: " + e.getMessage());
            }

            // Unregister peer
            try {
                RMIClient.getInstance()
                        .getPeerDiscoveryService()
                        .unregisterPeer(userId);
                System.out.println("✅ Peer unregistered");
            } catch (Exception e) {
                System.err.println("⚠️ Error unregistering peer: " + e.getMessage());
            }

            // Stop heartbeat
            if (heartbeatTimer != null) {
                heartbeatTimer.cancel();
                System.out.println("✅ Heartbeat stopped");
            }

            // Stop P2P server
            if (p2pServer != null) {
                p2pServer.stop();
                System.out.println("✅ P2P server stopped");
            }

            // Clear peer registry
            PeerRegistry.getInstance().clear();

            // Clear session
            UserSession.getInstance().clear();

            if (stage == null) {
                stage = (Stage) usernameLabel.getScene().getWindow();
            }
            switchScene("/view/login.fxml", 1200, 800);

        } catch (Exception e) {
            System.err.println("❌ Error during logout: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static class FriendListCell extends ListCell<UserDTO> {
        @Override
        protected void updateItem(UserDTO friend, boolean empty) {
            super.updateItem(friend, empty);

            if (empty || friend == null) {
                setText(null);
                setGraphic(null);
            } else {
                HBox content = new HBox(10);
                content.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                content.setPadding(new javafx.geometry.Insets(8));

                Region statusDot = new Region();
                statusDot.setPrefSize(10, 10);
                statusDot.setStyle(
                        "-fx-background-color: " +
                                (friend.getStatus() == UserStatus.ONLINE ? "#16c60c" : "#a19f9d") + ";" +
                                "-fx-background-radius: 5px;"
                );

                VBox textBox = new VBox(2);
                Label nameLabel = new Label(friend.getDisplayName());
                nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

                Label statusLabel = new Label(friend.getStatus().toString());
                statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #605e5c;");

                textBox.getChildren().addAll(nameLabel, statusLabel);
                content.getChildren().addAll(statusDot, textBox);
                setGraphic(content);
            }
        }
    }
}