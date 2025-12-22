// FILE: vku/chatapp/client/controller/MainController.java
// ✅ OPTIMIZED: Faster peer discovery, better connection

package vku.chatapp.client.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import vku.chatapp.client.controller.component.ProfileEditorController;
import vku.chatapp.client.model.CallSession;
import vku.chatapp.client.model.UserSession;
import vku.chatapp.client.p2p.P2PServer;
import vku.chatapp.client.p2p.P2PMessageHandler;
import vku.chatapp.client.p2p.PeerRegistry;
import vku.chatapp.client.service.AuthService;
import vku.chatapp.client.service.FriendService;
import vku.chatapp.client.service.StatusUpdateService;
import vku.chatapp.client.service.UserService;
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
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    @FXML private VBox sidebarContainer;

    @FXML private Label requestBadgeLabel;
    @FXML private VBox receivedRequestsBox;
    @FXML private VBox sentRequestsBox;

    private P2PServer p2pServer;
    private P2PMessageHandler messageHandler;
    private ChatController chatController;
    private FriendService friendService;
    private UserDTO selectedFriend;
    private UserDTO searchedUser;
    private ObservableList<UserDTO> friendList;
    private ObservableList<UserDTO> allFriends;
    private Timer heartbeatTimer;
    private StatusUpdateService statusUpdateService;

    private ScheduledExecutorService requestRefreshScheduler;

    // ✅ Thread pool for async operations
    private ExecutorService executorService;

    @FXML
    public void initialize() {
        usernameLabel.setText(UserSession.getInstance().getCurrentUser().getDisplayName());
        statusLabel.setText("Online");

        friendService = new FriendService();
        statusUpdateService = StatusUpdateService.getInstance();
        friendList = FXCollections.observableArrayList();
        allFriends = FXCollections.observableArrayList();
        friendListView.setItems(friendList);
        friendListView.setCellFactory(lv -> new FriendListCell());

        // ✅ Create thread pool
        executorService = Executors.newCachedThreadPool();

        initializeP2PServer();
        registerPeerWithServer();
        loadFriendList();
        setupChatArea();
        setupMessageHandlers();
        setupFriendSelection();
        setupFriendFilter();
        startHeartbeat();
        startStatusPolling();
        loadFriendRequests();
        startRequestAutoRefresh();
        Platform.runLater(() -> {
            Stage stage = (Stage) usernameLabel.getScene().getWindow();
            this.stage = stage; // gán cho BaseController

            stage.setOnCloseRequest(event -> {
                System.out.println("🚪 App window closed");
                handleAppExit();
            });
        });

    }

    private void initializeP2PServer() {
        try {
            messageHandler = new P2PMessageHandler();
            p2pServer = new P2PServer(messageHandler);
            p2pServer.start(5000);

            int port = p2pServer.getPort();
            vku.chatapp.client.media.MediaManager.getInstance().setLocalP2PPort(port);
            vku.chatapp.client.p2p.P2PClient.setLocalP2PPort(port);

            System.out.println("✅ P2P Server started on port: " + port);
        } catch (IOException e) {
            System.err.println("❌ Failed to start P2P server: " + e.getMessage());
        }
    }

    private String getLocalIPAddress() {
        try {
            // Method 1: Connect to external server
            try (Socket socket = new Socket()) {
                socket.connect(new java.net.InetSocketAddress("8.8.8.8", 80), 2000);
                String ip = socket.getLocalAddress().getHostAddress();
                System.out.println("🌐 Local IP (via 8.8.8.8): " + ip);
                return ip;
            }
        } catch (Exception e1) {
            // Method 2: Use InetAddress
            try {
                InetAddress localHost = InetAddress.getLocalHost();
                String ip = localHost.getHostAddress();

                if (!ip.equals("127.0.0.1") && !ip.startsWith("127.")) {
                    System.out.println("🌐 Local IP (via getLocalHost): " + ip);
                    return ip;
                }
            } catch (Exception e2) {
                // Method 3: Scan network interfaces
                try {
                    java.util.Enumeration<java.net.NetworkInterface> interfaces =
                            java.net.NetworkInterface.getNetworkInterfaces();

                    while (interfaces.hasMoreElements()) {
                        java.net.NetworkInterface iface = interfaces.nextElement();

                        if (iface.isLoopback() || !iface.isUp()) {
                            continue;
                        }

                        java.util.Enumeration<InetAddress> addresses = iface.getInetAddresses();
                        while (addresses.hasMoreElements()) {
                            InetAddress addr = addresses.nextElement();

                            if (addr instanceof java.net.Inet4Address &&
                                    !addr.isLoopbackAddress() &&
                                    addr.isSiteLocalAddress()) {

                                String ip = addr.getHostAddress();
                                System.out.println("🌐 Local IP (via NetworkInterface): " + ip);
                                return ip;
                            }
                        }
                    }
                } catch (Exception e3) {
                    System.err.println("⚠️ All methods failed: " + e3.getMessage());
                }
            }
        }

        System.err.println("❌ Using localhost fallback");
        return "localhost";
    }

    private void registerPeerWithServer() {
        // ✅ Async registration for faster startup
        executorService.submit(() -> {
            try {
                if (p2pServer != null && p2pServer.getPort() > 0) {
                    Long userId = UserSession.getInstance().getCurrentUser().getId();
                    String localIP = getLocalIPAddress();

                    PeerInfo peerInfo = new PeerInfo(userId, localIP, p2pServer.getPort());

//                    PeerInfo peerInfo = new PeerInfo(
//                            userId,
//                            "localhost",
//                            p2pServer.getPort()
//                    );

                    boolean registered = RMIClient.getInstance()
                            .getPeerDiscoveryService()
                            .registerPeer(peerInfo);

                    if (registered) {
                        System.out.println("✅ Peer registered: " + localIP + ":" + p2pServer.getPort());

                        // Update status async
                        RMIClient.getInstance()
                                .getUserService()
                                .updateStatus(userId, UserStatus.ONLINE);

                        System.out.println("✅ Status set to ONLINE");
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ Registration failed: " + e.getMessage());
            }
        });
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
                    System.err.println("❌ Heartbeat failed → OFFLINE");

                    cancel();
                }
            }
        }, 0, 5000);
    }


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
    }

    private void loadFriendList() {
        // ✅ Async loading
        executorService.submit(() -> {
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

                    System.out.println("✅ Loaded " + friends.size() + " friends");

                    // Update peer registry async
                    updatePeerRegistry();
                });

            } catch (Exception e) {
                System.err.println("❌ Error loading friends: " + e.getMessage());
            }
        });
    }

    private void updatePeerRegistry() {
        // ✅ Async peer registry update
        executorService.submit(() -> {
            try {
                Long userId = UserSession.getInstance().getCurrentUser().getId();
                List<PeerInfo> onlineFriends = RMIClient.getInstance()
                        .getPeerDiscoveryService()
                        .getOnlineFriends(userId);

                System.out.println("📡 Received " + onlineFriends.size() + " online peers");

                for (PeerInfo peerInfo : onlineFriends) {
                    PeerRegistry.getInstance().addPeer(peerInfo);
                    System.out.println("   → Peer " + peerInfo.getUserId() + " at " +
                            peerInfo.getAddress() + ":" + peerInfo.getPort());
                }

                Platform.runLater(() -> {
                    for (UserDTO friend : allFriends) {
                        PeerInfo peerInfo = PeerRegistry.getInstance().getPeerInfo(friend.getId());
                        if (peerInfo != null) {
                            friend.setStatus(UserStatus.ONLINE);
                            friend.setP2pAddress(peerInfo.getAddress());
                            friend.setP2pPort(peerInfo.getPort());
                            System.out.println("✅ Friend ONLINE: " + friend.getDisplayName());
                        } else {
                            friend.setStatus(UserStatus.OFFLINE);
                        }
                    }
                    friendListView.refresh();
                });

            } catch (Exception e) {
                System.err.println("❌ Error updating peers: " + e.getMessage());
            }
        });
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
            System.err.println("❌ Error loading chat: " + e.getMessage());
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

        executorService.submit(() -> {
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
                        showInfo("That's You!", "You cannot add yourself");
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
        });
    }

    @FXML
    private void handleAddFriendFromSearch() {
        if (searchedUser == null) return;

        addFriendButton.setDisable(true);

        executorService.submit(() -> {
            Long currentUserId = UserSession.getInstance().getCurrentUser().getId();
            boolean success = friendService.sendFriendRequest(currentUserId, searchedUser.getId());

            Platform.runLater(() -> {
                addFriendButton.setDisable(false);

                if (success) {
                    showInfo("Friend Added", searchedUser.getDisplayName() + " added!");

                    searchFriendField.clear();
                    searchResultBox.setVisible(false);
                    searchResultBox.setManaged(false);
                    searchedUser = null;

                    loadFriendRequests();
                } else {
                    showError("Failed", "Failed to add friend");
                }
            });
        });
    }

    private void startRequestAutoRefresh() {
        requestRefreshScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

        requestRefreshScheduler.scheduleAtFixedRate(() -> {
            Platform.runLater(this::loadFriendRequests);
        }, 0, 1, TimeUnit.SECONDS);
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
                System.out.println("🔧 Auto-added peer: " + senderId);
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
                .orElse(null);

        if (caller == null) {
            System.out.println("⚠️ Caller not in friends, fetching...");
            executorService.submit(() -> {
                try {
                    UserDTO fetched = RMIClient.getInstance()
                            .getUserService()
                            .getUserById(message.getSenderId());

                    if (fetched != null) {
                        Platform.runLater(() -> showCallDialog(message, fetched, callType));
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error fetching caller: " + e.getMessage());
                }
            });
            return;
        }

        showCallDialog(message, caller, callType);
    }

    private void showCallDialog(P2PMessage message, UserDTO caller, CallType callType) {
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

            Scene scene = new Scene(callView, 1000, 750);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

            callStage.setTitle("📞 Call - " + callSession.getPeer().getDisplayName());
            callStage.setScene(scene);
            callStage.setResizable(false);
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
        alert.setHeaderText("⚙️ Account Settings");

        ButtonType refreshButton = new ButtonType("🔄 Refresh");
        ButtonType profileButton = new ButtonType("👤 Profile");
        ButtonType logoutButton = new ButtonType("🚪 Logout");
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(refreshButton, profileButton, logoutButton, cancelButton);

        alert.showAndWait().ifPresent(response -> {
            if (response == logoutButton) {
                handleLogout();
            } else if (response == profileButton) {
                handlerProfile();
            } else if (response == refreshButton) {
                loadFriendList();
                loadFriendRequests();
                showInfo("Refreshed", "✅ Data refreshed!");
            }
        });
    }

    private void handlerProfile() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/profile_editor.fxml"));
            VBox profileView = loader.load();

            ProfileEditorController profileController = loader.getController();

            // Tạo Stage mới cho Profile Editor
            Stage profileStage = new Stage();
            profileController.setStage(profileStage);

            Scene scene = new Scene(profileView);

            // Load CSS nếu có
            try {
                scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            } catch (Exception e) {
                System.err.println("⚠️ CSS file not found, using default styles");
            }

            profileStage.setTitle("Edit Profile");
            profileStage.setScene(scene);
            profileStage.initModality(Modality.APPLICATION_MODAL);
            profileStage.setResizable(false);

            // Callback khi đóng profile editor để refresh UI
            profileStage.setOnHidden(e -> {
                // Refresh user info on main screen
                User updatedUser = UserSession.getInstance().getCurrentUser();
                if (updatedUser != null) {
                    usernameLabel.setText(updatedUser.getDisplayName());
                    statusLabel.setText(updatedUser.getStatus().toString());
                }
            });

            profileStage.show();

        } catch (Exception e) {
            System.err.println("❌ Error opening profile editor: " + e.getMessage());
            e.printStackTrace();
            showError("Error", "Failed to open profile editor: " + e.getMessage());
        }
    }



    private void handleLogout() {
        try {
            Long userId = UserSession.getInstance().getCurrentUser().getId();

            statusUpdateService.stopPolling();
            statusUpdateService.removeListener(this::handleStatusUpdate);

            // Async cleanup
//            executorService.submit(() -> {
//                try {
//                    RMIClient.getInstance()
//                            .getUserService()
//                            .updateStatus(userId, UserStatus.OFFLINE);
//
//                    RMIClient.getInstance()
//                            .getPeerDiscoveryService()
//                            .unregisterPeer(userId);
//
//                    System.out.println("✅ Logged out");
//                } catch (Exception e) {
//                    System.err.println("⚠️ Logout error: " + e.getMessage());
//                }
//            });
//
//            if (heartbeatTimer != null) {
//                heartbeatTimer.cancel();
//            }
//
//            if (p2pServer != null) {
//                p2pServer.stop();
//            }
//
//            if (executorService != null) {
//                executorService.shutdown();
//            }
//
//            PeerRegistry.getInstance().clear();
//            UserSession.getInstance().clear();
//
//            if (stage == null) {
//                stage = (Stage) usernameLabel.getScene().getWindow();
//            }
            handleAppExit();
            switchScene("/view/login.fxml", 1200, 800);

        } catch (Exception e) {
            System.err.println("❌ Logout error: " + e.getMessage());
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
    private void handleAppExit() {
        try {
            Long userId = UserSession.getInstance().getCurrentUser().getId();

            // Stop polling
            if (statusUpdateService != null) {
                statusUpdateService.stopPolling();
                statusUpdateService.removeListener(this::handleStatusUpdate);
            }

            // Stop heartbeat
            if (heartbeatTimer != null) {
                heartbeatTimer.cancel();
            }

            // Async OFFLINE update
            if (executorService != null) {
                executorService.submit(() -> {
                    try {
                        RMIClient.getInstance()
                                .getUserService()
                                .updateStatus(userId, UserStatus.OFFLINE);

                        RMIClient.getInstance()
                                .getPeerDiscoveryService()
                                .unregisterPeer(userId);

                        System.out.println("🔴 User set OFFLINE (app exit)");
                    } catch (Exception e) {
                        System.err.println("⚠️ Exit update failed: " + e.getMessage());
                    }
                });
            }

            if (p2pServer != null) {
                p2pServer.stop();
            }

            if (executorService != null) {
                executorService.shutdownNow();
            }

            PeerRegistry.getInstance().clear();
            UserSession.getInstance().clear();

        } catch (Exception e) {
            System.err.println("❌ handleAppExit error: " + e.getMessage());
        }
    }

}