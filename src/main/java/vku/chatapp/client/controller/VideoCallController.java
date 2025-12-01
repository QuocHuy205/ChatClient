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

import java.util.UUID;

public class VideoCallController extends BaseController {
    @FXML private StackPane callContainer;
    @FXML private ImageView localVideoView;
    @FXML private ImageView remoteVideoView;
    @FXML private Label peerNameLabel;
    @FXML private Label callStatusLabel;
    @FXML private Label callDurationLabel;
    @FXML private Button muteButton;
    @FXML private Button videoToggleButton;
    @FXML private Button endCallButton;
    @FXML private Button switchCameraButton;

    private CallService callService;
    private MediaManager mediaManager;
    private P2PMessageHandler messageHandler;
    private CallSession currentCall;
    private Timeline durationTimer;

    private boolean isMuted = false;
    private boolean isVideoEnabled = true;

    @FXML
    public void initialize() {
        callService = new CallService();
        mediaManager = MediaManager.getInstance();

        setupCallDurationTimer();
        setupMessageHandler();
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

    // Initiate outgoing call
    public void initiateCall(UserDTO peer, CallType callType) {
        String callId = UUID.randomUUID().toString();
        boolean isCaller = true;

        currentCall = new CallSession(callId, peer, callType, isCaller);

        peerNameLabel.setText(peer.getDisplayName());
        callStatusLabel.setText("Calling...");

        // Send call offer
        boolean sent = callService.initiateCall(peer.getId(), callType);

        if (sent) {
            currentCall.setStatus(CallStatus.RINGING);

            // Timeout after 30 seconds
            Timeline timeout = new Timeline(new KeyFrame(Duration.seconds(30), e -> {
                if (currentCall.getStatus() == CallStatus.RINGING) {
                    handleCallTimeout();
                }
            }));
            timeout.play();
        } else {
            showError("Call Failed", "Could not initiate call. User may be offline.");
            handleEndCall();
        }
    }

    // Receive incoming call
    public void receiveCall(CallSession incomingCall) {
        this.currentCall = incomingCall;

        peerNameLabel.setText(incomingCall.getPeer().getDisplayName());
        callStatusLabel.setText("Incoming " +
                (incomingCall.getCallType() == CallType.VIDEO ? "Video" : "Voice") +
                " Call");

        // Show accept/reject UI
        showIncomingCallUI();
    }

    private void showIncomingCallUI() {
        // TODO: Show accept/reject buttons
        // For now, auto-accept for testing
        handleAcceptCall();
    }

    private void handleAcceptCall() {
        if (currentCall == null) return;

        // Send answer
        callService.answerCall(currentCall.getPeer().getId(), currentCall.getCallId());

        // Start media streams
        startMediaStreams();
    }

    private void startMediaStreams() {
        currentCall.start();
        callStatusLabel.setText("Connected");
        durationTimer.play();

        // Start media
        if (currentCall.getCallType() == CallType.VIDEO) {
            mediaManager.startCall(currentCall, localVideoView, remoteVideoView);
        } else {
            mediaManager.startCall(currentCall, null, null);
        }

        System.out.println("Media streams started");
    }

    @FXML
    private void handleEndCall() {
        if (currentCall == null) return;

        // Send end call message
        callService.endCall(currentCall.getPeer().getId(), currentCall.getCallId());

        // Stop media
        mediaManager.endCall();

        // Stop timer
        if (durationTimer != null) {
            durationTimer.stop();
        }

        // Update status
        if (currentCall != null) {
            currentCall.end();
        }

        // Close window or go back
        Platform.runLater(() -> {
            if (stage != null) {
                stage.close();
            }
        });
    }

    @FXML
    private void handleMute() {
        isMuted = !isMuted;
        muteButton.setText(isMuted ? "🔇" : "🔊");

        // TODO: Implement actual mute functionality
        System.out.println("Mute: " + isMuted);
    }

    @FXML
    private void handleVideoToggle() {
        if (currentCall == null || currentCall.getCallType() != CallType.VIDEO) {
            return;
        }

        isVideoEnabled = !isVideoEnabled;
        videoToggleButton.setText(isVideoEnabled ? "📹" : "📹❌");

        // TODO: Implement video toggle
        System.out.println("Video enabled: " + isVideoEnabled);
    }

    @FXML
    private void handleSwitchCamera() {
        // TODO: Implement camera switching
        System.out.println("Switch camera");
    }

    private void handleCallMessage(P2PMessage message) {
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
        // This is handled by main controller showing this window
    }

    private void handleCallAnswer(P2PMessage message) {
        if (currentCall == null || !currentCall.isCaller()) {
            return;
        }

        // Remote peer answered
        startMediaStreams();
    }

    private void handleCallReject(P2PMessage message) {
        callStatusLabel.setText("Call Rejected");

        // Show message and close
        Platform.runLater(() -> {
            showInfo("Call Rejected", "The call was rejected");
            handleEndCall();
        });
    }

    private void handleCallEnd(P2PMessage message) {
        callStatusLabel.setText("Call Ended");

        // End call
        handleEndCall();
    }

    private void handleCallTimeout() {
        callStatusLabel.setText("No Answer");

        Platform.runLater(() -> {
            showInfo("Call Timeout", "No answer from " + currentCall.getPeer().getDisplayName());
            handleEndCall();
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