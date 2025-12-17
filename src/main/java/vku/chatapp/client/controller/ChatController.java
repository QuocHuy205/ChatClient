// FILE: vku/chatapp/client/controller/ChatController.java
// ✅ ADD: Audio/Video call handlers

package vku.chatapp.client.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import vku.chatapp.client.model.CallSession;
import vku.chatapp.client.model.ChatSession;
import vku.chatapp.client.model.UserSession;
import vku.chatapp.client.p2p.P2PMessageHandler;
import vku.chatapp.client.p2p.P2PServer;
import vku.chatapp.client.service.FileTransferService;
import vku.chatapp.client.service.MessageService;
import vku.chatapp.client.rmi.RMIClient;
import vku.chatapp.common.dto.UserDTO;
import vku.chatapp.common.enums.CallType;
import vku.chatapp.common.enums.MessageStatus;
import vku.chatapp.common.enums.MessageType;
import vku.chatapp.common.model.Message;
import vku.chatapp.common.protocol.P2PMessage;
import vku.chatapp.common.protocol.P2PMessageType;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ChatController extends BaseController {
    @FXML private VBox messagesContainer;
    @FXML private ScrollPane messagesScrollPane;
    @FXML private TextArea messageInput;
    @FXML private Button sendButton;
    @FXML private Button attachButton;
    @FXML private Button emojiButton;
    @FXML private Button audioCallButton; // ✅ NEW
    @FXML private Button videoCallButton; // ✅ NEW
    @FXML private Label chatTitleLabel;
    @FXML private Label chatStatusLabel;

    private MessageService messageService;
    private FileTransferService fileTransferService;
    private P2PMessageHandler messageHandler;
    private Map<Long, ChatSession> chatSessions;
    private ChatSession currentChatSession;
    private DateTimeFormatter timeFormatter;
    private Set<String> displayedMessageIds;
    private boolean isSending = false;
    private P2PServer localP2PServer;

    @FXML
    public void initialize() {
        messageService = new MessageService();
        fileTransferService = new FileTransferService();
        chatSessions = new HashMap<>();
        timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        displayedMessageIds = new HashSet<>();

        setupMessageInput();
        setupMessageListener();

        messagesContainer.heightProperty().addListener((obs, oldVal, newVal) -> {
            messagesScrollPane.setVvalue(1.0);
        });
    }

    private void setupMessageInput() {
        messageInput.setOnKeyPressed(event -> {
            if (event.getCode().toString().equals("ENTER") && !event.isShiftDown()) {
                event.consume();
                handleSendMessage();
            }
        });

        messageInput.textProperty().addListener((obs, oldVal, newVal) -> {
            if (currentChatSession != null && !isSending) {
                boolean isTyping = newVal != null && !newVal.trim().isEmpty();
                messageService.sendTypingIndicator(
                        currentChatSession.getFriend().getId(),
                        isTyping
                );
            }
        });
    }

    private void setupMessageListener() {
        if (messageHandler != null) {
            messageHandler.addListener(this::handleIncomingMessage);
        }
    }

    public void setMessageHandler(P2PMessageHandler handler) {
        this.messageHandler = handler;
        setupMessageListener();
    }

    public void openChat(UserDTO friend) {
        Long friendId = friend.getId();

        if (!chatSessions.containsKey(friendId)) {
            chatSessions.put(friendId, new ChatSession(friend));
        }

        currentChatSession = chatSessions.get(friendId);

        chatTitleLabel.setText(friend.getDisplayName() != null ? friend.getDisplayName() : "Friend");

        if (friend.getStatus() != null) {
            chatStatusLabel.setText(friend.getStatus().toString());
        } else {
            chatStatusLabel.setText("Online");
        }

        // ✅ Enable call buttons
        if (audioCallButton != null) audioCallButton.setDisable(false);
        if (videoCallButton != null) videoCallButton.setDisable(false);

        loadMessages();
    }

    // ✅ NEW: Handle audio call
    @FXML
    private void handleAudioCall() {
        if (currentChatSession == null) {
            showError("No Chat Selected", "Please select a friend to call");
            return;
        }

        UserDTO friend = currentChatSession.getFriend();

        System.out.println("📞 Initiating audio call to: " + friend.getDisplayName());

        initiateCall(friend, CallType.AUDIO);
    }

    // ✅ NEW: Handle video call
    @FXML
    private void handleVideoCall() {
        if (currentChatSession == null) {
            showError("No Chat Selected", "Please select a friend to call");
            return;
        }

        UserDTO friend = currentChatSession.getFriend();

        System.out.println("🎥 Initiating video call to: " + friend.getDisplayName());

        initiateCall(friend, CallType.VIDEO);
    }

    private void initiateCall(UserDTO friend, CallType callType) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/video-call.fxml"));
            VBox callView = loader.load();

            VideoCallController callController = loader.getController();
            callController.setMessageHandler(messageHandler);

            Stage callStage = new Stage();
            callController.setStage(callStage);

            Scene scene = new Scene(callView, 1000, 750);

            try {
                scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            } catch (Exception e) {
                System.out.println("⚠️ CSS not found, using default styles");
            }

            callStage.setTitle("📞 " + (callType == CallType.VIDEO ? "Video" : "Audio") +
                    " Call - " + friend.getDisplayName());
            callStage.setScene(scene);
            callStage.setResizable(false); // ✅ Fix window size
            callStage.initModality(Modality.NONE);
            callStage.show();

            callController.initiateCall(friend, callType);

            System.out.println("✅ Call window opened");

        } catch (Exception e) {
            System.err.println("❌ Error opening call window: " + e.getMessage());
            e.printStackTrace();
            showError("Call Error", "Could not open call window: " + e.getMessage());
        }
    }

    private void loadMessages() {
        messagesContainer.getChildren().clear();
        displayedMessageIds.clear();

        if (currentChatSession == null) {
            return;
        }

        new Thread(() -> {
            try {
                Long currentUserId = UserSession.getInstance().getCurrentUser().getId();
                Long friendId = currentChatSession.getFriend().getId();

                List<Message> messages = RMIClient.getInstance()
                        .getMessageService()
                        .getConversationHistory(currentUserId, friendId, 100);

                Platform.runLater(() -> {
                    currentChatSession.getMessages().clear();

                    for (int i = messages.size() - 1; i >= 0; i--) {
                        Message msg = messages.get(i);
                        currentChatSession.addMessage(msg);
                        displayMessage(msg, false);
                    }

                    System.out.println("✅ Loaded " + messages.size() + " messages from database");
                });

            } catch (Exception e) {
                System.err.println("❌ Error loading messages: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    @FXML
    private void handleSendMessage() {
        if (isSending) {
            System.out.println("⚠️ Already sending message, ignoring...");
            return;
        }

        if (currentChatSession == null) {
            showError("No Chat Selected", "Please select a friend to chat with");
            return;
        }

        String content = messageInput.getText().trim();

        if (content.isEmpty()) {
            System.out.println("⚠️ Empty message, not sending");
            return;
        }

        Long senderId = UserSession.getInstance().getCurrentUser().getId();
        Long receiverId = currentChatSession.getFriend().getId();

        isSending = true;
        sendButton.setDisable(true);

        String messageToSend = content;
        messageInput.clear();

        new Thread(() -> {
            try {
                Message message = new Message();
                message.setSenderId(senderId);
                message.setReceiverId(receiverId);
                message.setContent(messageToSend);
                message.setType(MessageType.TEXT);
                message.setStatus(MessageStatus.SENDING);
                message.setSentAt(LocalDateTime.now());

                Message savedMessage = RMIClient.getInstance()
                        .getMessageService()
                        .saveMessage(message);

                System.out.println("✅ Message saved to DB with ID: " + savedMessage.getId());

                boolean p2pSuccess = messageService.sendTextMessage(receiverId, messageToSend);

                if (p2pSuccess) {
                    savedMessage.setStatus(MessageStatus.SENT);
                    RMIClient.getInstance()
                            .getMessageService()
                            .updateMessageStatus(savedMessage.getId(), MessageStatus.SENT.name());
                } else {
                    savedMessage.setStatus(MessageStatus.FAILED);
                    RMIClient.getInstance()
                            .getMessageService()
                            .updateMessageStatus(savedMessage.getId(), MessageStatus.FAILED.name());
                }

                Platform.runLater(() -> {
                    currentChatSession.addMessage(savedMessage);
                    displayMessage(savedMessage, false);

                    if (!p2pSuccess) {
                        showError("Send Failed", "Could not send message. User may be offline.\nMessage saved to database.");
                    }

                    isSending = false;
                    sendButton.setDisable(false);

                    System.out.println("✅ Message processed successfully");
                });

            } catch (Exception e) {
                System.err.println("❌ Error sending message: " + e.getMessage());
                e.printStackTrace();

                Platform.runLater(() -> {
                    showError("Send Failed", "Error: " + e.getMessage());
                    messageInput.setText(messageToSend);
                    isSending = false;
                    sendButton.setDisable(false);
                });
            }
        }).start();
    }

    @FXML
    private void handleAttachFile() {
        if (currentChatSession == null) {
            showError("No Chat Selected", "Please select a friend to send file to");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Send");
        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            sendFile(file);
        }
    }

    private void sendFile(File file) {
        Long senderId = UserSession.getInstance().getCurrentUser().getId();
        Long receiverId = currentChatSession.getFriend().getId();

        new Thread(() -> {
            try {
                Message message = new Message();
                message.setSenderId(senderId);
                message.setReceiverId(receiverId);
                message.setFileName(file.getName());
                message.setFileSize(file.length());
                message.setType(MessageType.FILE);
                message.setStatus(MessageStatus.SENDING);
                message.setSentAt(LocalDateTime.now());
                message.setContent("📎 " + file.getName());

                Message savedMessage = RMIClient.getInstance()
                        .getMessageService()
                        .saveMessage(message);

                System.out.println("✅ File message saved to DB with ID: " + savedMessage.getId());

                boolean success = fileTransferService.sendFile(receiverId, file);

                if (success) {
                    savedMessage.setStatus(MessageStatus.SENT);
                    RMIClient.getInstance()
                            .getMessageService()
                            .updateMessageStatus(savedMessage.getId(), MessageStatus.SENT.name());
                } else {
                    savedMessage.setStatus(MessageStatus.FAILED);
                    RMIClient.getInstance()
                            .getMessageService()
                            .updateMessageStatus(savedMessage.getId(), MessageStatus.FAILED.name());
                }

                Platform.runLater(() -> {
                    currentChatSession.addMessage(savedMessage);
                    displayMessage(savedMessage, false);

                    if (success) {
                        showInfo("File Sent", "File sent successfully: " + file.getName());
                    } else {
                        showError("Send Failed", "Could not send file. Message saved to database.");
                    }
                });

            } catch (Exception e) {
                System.err.println("❌ Error sending file: " + e.getMessage());
                e.printStackTrace();

                Platform.runLater(() -> {
                    showError("Send Failed", "Error sending file: " + e.getMessage());
                });
            }
        }).start();
    }

    @FXML
    private void handleEmoji() {
        showInfo("Emoji", "Emoji picker coming soon!");
    }

    private void handleIncomingMessage(P2PMessage p2pMessage) {
        if (p2pMessage.getType() == P2PMessageType.TEXT_MESSAGE) {
            handleIncomingTextMessage(p2pMessage);
        } else if (p2pMessage.getType() == P2PMessageType.FILE_TRANSFER) {
            handleIncomingFileTransfer(p2pMessage);
        }
    }

    private void handleIncomingTextMessage(P2PMessage p2pMessage) {
        Platform.runLater(() -> {
            Long senderId = p2pMessage.getSenderId();

            if (p2pMessage.getContent() == null || p2pMessage.getContent().trim().isEmpty()) {
                System.out.println("⚠️ Received empty message, ignoring");
                return;
            }

            String messageId = p2pMessage.getMessageId();
            if (displayedMessageIds.contains(messageId)) {
                System.out.println("⚠️ Duplicate message received, ignoring: " + messageId);
                return;
            }

            if (!chatSessions.containsKey(senderId)) {
                System.out.println("⚠️ No chat session for sender: " + senderId + ", creating one...");

                new Thread(() -> {
                    try {
                        UserDTO sender = RMIClient.getInstance().getUserService().getUserById(senderId);

                        if (sender != null) {
                            Platform.runLater(() -> {
                                chatSessions.put(senderId, new ChatSession(sender));
                                System.out.println("✅ Created chat session for: " + sender.getDisplayName());
                                processIncomingTextMessage(p2pMessage, senderId);
                            });
                        } else {
                            System.err.println("❌ Could not find user with ID: " + senderId);
                        }
                    } catch (Exception e) {
                        System.err.println("❌ Error getting user info: " + e.getMessage());
                    }
                }).start();

                return;
            }

            processIncomingTextMessage(p2pMessage, senderId);
        });
    }

    private void processIncomingTextMessage(P2PMessage p2pMessage, Long senderId) {
        ChatSession session = chatSessions.get(senderId);
        if (session == null) {
            System.err.println("❌ Still no chat session for sender: " + senderId);
            return;
        }

        new Thread(() -> {
            try {
                Message message = new Message();
                message.setSenderId(senderId);
                message.setReceiverId(UserSession.getInstance().getCurrentUser().getId());
                message.setContent(p2pMessage.getContent());
                message.setType(p2pMessage.getContentType());
                message.setStatus(MessageStatus.DELIVERED);
                message.setSentAt(LocalDateTime.now());

                Message savedMessage = RMIClient.getInstance()
                        .getMessageService()
                        .saveMessage(message);

                Platform.runLater(() -> {
                    session.addMessage(savedMessage);

                    if (currentChatSession != null &&
                            currentChatSession.getFriend().getId().equals(senderId)) {
                        displayMessage(savedMessage, false);

                        messageService.sendReadReceipt(senderId, p2pMessage.getMessageId());

                        new Thread(() -> {
                            try {
                                RMIClient.getInstance()
                                        .getMessageService()
                                        .updateMessageStatus(savedMessage.getId(), MessageStatus.READ.name());
                            } catch (Exception e) {
                                System.err.println("❌ Error updating to READ: " + e.getMessage());
                            }
                        }).start();
                    }

                    System.out.println("✅ Incoming message saved and displayed");
                });

            } catch (Exception e) {
                System.err.println("❌ Error saving incoming message: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private void handleIncomingFileTransfer(P2PMessage p2pMessage) {
        Platform.runLater(() -> {
            Long senderId = p2pMessage.getSenderId();

            if (!chatSessions.containsKey(senderId)) {
                System.out.println("⚠️ No chat session for file sender: " + senderId);
                return;
            }

            ChatSession session = chatSessions.get(senderId);
            fileTransferService.receiveFile(p2pMessage);

            new Thread(() -> {
                try {
                    Message message = new Message();
                    message.setSenderId(senderId);
                    message.setReceiverId(UserSession.getInstance().getCurrentUser().getId());
                    message.setFileName(p2pMessage.getFileName());
                    message.setFileSize((long) p2pMessage.getFileData().length);
                    message.setType(MessageType.FILE);
                    message.setStatus(MessageStatus.DELIVERED);
                    message.setSentAt(LocalDateTime.now());
                    message.setContent("📎 " + p2pMessage.getFileName());

                    Message savedMessage = RMIClient.getInstance()
                            .getMessageService()
                            .saveMessage(message);

                    Platform.runLater(() -> {
                        session.addMessage(savedMessage);

                        if (currentChatSession != null &&
                                currentChatSession.getFriend().getId().equals(senderId)) {
                            displayMessage(savedMessage, false);
                        }

                        String downloadPath = System.getProperty("user.home") + "/Downloads/VKUChat/";
                        showInfo("File Received",
                                "File received: " + p2pMessage.getFileName() +
                                        "\nSaved to: " + downloadPath);

                        System.out.println("✅ File transfer saved and displayed");
                    });

                } catch (Exception e) {
                    System.err.println("❌ Error saving file transfer: " + e.getMessage());
                }
            }).start();
        });
    }

    private void displayMessage(Message message, boolean addToSession) {
        if (message.getContent() == null || message.getContent().trim().isEmpty()) {
            if (message.getType() != MessageType.FILE) {
                System.out.println("⚠️ Skipping empty message display");
                return;
            }
        }

        String messageId = message.getSenderId() + "_" +
                message.getReceiverId() + "_" +
                (message.getId() != null ? message.getId() : message.getContent()) + "_" +
                message.getSentAt();

        if (displayedMessageIds.contains(messageId)) {
            System.out.println("⚠️ Message already displayed, skipping");
            return;
        }

        displayedMessageIds.add(messageId);

        boolean isSent = message.getSenderId().equals(
                UserSession.getInstance().getCurrentUser().getId()
        );

        HBox messageBox = new HBox(10);
        messageBox.setPadding(new Insets(5, 10, 5, 10));
        messageBox.setAlignment(isSent ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox messageContent = new VBox(5);
        messageContent.setMaxWidth(400);

        VBox bubble = new VBox(5);
        bubble.setPadding(new Insets(10, 15, 10, 15));
        bubble.setStyle(
                "-fx-background-color: " + (isSent ? "#0078d4" : "#f3f3f3") + ";" +
                        "-fx-background-radius: 18px;"
        );

        if (message.getType() == MessageType.TEXT) {
            Label contentLabel = new Label(message.getContent());
            contentLabel.setWrapText(true);
            contentLabel.setMaxWidth(370);
            contentLabel.setStyle(
                    "-fx-text-fill: " + (isSent ? "white" : "#323130") + ";" +
                            "-fx-font-size: 14px;"
            );
            bubble.getChildren().add(contentLabel);

        } else if (message.getType() == MessageType.FILE) {
            Label fileLabel = new Label("📎 " + message.getFileName());
            fileLabel.setStyle(
                    "-fx-text-fill: " + (isSent ? "white" : "#323130") + ";" +
                            "-fx-font-size: 14px;" +
                            "-fx-font-weight: bold;"
            );

            if (message.getFileSize() != null) {
                Label sizeLabel = new Label(formatFileSize(message.getFileSize()));
                sizeLabel.setStyle(
                        "-fx-text-fill: " + (isSent ? "rgba(255,255,255,0.8)" : "#605e5c") + ";" +
                                "-fx-font-size: 11px;"
                );
                bubble.getChildren().addAll(fileLabel, sizeLabel);
            } else {
                bubble.getChildren().add(fileLabel);
            }
        }

        HBox statusBox = new HBox(5);
        statusBox.setAlignment(Pos.CENTER_RIGHT);

        if (message.getSentAt() != null) {
            Label timeLabel = new Label(message.getSentAt().format(timeFormatter));
            timeLabel.setStyle(
                    "-fx-text-fill: " + (isSent ? "rgba(255,255,255,0.7)" : "#605e5c") + ";" +
                            "-fx-font-size: 11px;"
            );
            statusBox.getChildren().add(timeLabel);
        }

        if (isSent && message.getStatus() != null) {
            String statusIcon = switch (message.getStatus()) {
                case SENDING -> "⏳";
                case SENT -> "✓";
                case DELIVERED -> "✓✓";
                case READ -> "✓✓";
                case FAILED -> "❌";
            };

            Label statusLabel = new Label(statusIcon);
            statusLabel.setStyle(
                    "-fx-text-fill: " + (isSent ? "rgba(255,255,255,0.7)" : "#605e5c") + ";" +
                            "-fx-font-size: 11px;"
            );
            statusBox.getChildren().add(statusLabel);
        }

        bubble.getChildren().add(statusBox);

        messageContent.getChildren().add(bubble);
        messageBox.getChildren().add(messageContent);

        messagesContainer.getChildren().add(messageBox);
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    public void setLocalP2PServer(P2PServer server) {
        this.localP2PServer = server;

        // Update MessageService
        if (messageService != null) {
            messageService.setLocalP2PServer(server);
        }

        // Update FileTransferService if needed
        if (fileTransferService != null) {
            // fileTransferService.setLocalP2PServer(server);
        }
    }
}