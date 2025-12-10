    package vku.chatapp.client.controller;

    import javafx.application.Platform;
    import javafx.collections.FXCollections;
    import javafx.collections.ObservableList;
    import javafx.fxml.FXML;
    import javafx.fxml.FXMLLoader;
    import javafx.geometry.Insets;
    import javafx.geometry.Pos;
    import javafx.scene.Scene;
    import javafx.scene.control.*;
    import javafx.scene.layout.*;
    import javafx.scene.text.Font;
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
    import vku.chatapp.common.model.Friend;
    import vku.chatapp.common.model.User;
    import vku.chatapp.common.protocol.P2PMessage;
    import vku.chatapp.common.protocol.P2PMessageType;
    import vku.chatapp.client.rmi.RMIClient;

    import java.io.IOException;
    import java.time.LocalDateTime;
    import java.time.format.DateTimeFormatter;
    import java.time.temporal.ChronoUnit;
    import java.util.List;
    import java.util.Timer;
    import java.util.TimerTask;
    import java.util.concurrent.Executors;
    import java.util.concurrent.ScheduledExecutorService;
    import java.util.concurrent.TimeUnit;

    public class MainController extends BaseController {
        // User Profile Components
        @FXML private Label usernameLabel;
        @FXML private Label statusLabel;

        // Search Components
        @FXML private TextField searchFriendField;
        @FXML private VBox searchResultBox;
        @FXML private Label searchResultName;
        @FXML private Label searchResultUsername;
        @FXML private Button addFriendButton;

        // Friend List Components
        @FXML private TextField filterFriendField;
        @FXML private ListView<UserDTO> friendListView;
        @FXML private ListView<String> chatListView;
        @FXML private VBox chatAreaContainer;

        // Friend Requests Components
        @FXML private Label requestBadgeLabel;
        @FXML private VBox receivedRequestsBox;
        @FXML private VBox sentRequestsBox;

        // P2P and Chat
        private P2PServer p2pServer;
        private P2PMessageHandler messageHandler;
        private ChatController chatController;

        // Services
        private FriendService friendService;
        private StatusUpdateService statusUpdateService;

        // Data
        private UserDTO selectedFriend;
        private UserDTO searchedUser;
        private ObservableList<UserDTO> friendList;
        private ObservableList<UserDTO> allFriends;

        // Timers
        private Timer heartbeatTimer;
        private ScheduledExecutorService requestRefreshScheduler;

        @FXML
        public void initialize() {
            // Initialize user info
            User currentUser = UserSession.getInstance().getCurrentUser();
            usernameLabel.setText(currentUser.getDisplayName());
            statusLabel.setText("Online");

            // Initialize services
            friendService = new FriendService();
            statusUpdateService = StatusUpdateService.getInstance();

            // Initialize data
            friendList = FXCollections.observableArrayList();
            allFriends = FXCollections.observableArrayList();
            friendListView.setItems(friendList);
            friendListView.setCellFactory(lv -> new FriendListCell());

            // Setup components
            initializeP2PServer();
            registerPeerWithServer();
            loadFriendList();
            setupChatArea();
            setupMessageHandlers();
            setupFriendSelection();
            setupFriendFilter();

            // Start background tasks
            startHeartbeat();
            startStatusPolling();
            loadFriendRequests();
            startRequestAutoRefresh();
        }

        // ==================== P2P Setup ====================

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

        // ==================== Status Updates ====================

        private void startStatusPolling() {
            statusUpdateService.addListener(this::handleStatusUpdate);
            statusUpdateService.startPolling();
        }

        private void handleStatusUpdate(Long userId, UserStatus newStatus) {
            for (UserDTO friend : allFriends) {
                if (friend.getId().equals(userId)) {
                    friend.setStatus(newStatus);
                    System.out.println("🔄 Friend " + friend.getDisplayName() + " is now " + newStatus);
                    break;
                }
            }

            friendListView.refresh();

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

        // ==================== Friend List Management ====================

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

        // ==================== Chat Setup ====================

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

        // ==================== Search User ====================

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
                    UserDTO foundUser = friendService.searchUserByUsername(username);

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
                        showInfo("Friend Request Sent",
                                "Friend request sent to " + searchedUser.getDisplayName());

                        searchFriendField.clear();
                        searchResultBox.setVisible(false);
                        searchResultBox.setManaged(false);
                        searchedUser = null;

                        loadFriendRequests();
                    } else {
                        showError("Failed", "Failed to send friend request. Please try again.");
                    }
                });
            }).start();
        }

        // ==================== Friend Requests Management ====================

        private void startRequestAutoRefresh() {
            requestRefreshScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            });

            requestRefreshScheduler.scheduleAtFixedRate(() -> {
                Platform.runLater(this::loadFriendRequests);
            }, 30, 30, TimeUnit.SECONDS);
        }

        private void loadFriendRequests() {
            new Thread(() -> {
                try {
                    Long currentUserId = UserSession.getInstance().getCurrentUser().getId();
                    List<Friend> receivedRequests = friendService.getPendingRequests(currentUserId);
                    List<Friend> sentRequests = friendService.getSentRequests(currentUserId);

                    Platform.runLater(() -> {
                        updateRequestBadge(receivedRequests.size());
                        displayReceivedRequests(receivedRequests);
                        displaySentRequests(sentRequests);
                    });
                } catch (Exception e) {
                    System.err.println("❌ Error loading friend requests: " + e.getMessage());
                    e.printStackTrace();
                }
            }).start();
        }

        private void updateRequestBadge(int count) {
            if (count > 0) {
                requestBadgeLabel.setText(String.valueOf(count));
                requestBadgeLabel.setVisible(true);
                requestBadgeLabel.setManaged(true);
            } else {
                requestBadgeLabel.setVisible(false);
                requestBadgeLabel.setManaged(false);
            }
        }

        private void displayReceivedRequests(List<Friend> requests) {
            receivedRequestsBox.getChildren().clear();

            if (requests.isEmpty()) {
                receivedRequestsBox.getChildren().add(createEmptyState(
                        "📭",
                        "No pending requests",
                        "You have no friend requests"
                ));
                return;
            }

            for (Friend request : requests) {
                receivedRequestsBox.getChildren().add(createReceivedRequestCard(request));
            }
        }

        private void displaySentRequests(List<Friend> requests) {
            sentRequestsBox.getChildren().clear();

            if (requests.isEmpty()) {
                sentRequestsBox.getChildren().add(createEmptyState(
                        "📤",
                        "No sent requests",
                        "You haven't sent any requests"
                ));
                return;
            }

            for (Friend request : requests) {
                sentRequestsBox.getChildren().add(createSentRequestCard(request));
            }
        }

        private VBox createReceivedRequestCard(Friend request) {
            VBox card = new VBox(8);
            card.setStyle("-fx-background-color: white; -fx-padding: 12; -fx-background-radius: 6; " +
                    "-fx-border-color: #edebe9; -fx-border-radius: 6; -fx-border-width: 1;");
            card.setMaxWidth(Double.MAX_VALUE);
            VBox.setMargin(card, new Insets(0, 0, 8, 0));

            // Get sender info via RMI
            new Thread(() -> {
                try {
                    UserDTO sender = RMIClient.getInstance()
                            .getUserService()
                            .getUserById(request.getUserId());

                    if (sender == null) return;

                    Platform.runLater(() -> {
                        // Header
                        HBox header = new HBox(10);
                        header.setAlignment(Pos.CENTER_LEFT);

                        Region avatar = new Region();
                        avatar.setStyle("-fx-background-color: #0078d4; -fx-background-radius: 18;");
                        avatar.setPrefSize(36, 36);

                        VBox userInfo = new VBox(1);
                        HBox.setHgrow(userInfo, Priority.ALWAYS);

                        Label nameLabel = new Label(sender.getDisplayName());
                        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

                        Label usernameLabel = new Label("@" + sender.getUsername());
                        usernameLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");

                        userInfo.getChildren().addAll(nameLabel, usernameLabel);
                        header.getChildren().addAll(avatar, userInfo);

                        // Time
                        if (request.getRequestedAt() != null) {
                            Label timeLabel = new Label(formatTimeAgo(request.getRequestedAt()));
                            timeLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 11px;");
                            card.getChildren().add(timeLabel);
                        }

                        // Actions
                        HBox actions = new HBox(8);
                        actions.setAlignment(Pos.CENTER_RIGHT);

                        Button acceptBtn = new Button("✓ Accept");
                        acceptBtn.setStyle("-fx-background-color: #16c60c; -fx-text-fill: white; " +
                                "-fx-font-size: 12px; -fx-padding: 6 16; -fx-cursor: hand; " +
                                "-fx-background-radius: 4;");
                        acceptBtn.setOnAction(e -> handleAcceptRequest(request.getId()));

                        Button rejectBtn = new Button("✗");
                        rejectBtn.setStyle("-fx-background-color: #e1e1e1; -fx-text-fill: #666; " +
                                "-fx-font-size: 12px; -fx-padding: 6 10; -fx-cursor: hand; " +
                                "-fx-background-radius: 4;");
                        rejectBtn.setOnAction(e -> handleRejectRequest(request.getId()));

                        actions.getChildren().addAll(rejectBtn, acceptBtn);

                        card.getChildren().addAll(header, actions);
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

            return card;
        }

        private VBox createSentRequestCard(Friend request) {
            VBox card = new VBox(8);
            card.setStyle("-fx-background-color: white; -fx-padding: 12; -fx-background-radius: 6; " +
                    "-fx-border-color: #edebe9; -fx-border-radius: 6; -fx-border-width: 1;");
            card.setMaxWidth(Double.MAX_VALUE);
            VBox.setMargin(card, new Insets(0, 0, 8, 0));

            // Get receiver info via RMI
            new Thread(() -> {
                try {
                    UserDTO receiver = RMIClient.getInstance()
                            .getUserService()
                            .getUserById(request.getFriendId());

                    if (receiver == null) return;

                    Platform.runLater(() -> {
                        HBox header = new HBox(10);
                        header.setAlignment(Pos.CENTER_LEFT);

                        Region avatar = new Region();
                        avatar.setStyle("-fx-background-color: #0078d4; -fx-background-radius: 18;");
                        avatar.setPrefSize(36, 36);

                        VBox userInfo = new VBox(1);
                        HBox.setHgrow(userInfo, Priority.ALWAYS);

                        Label nameLabel = new Label(receiver.getDisplayName());
                        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

                        Label statusLabel = new Label("⏳ Pending");
                        statusLabel.setStyle("-fx-text-fill: #ff8c00; -fx-font-size: 11px;");

                        userInfo.getChildren().addAll(nameLabel, statusLabel);

                        Button cancelBtn = new Button("Cancel");
                        cancelBtn.setStyle("-fx-background-color: #e1e1e1; -fx-text-fill: #666; " +
                                "-fx-font-size: 11px; -fx-padding: 6 12; -fx-cursor: hand; " +
                                "-fx-background-radius: 4;");
                        cancelBtn.setOnAction(e -> handleCancelRequest(request.getId()));

                        header.getChildren().addAll(avatar, userInfo, cancelBtn);
                        card.getChildren().add(header);
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

            return card;
        }

        private VBox createEmptyState(String emoji, String title, String subtitle) {
            VBox emptyState = new VBox(10);
            emptyState.setAlignment(Pos.CENTER);
            emptyState.setPadding(new Insets(40, 20, 40, 20));

            Label emojiLabel = new Label(emoji);
            emojiLabel.setFont(new Font(36));

            Label titleLabel = new Label(title);
            titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #333;");

            Label subtitleLabel = new Label(subtitle);
            subtitleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

            emptyState.getChildren().addAll(emojiLabel, titleLabel, subtitleLabel);
            return emptyState;
        }

        private void handleAcceptRequest(Long requestId) {
            new Thread(() -> {
                boolean success = friendService.acceptFriendRequest(requestId);
                Platform.runLater(() -> {
                    if (success) {
                        showInfo("Success", "Friend request accepted!");
                        loadFriendRequests();
                        loadFriendList();
                    } else {
                        showError("Error", "Failed to accept request");
                    }
                });
            }).start();
        }

        private void handleRejectRequest(Long requestId) {
            new Thread(() -> {
                boolean success = friendService.rejectFriendRequest(requestId);
                Platform.runLater(() -> {
                    if (success) {
                        loadFriendRequests();
                    } else {
                        showError("Error", "Failed to reject request");
                    }
                });
            }).start();
        }

        private void handleCancelRequest(Long requestId) {
            new Thread(() -> {
                Long currentUserId = UserSession.getInstance().getCurrentUser().getId();
                boolean success = friendService.cancelFriendRequest(requestId, currentUserId);
                Platform.runLater(() -> {
                    if (success) {
                        loadFriendRequests();
                    } else {
                        showError("Error", "Failed to cancel request");
                    }
                });
            }).start();
        }

        private String formatTimeAgo(LocalDateTime dateTime) {
            LocalDateTime now = LocalDateTime.now();
            long minutes = ChronoUnit.MINUTES.between(dateTime, now);

            if (minutes < 1) return "Just now";
            if (minutes < 60) return minutes + " min ago";

            long hours = ChronoUnit.HOURS.between(dateTime, now);
            if (hours < 24) return hours + " hour" + (hours > 1 ? "s" : "") + " ago";

            long days = ChronoUnit.DAYS.between(dateTime, now);
            if (days < 7) return days + " day" + (days > 1 ? "s" : "") + " ago";

            return dateTime.format(DateTimeFormatter.ofPattern("MMM dd"));
        }

        // ==================== Incoming Messages & Calls ====================

        private void handleIncomingMessage(P2PMessage message) {
            Platform.runLater(() -> {
                long senderId = message.getSenderId();
                PeerRegistry registry = PeerRegistry.getInstance();

                if (registry.getPeer(senderId) == null) {
                    registry.addPeer(new PeerInfo(
                            senderId,
                            message.getSourceIp(),
                            message.getSourcePort()
                    ));
                    System.out.println("🔧 Auto-added peer from message: " + senderId);
                }

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

        // ==================== Settings & Logout ====================

        @FXML
        private void handleSettings() {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Settings");
            alert.setHeaderText("⚙ Account Settings");

            ButtonType refreshButton = new ButtonType("🔄 Refresh");
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
                    loadFriendRequests();
                    showInfo("Refreshed", "✅ Data refreshed!");
                }
            });
        }

        private void handleLogout() {
            try {
                Long userId = UserSession.getInstance().getCurrentUser().getId();

                // Stop status polling
                statusUpdateService.stopPolling();
                statusUpdateService.removeListener(this::handleStatusUpdate);

                // Stop request refresh
                if (requestRefreshScheduler != null && !requestRefreshScheduler.isShutdown()) {
                    requestRefreshScheduler.shutdown();
                }

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

        // ==================== Friend List Cell ====================

        private class FriendListCell extends ListCell<UserDTO> {
            private HBox content;
            private Region statusDot;
            private Label nameLabel;
            private Label statusLabel;
            private Button chatButton;
            private Button removeButton;

            public FriendListCell() {
                content = new HBox(10);
                content.setAlignment(Pos.CENTER_LEFT);
                content.setStyle("-fx-padding: 8 6; -fx-cursor: hand;");

                statusDot = new Region();
                statusDot.setPrefSize(10, 10);
                statusDot.setStyle("-fx-background-radius: 5px;");

                VBox textBox = new VBox(2);
                HBox.setHgrow(textBox, Priority.ALWAYS);

                nameLabel = new Label();
                nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

                statusLabel = new Label();
                statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #605e5c;");

                textBox.getChildren().addAll(nameLabel, statusLabel);

                HBox buttons = new HBox(4);
                buttons.setAlignment(Pos.CENTER_RIGHT);

                chatButton = new Button("💬");
                chatButton.setStyle("-fx-background-color: #0078d4; -fx-text-fill: white; " +
                        "-fx-font-size: 12px; -fx-padding: 4 8; -fx-cursor: hand; " +
                        "-fx-background-radius: 4;");
                chatButton.setTooltip(new Tooltip("Chat"));

                removeButton = new Button("🗑");
                removeButton.setStyle("-fx-background-color: #e1e1e1; -fx-text-fill: #666; " +
                        "-fx-font-size: 11px; -fx-padding: 4 8; -fx-cursor: hand; " +
                        "-fx-background-radius: 4;");
                removeButton.setTooltip(new Tooltip("Remove"));

                buttons.getChildren().addAll(chatButton, removeButton);

                content.getChildren().addAll(statusDot, textBox, buttons);

                content.setOnMouseEntered(e ->
                        content.setStyle("-fx-padding: 8 6; -fx-cursor: hand; -fx-background-color: #f3f2f1;"));
                content.setOnMouseExited(e ->
                        content.setStyle("-fx-padding: 8 6; -fx-cursor: hand;"));
            }

            @Override
            protected void updateItem(UserDTO friend, boolean empty) {
                super.updateItem(friend, empty);

                if (empty || friend == null) {
                    setGraphic(null);
                } else {
                    nameLabel.setText(friend.getDisplayName());
                    statusLabel.setText(friend.getStatus().toString());

                    // Update status dot color
                    String dotColor = friend.getStatus() == UserStatus.ONLINE ? "#16c60c" : "#a19f9d";
                    statusDot.setStyle("-fx-background-color: " + dotColor + "; -fx-background-radius: 5px;");

                    chatButton.setOnAction(e -> {
                        onFriendSelected(friend);
                    });

                    removeButton.setOnAction(e -> {
                        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                        confirm.setTitle("Remove Friend");
                        confirm.setHeaderText("Remove " + friend.getDisplayName() + "?");
                        confirm.setContentText("This will remove them from your friend list.");
                        confirm.showAndWait().ifPresent(response -> {
                            if (response == ButtonType.OK) {
                                new Thread(() -> {
                                    Long currentUserId = UserSession.getInstance().getCurrentUser().getId();
                                    boolean success = friendService.removeFriend(currentUserId, friend.getId());
                                    Platform.runLater(() -> {
                                        if (success) {
                                            showInfo("Removed", friend.getDisplayName() + " has been removed.");
                                            loadFriendList();
                                        } else {
                                            showError("Error", "Failed to remove friend.");
                                        }
                                    });
                                }).start();
                            }
                        });
                    });

                    setGraphic(content);
                }
            }
        }
    }