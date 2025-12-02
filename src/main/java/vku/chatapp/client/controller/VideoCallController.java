// FILE: vku/chatapp/client/controller/VideoCallController.java
// ✅ FIX: Infinite loop CALL_END + Update UI labels

package vku.chatapp.client.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import vku.chatapp.client.media.MediaManager;
import vku.chatapp.client.model.CallSession;
import vku.chatapp.client.model.UserSession;
import vku.chatapp.client.p2p.P2PMessageHandler;
import vku.chatapp.client.service.CallService;
import vku.chatapp.common.dto.UserDTO;
import vku.chatapp.common.enums.CallStatus;
import vku.chatapp.common.enums.CallType;
import vku.chatapp.common.protocol.P2PMessage;
import vku.chatapp.common.protocol.P2PMessageType;

import java.time.LocalDateTime;
import java.util.UUID;

public class VideoCallController extends BaseController {
    @FXML private StackPane callContainer;
    @FXML private ImageView localVideoView;
    @FXML private ImageView remoteVideoView;
    @FXML private Label peerNameLabel;
    @FXML private Label callStatusLabel;
    @FXML private Label callDurationLabel;
    @FXML private Label remoteVideoPlaceholder; // ✅ NEW
    @FXML private Button muteButton;
    @FXML private Button videoToggleButton;
    @FXML private Button endCallButton;
    @FXML private Button switchCameraButton;
    @FXML private Label muteLabel; // ✅ NEW
    @FXML private Label videoLabel; // ✅ NEW

    private CallService callService;
    private MediaManager mediaManager;
    private P2PMessageHandler messageHandler;
    private CallSession currentCall;
    private Timeline durationTimer;

    private Long callHistoryId;
    private LocalDateTime callStartTime;

    private boolean isMuted = false;
    private boolean isVideoEnabled = true;
    private boolean isCallEnded = false; // ✅ NEW: Flag để prevent double end

    @FXML
    public void initialize() {
        callService = new CallService();
        mediaManager = MediaManager.getInstance();

        setupCallDurationTimer();
        setupMessageHandler();

        // ✅ Set initial button states
        updateMuteButton();
        updateVideoButton();
    }

