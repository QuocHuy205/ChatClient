package vku.chatapp.client.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import vku.chatapp.client.model.ChatSession;
import vku.chatapp.client.model.UserSession;
import vku.chatapp.client.p2p.P2PMessageHandler;
import vku.chatapp.client.service.FileTransferService;
import vku.chatapp.client.service.MessageService;
import vku.chatapp.common.dto.UserDTO;
import vku.chatapp.common.enums.MessageType;
import vku.chatapp.common.enums.UserStatus;
import vku.chatapp.common.model.Message;
import vku.chatapp.common.protocol.P2PMessage;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class ChatController extends BaseController {
    @FXML private VBox messagesContainer;
    @FXML private ScrollPane messagesScrollPane;
    @FXML private TextArea messageInput;
    @FXML private Button sendButton;
    @FXML private Button attachButton;
    @FXML private Button emojiButton;
    @FXML private Label chatTitleLabel;
    @FXML private Label chatStatusLabel;

    private MessageService messageService;
    private FileTransferService fileTransferService;
    private P2PMessageHandler messageHandler;
    private Map<Long, ChatSession> chatSessions;
    private ChatSession currentChatSession;
    private DateTimeFormatter timeFormatter;

    @FXML
    public void initialize() {
        messageService = new MessageService();
        fileTransferService = new FileTransferService();
        chatSessions = new HashMap<>();
        timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        setupMessageInput();
        setupMessageListener();

        // Auto-scroll to bottom when new messages arrive
        messagesContainer.heightProperty().addListener((obs, oldVal, newVal) -> {
            messagesScrollPane.setVvalue(1.0);
        });
    }

    private void setupMessageInput() {
        // Send on Ctrl+Enter
        messageInput.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode().toString().equals("ENTER")) {
                handleSendMessage();
            }
        });

        // Typing indicator
        messageInput.textProperty().addListener((obs, oldVal, newVal) -> {
            if (currentChatSession != null) {
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

        // Get or create chat session
        if (!chatSessions.containsKey(friendId)) {
            chatSessions.put(friendId, new ChatSession(friend));
        }

        currentChatSession = chatSessions.get(friendId);

        // Update UI
        chatTitleLabel.setText(friend.getDisplayName() != null ? friend.getDisplayName() : "Friend");

        // Handle null status safely
        if (friend.getStatus() != null) {
            chatStatusLabel.setText(friend.getStatus().toString());
        } else {
            chatStatusLabel.setText("Online"); // Default
        }

        // Load messages
        loadMessages();
    }

    private void loadMessages() {
        messagesContainer.getChildren().clear();

        if (currentChatSession == null) {
            return;
        }

        for (Message msg : currentChatSession.getMessages()) {
            displayMessage(msg);
        }
    }

    @FXML
    private void handleSendMessage() {
        if (currentChatSession == null) {
            showError("No Chat Selected", "Please select a friend to chat with");
            return;
        }

        String content = messageInput.getText().trim();
        if (content.isEmpty()) {
            return;
        }

        Long receiverId = currentChatSession.getFriend().getId();

        // Send via P2P
        new Thread(() -> {
            boolean success = messageService.sendTextMessage(receiverId, content);

            Platform.runLater(() -> {
                if (success) {
                    // Create message object
                    Message message = new Message();
                    message.setSenderId(UserSession.getInstance().getCurrentUser().getId());
                    message.setReceiverId(receiverId);
                    message.setContent(content);
                    message.setType(MessageType.TEXT);
                    message.setSentAt(LocalDateTime.now());

                    // Add to session
                    currentChatSession.addMessage(message);

                    // Display in UI
                    displayMessage(message);

                    // Clear input
                    messageInput.clear();
                } else {
                    showError("Send Failed", "Could not send message. User may be offline.");
                }
            });
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
        Long receiverId = currentChatSession.getFriend().getId();

        new Thread(() -> {
            boolean success = fileTransferService.sendFile(receiverId, file);

            Platform.runLater(() -> {
                if (success) {
                    Message message = new Message();
                    message.setSenderId(UserSession.getInstance().getCurrentUser().getId());
                    message.setReceiverId(receiverId);
                    message.setFileName(file.getName());
                    message.setType(MessageType.FILE);
                    message.setSentAt(LocalDateTime.now());

                    currentChatSession.addMessage(message);
                    displayMessage(message);

                    showInfo("File Sent", "File sent successfully: " + file.getName());
                } else {
                    showError("Send Failed", "Could not send file");
                }
            });
        }).start();
    }

    @FXML
    private void handleEmoji() {
        showInfo("Emoji", "Emoji picker coming soon!");
    }

    private void handleIncomingMessage(P2PMessage p2pMessage) {
        Platform.runLater(() -> {
            Long senderId = p2pMessage.getSenderId();

            // Get or create chat session
            if (!chatSessions.containsKey(senderId)) {
                return;
            }

            ChatSession session = chatSessions.get(senderId);

            // Create message
            Message message = new Message();
            message.setSenderId(senderId);
            message.setReceiverId(UserSession.getInstance().getCurrentUser().getId());
            message.setContent(p2pMessage.getContent());
            message.setType(p2pMessage.getContentType());
            message.setSentAt(LocalDateTime.now());

            session.addMessage(message);

            // Display if this is current chat
            if (currentChatSession != null &&
                    currentChatSession.getFriend().getId().equals(senderId)) {
                displayMessage(message);

                // Send read receipt
                messageService.sendReadReceipt(senderId, p2pMessage.getMessageId());
            }
        });
    }

    private void displayMessage(Message message) {
        boolean isSent = message.getSenderId().equals(
                UserSession.getInstance().getCurrentUser().getId()
        );

        HBox messageBox = new HBox(10);
        messageBox.setPadding(new Insets(5, 10, 5, 10));
        messageBox.setAlignment(isSent ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox messageContent = new VBox(5);
        messageContent.setMaxWidth(400);

        // Message bubble
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
            bubble.getChildren().add(fileLabel);
        }

        // Timestamp
        if (message.getSentAt() != null) {
            Label timeLabel = new Label(message.getSentAt().format(timeFormatter));
            timeLabel.setStyle(
                    "-fx-text-fill: " + (isSent ? "rgba(255,255,255,0.7)" : "#605e5c") + ";" +
                            "-fx-font-size: 11px;"
            );
            bubble.getChildren().add(timeLabel);
        }

        messageContent.getChildren().add(bubble);
        messageBox.getChildren().add(messageContent);

        messagesContainer.getChildren().add(messageBox);
    }
}