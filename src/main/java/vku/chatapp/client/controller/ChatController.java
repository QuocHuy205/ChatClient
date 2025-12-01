// FILE: vku/chatapp/client/controller/ChatController.java
// ✅ FIX: Ngăn duplicate messages và empty messages

package vku.chatapp.client.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import vku.chatapp.client.model.ChatSession;
import vku.chatapp.client.model.UserSession;
import vku.chatapp.client.p2p.P2PMessageHandler;
import vku.chatapp.client.service.FileTransferService;
import vku.chatapp.client.service.MessageService;
import vku.chatapp.common.dto.UserDTO;
import vku.chatapp.common.enums.MessageType;
import vku.chatapp.common.model.Message;
import vku.chatapp.common.protocol.P2PMessage;
import vku.chatapp.common.protocol.P2PMessageType;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

    // ✅ FIX: Track displayed messages to prevent duplicates
    private Set<String> displayedMessageIds;
    private boolean isSending = false; // ✅ FIX: Prevent multiple sends

    @FXML
    public void initialize() {
        messageService = new MessageService();
        fileTransferService = new FileTransferService();
        chatSessions = new HashMap<>();
        timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        displayedMessageIds = new HashSet<>(); // ✅ NEW

        setupMessageInput();
        setupMessageListener();

        // Auto-scroll to bottom when new messages arrive
        messagesContainer.heightProperty().addListener((obs, oldVal, newVal) -> {
            messagesScrollPane.setVvalue(1.0);
        });
    }

    private void setupMessageInput() {
        // ✅ FIX: Send on Enter (not Ctrl+Enter)
        messageInput.setOnKeyPressed(event -> {
            if (event.getCode().toString().equals("ENTER") && !event.isShiftDown()) {
                event.consume(); // Prevent newline
                handleSendMessage();
            }
        });

        // Typing indicator
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

        // Get or create chat session
        if (!chatSessions.containsKey(friendId)) {
            chatSessions.put(friendId, new ChatSession(friend));
        }

        currentChatSession = chatSessions.get(friendId);

        // Update UI
        chatTitleLabel.setText(friend.getDisplayName() != null ? friend.getDisplayName() : "Friend");

        if (friend.getStatus() != null) {
            chatStatusLabel.setText(friend.getStatus().toString());
        } else {
            chatStatusLabel.setText("Online");
        }

        // Load messages
        loadMessages();
    }

    private void loadMessages() {
        messagesContainer.getChildren().clear();
        displayedMessageIds.clear(); // ✅ FIX: Clear tracked IDs

        if (currentChatSession == null) {
            return;
        }

        for (Message msg : currentChatSession.getMessages()) {
            displayMessage(msg, false); // false = don't add to session again
        }
    }

    @FXML
    private void handleSendMessage() {
        // ✅ FIX: Prevent multiple sends
        if (isSending) {
            System.out.println("⚠️ Already sending message, ignoring...");
            return;
        }

        if (currentChatSession == null) {
            showError("No Chat Selected", "Please select a friend to chat with");
            return;
        }

        String content = messageInput.getText().trim();

        // ✅ FIX: Validate content is not empty
        if (content.isEmpty()) {
            System.out.println("⚠️ Empty message, not sending");
            return;
        }

        Long receiverId = currentChatSession.getFriend().getId();

        // ✅ FIX: Set sending flag
        isSending = true;
        sendButton.setDisable(true);

        // ✅ FIX: Clear input immediately to prevent accidental re-sends
        String messageToSend = content;
        messageInput.clear();

        // Send via P2P
        new Thread(() -> {
            try {
                System.out.println("📤 Sending message: " + messageToSend);
                boolean success = messageService.sendTextMessage(receiverId, messageToSend);

                Platform.runLater(() -> {
                    if (success) {
                        // Create message object
                        Message message = new Message();
                        message.setSenderId(UserSession.getInstance().getCurrentUser().getId());
                        message.setReceiverId(receiverId);
                        message.setContent(messageToSend);
                        message.setType(MessageType.TEXT);
                        message.setSentAt(LocalDateTime.now());

                        // Add to session
                        currentChatSession.addMessage(message);

                        // Display in UI
                        displayMessage(message, false); // false = already added to session

                        System.out.println("✅ Message sent and displayed");
                    } else {
                        showError("Send Failed", "Could not send message. User may be offline.");
                        // Restore message to input if failed
                        messageInput.setText(messageToSend);
                    }

                    // ✅ FIX: Reset sending flag
                    isSending = false;
                    sendButton.setDisable(false);
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
                    displayMessage(message, false);

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
        // ✅ FIX: Ignore non-text messages here (they're handled elsewhere)
        if (p2pMessage.getType() != P2PMessageType.TEXT_MESSAGE) {
            return;
        }

        Platform.runLater(() -> {
            Long senderId = p2pMessage.getSenderId();

            // ✅ FIX: Validate message has content
            if (p2pMessage.getContent() == null || p2pMessage.getContent().trim().isEmpty()) {
                System.out.println("⚠️ Received empty message, ignoring");
                return;
            }

            // ✅ FIX: Check for duplicate
            String messageId = p2pMessage.getMessageId();
            if (displayedMessageIds.contains(messageId)) {
                System.out.println("⚠️ Duplicate message received, ignoring: " + messageId);
                return;
            }

            // Get or create chat session
            if (!chatSessions.containsKey(senderId)) {
                System.out.println("⚠️ No chat session for sender: " + senderId);
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
                displayMessage(message, false);

                // Send read receipt
                messageService.sendReadReceipt(senderId, p2pMessage.getMessageId());

                System.out.println("✅ Incoming message displayed");
            }
        });
    }

    // ✅ FIX: Add parameter to control whether to add to session
    private void displayMessage(Message message, boolean addToSession) {
        if (message.getContent() == null || message.getContent().trim().isEmpty()) {
            System.out.println("⚠️ Skipping empty message display");
            return;
        }

        // ✅ FIX: Generate unique ID for tracking
        String messageId = message.getSenderId() + "_" +
                message.getReceiverId() + "_" +
                message.getContent() + "_" +
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

    // Overload for backward compatibility
    private void displayMessage(Message message) {
        displayMessage(message, true);
    }
}