    private void setupCallDurationTimer() {
        durationTimer = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            if (currentCall != null && currentCall.getStatus() == CallStatus.CONNECTED) {
                long duration = currentCall.getDuration();
                callDurationLabel.setText(formatDuration(duration));
            }
        }));
        durationTimer.setCycleCount(Timeline.INDEFINITE);
    }

    private void setupMessageHandler() {
        if (messageHandler != null) {
            messageHandler.addListener(this::handleCallMessage);
        }
    }

    public void setMessageHandler(P2PMessageHandler handler) {
        this.messageHandler = handler;
        setupMessageHandler();
    }

    public void initiateCall(UserDTO peer, CallType callType) {
        String callId = UUID.randomUUID().toString();
        boolean isCaller = true;

        currentCall = new CallSession(callId, peer, callType, isCaller);

        peerNameLabel.setText(peer.getDisplayName());
        callStatusLabel.setText("Calling...");

        if (remoteVideoPlaceholder != null) {
            remoteVideoPlaceholder.setText("Calling " + peer.getDisplayName() + "...");
        }

        boolean sent = callService.initiateCall(peer.getId(), callType);

        if (sent) {
            currentCall.setStatus(CallStatus.RINGING);

            Timeline timeout = new Timeline(new KeyFrame(Duration.seconds(30), e -> {
                if (currentCall != null && currentCall.getStatus() == CallStatus.RINGING) {
                    handleCallTimeout();
                }
            }));
            timeout.play();

            System.out.println("✅ Call initiated: " + callType + " to " + peer.getDisplayName());
        } else {
            showError("Call Failed", "Could not initiate call. User may be offline.");
            updateCallStatus(CallStatus.FAILED);
            handleEndCall();
        }
    }

    public void receiveCall(CallSession incomingCall) {
        this.currentCall = incomingCall;

        peerNameLabel.setText(incomingCall.getPeer().getDisplayName());
        callStatusLabel.setText("Incoming " +
                (incomingCall.getCallType() == CallType.VIDEO ? "Video" : "Audio") +
                " Call");

        if (remoteVideoPlaceholder != null) {
            remoteVideoPlaceholder.setText("Incoming call from " +
                    incomingCall.getPeer().getDisplayName());
        }

        showIncomingCallUI();
    }

    private void updateCallStatus(CallStatus status) {
        if (currentCall != null) {
            currentCall.setStatus(status);
        }
    }

    private void showIncomingCallUI() {
        Platform.runLater(() -> {
            callStatusLabel.setText("Auto-accepting call...");
            new Timeline(new KeyFrame(Duration.seconds(1), e -> handleAcceptCall())).play();
        });
    }

    private void handleAcceptCall() {
        if (currentCall == null) return;

        System.out.println("✅ Accepting call from " + currentCall.getPeer().getDisplayName());

        callService.answerCall(currentCall.getPeer().getId(), currentCall.getCallId());

        startMediaStreams();
    }

    private void startMediaStreams() {
        callStartTime = LocalDateTime.now();
        currentCall.start();
        callStatusLabel.setText("Connected");

        if (remoteVideoPlaceholder != null) {
            remoteVideoPlaceholder.setText("Connected to " + currentCall.getPeer().getDisplayName());
        }

        durationTimer.play();

        updateCallStatus(CallStatus.CONNECTED);

        try {
            if (currentCall.getCallType() == CallType.VIDEO) {
                mediaManager.startCall(currentCall, localVideoView, remoteVideoView);
                System.out.println("✅ Video call started (placeholder mode)");
            } else {
                mediaManager.startCall(currentCall, null, null);
                System.out.println("✅ Audio call started (placeholder mode)");
            }
        } catch (Exception e) {
            System.err.println("❌ Error starting media: " + e.getMessage());
            callStatusLabel.setText("Media Error");
        }
    }

    @FXML
    private void handleEndCall() {
        // ✅ FIX: Prevent double call to endCall
        if (isCallEnded) {
            System.out.println("⚠️ Call already ended, ignoring duplicate end request");
            return;
        }

        isCallEnded = true;

        if (currentCall == null) {
            closeWindow();
            return;
        }

        System.out.println("📞 Ending call with " + currentCall.getPeer().getDisplayName());

        int durationSeconds = 0;
        if (callStartTime != null) {
            durationSeconds = (int) java.time.Duration.between(
                    callStartTime,
                    LocalDateTime.now()
            ).getSeconds();
        }

        // ✅ FIX: Only send CALL_END if we haven't received one
        if (currentCall.getStatus() != CallStatus.ENDED) {
            callService.endCall(currentCall.getPeer().getId(), currentCall.getCallId());
            System.out.println("📤 Sent CALL_END message");
        } else {
            System.out.println("⚠️ Call already ended by peer, not sending CALL_END");
        }

        try {
            mediaManager.endCall();
        } catch (Exception e) {
            System.err.println("⚠️ Error ending media: " + e.getMessage());
        }

        if (durationTimer != null) {
            durationTimer.stop();
        }

        currentCall.end();
        updateCallStatus(CallStatus.ENDED);

        System.out.println("📞 Call ended. Duration: " + durationSeconds + " seconds");

        closeWindow();
    }

    @FXML
    private void handleMute() {
        isMuted = !isMuted;
        updateMuteButton();

        try {
            mediaManager.setMuted(isMuted);
            System.out.println((isMuted ? "🔇" : "🔊") + " Microphone " + (isMuted ? "muted" : "unmuted"));
        } catch (Exception e) {
            System.err.println("❌ Error toggling mute: " + e.getMessage());
        }
    }

    private void updateMuteButton() {
        if (isMuted) {
            muteButton.setStyle("-fx-background-color: #d13438; -fx-background-radius: 30px; -fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold;");
            if (muteLabel != null) muteLabel.setText("Unmute");
        } else {
            muteButton.setStyle("-fx-background-color: #4a4a4a; -fx-background-radius: 30px; -fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold;");
            if (muteLabel != null) muteLabel.setText("Mute");
        }
    }

    @FXML
    private void handleVideoToggle() {
        if (currentCall == null || currentCall.getCallType() != CallType.VIDEO) {
            return;
        }

        isVideoEnabled = !isVideoEnabled;
        updateVideoButton();

        try {
            mediaManager.setVideoEnabled(isVideoEnabled);
            System.out.println((isVideoEnabled ? "🎥" : "🎥❌") + " Video " + (isVideoEnabled ? "enabled" : "disabled"));
        } catch (Exception e) {
            System.err.println("❌ Error toggling video: " + e.getMessage());
        }
    }

    private void updateVideoButton() {
        if (isVideoEnabled) {
            videoToggleButton.setStyle("-fx-background-color: #4a4a4a; -fx-background-radius: 30px; -fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold;");
            if (videoLabel != null) videoLabel.setText("Video On");
        } else {
            videoToggleButton.setStyle("-fx-background-color: #d13438; -fx-background-radius: 30px; -fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold;");
            if (videoLabel != null) videoLabel.setText("Video Off");
        }
    }

    @FXML
    private void handleSwitchCamera() {
        try {
            mediaManager.switchCamera();
            System.out.println("🔄 Camera switched");
        } catch (Exception e) {
            System.err.println("❌ Error switching camera: " + e.getMessage());
        }
    }

    private void handleCallMessage(P2PMessage message) {
        // ✅ FIX: Ignore messages if call already ended
        if (isCallEnded) {
            System.out.println("⚠️ Ignoring message, call already ended: " + message.getType());
            return;
        }

        if (currentCall == null) return;

        if (!message.getSenderId().equals(currentCall.getPeer().getId())) {
            return;
        }

        Platform.runLater(() -> {
            switch (message.getType()) {
                case CALL_OFFER:
                    handleCallOffer(message);
                    break;
                case CALL_ANSWER:
                    handleCallAnswer(message);
                    break;
                case CALL_REJECT:
                    handleCallReject(message);
                    break;
                case CALL_END:
                    handleCallEnd(message);
                    break;
                case AUDIO_STREAM:
                    mediaManager.handleIncomingAudio(message);
                    break;
                case VIDEO_STREAM:
                    mediaManager.handleIncomingVideo(message);
                    break;
            }
        });
    }

    private void handleCallOffer(P2PMessage message) {
        // Handled by main controller
    }

    private void handleCallAnswer(P2PMessage message) {
        if (currentCall == null || !currentCall.isCaller()) {
            return;
        }

        System.out.println("✅ Call answered by " + currentCall.getPeer().getDisplayName());
        startMediaStreams();
    }

    private void handleCallReject(P2PMessage message) {
        System.out.println("❌ Call rejected by " + currentCall.getPeer().getDisplayName());

        callStatusLabel.setText("Call Rejected");
        updateCallStatus(CallStatus.REJECTED);

        Platform.runLater(() -> {
            showInfo("Call Rejected", "The call was rejected");
            new Timeline(new KeyFrame(Duration.seconds(2), e -> handleEndCall())).play();
        });
    }

    private void handleCallEnd(P2PMessage message) {
        // ✅ FIX: Set status to ENDED to prevent sending another CALL_END
        if (currentCall != null) {
            currentCall.setStatus(CallStatus.ENDED);
        }

        System.out.println("📞 Call ended by " +
                (currentCall != null ? currentCall.getPeer().getDisplayName() : "peer"));

        callStatusLabel.setText("Call Ended");
        handleEndCall();
    }

    private void handleCallTimeout() {
        System.out.println("⏰ Call timeout - no answer");

        callStatusLabel.setText("No Answer");
        updateCallStatus(CallStatus.NO_ANSWER);

        Platform.runLater(() -> {
            showInfo("Call Timeout", "No answer from " + currentCall.getPeer().getDisplayName());
            new Timeline(new KeyFrame(Duration.seconds(2), e -> handleEndCall())).play();
        });
    }

    private void closeWindow() {
        Platform.runLater(() -> {
            if (stage != null) {
                stage.close();
                System.out.println("✅ Call window closed");
            }
        });
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format("%02d:%02d", minutes, secs);
        }
    }
}