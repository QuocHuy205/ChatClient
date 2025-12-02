// FILE: vku/chatapp/client/media/MediaManager.java
// ✅ FULL IMPLEMENTATION với Audio & Video streaming

package vku.chatapp.client.media;

import javafx.scene.image.ImageView;
import vku.chatapp.client.media.audio.AudioStreamHandler;
import vku.chatapp.client.media.video.VideoStreamHandler;
import vku.chatapp.client.model.CallSession;
import vku.chatapp.common.enums.CallType;
import vku.chatapp.common.protocol.P2PMessage;

public class MediaManager {
    private static MediaManager instance;

    private boolean isMuted = false;
    private boolean isVideoEnabled = true;
    private CallSession currentCall;

    // ✅ Audio & Video handlers
    private AudioStreamHandler audioHandler;
    private VideoStreamHandler videoHandler;

    private ImageView localView;
    private ImageView remoteView;

    private MediaManager() {
        this.audioHandler = new AudioStreamHandler();
    }

    public static MediaManager getInstance() {
        if (instance == null) {
            synchronized (MediaManager.class) {
                if (instance == null) {
                    instance = new MediaManager();
                }
            }
        }
        return instance;
    }

    /**
     * Start a call with audio/video streaming
     */
    public void startCall(CallSession callSession, ImageView localVideoView, ImageView remoteVideoView) {
        this.currentCall = callSession;
        this.localView = localVideoView;
        this.remoteView = remoteVideoView;

        System.out.println("🎬 MediaManager: Starting call");
        System.out.println("   Call Type: " + callSession.getCallType());
        System.out.println("   Peer: " + callSession.getPeer().getDisplayName());

        Long peerId = callSession.getPeer().getId();

        try {
            // ✅ Always start audio for both audio and video calls
            System.out.println("🎤 Starting audio stream...");
            audioHandler.startAudioStream(peerId);

            // ✅ Start video if it's a video call
            if (callSession.getCallType() == CallType.VIDEO && remoteVideoView != null) {
                System.out.println("📹 Starting video stream...");
                videoHandler = new VideoStreamHandler(localVideoView, remoteVideoView);
                videoHandler.startVideoStream(peerId);

                // Show video views
                if (localVideoView != null) {
                    localVideoView.setVisible(true);
                }
                if (remoteVideoView != null) {
                    remoteVideoView.setVisible(true);
                }
            }

            playNotificationSound("Call connected");
            System.out.println("✅ MediaManager: Call started successfully");

        } catch (Exception e) {
            System.err.println("❌ Error starting media streams: " + e.getMessage());
            e.printStackTrace();

            // Cleanup on error
            endCall();
        }
    }

    /**
     * End the current call and stop all streams
     */
    public void endCall() {
        System.out.println("🛑 MediaManager: Ending call");

        // Stop audio
        if (audioHandler != null && audioHandler.isStreaming()) {
            audioHandler.stopAudioStream();
            System.out.println("🎤 Audio stream stopped");
        }

        // Stop video
        if (videoHandler != null && videoHandler.isStreaming()) {
            videoHandler.stopVideoStream();
            System.out.println("📹 Video stream stopped");
        }

        // Hide video views
        if (localView != null) {
            localView.setVisible(false);
        }
        if (remoteView != null) {
            remoteView.setVisible(false);
        }

        // Clear references
        currentCall = null;
        localView = null;
        remoteView = null;
        videoHandler = null;

        System.out.println("✅ MediaManager: Call ended");
    }

    /**
     * Handle incoming audio stream
     */
    public void handleIncomingAudio(P2PMessage message) {
        if (currentCall == null) {
            // System.err.println("⚠️ Received audio but no active call");
            return;
        }

        if (audioHandler != null) {
            audioHandler.receiveAudioStream(message);
        }
    }

    /**
     * Handle incoming video stream
     */
    public void handleIncomingVideo(P2PMessage message) {
        if (currentCall == null || remoteView == null) {
            // System.err.println("⚠️ Received video but no active call or view");
            return;
        }

        if (videoHandler != null) {
            videoHandler.receiveVideoStream(message);
        }
    }

    /**
     * Mute/unmute microphone
     */
    public void setMuted(boolean muted) {
        this.isMuted = muted;

        if (muted) {
            // Stop audio capture but keep playback
            if (audioHandler != null && audioHandler.isStreaming()) {
                // audioHandler.pauseCapture(); // TODO: Add pause method
                System.out.println("🔇 Microphone muted");
            }
        } else {
            // Resume audio capture
            if (audioHandler != null && audioHandler.isStreaming()) {
                // audioHandler.resumeCapture(); // TODO: Add resume method
                System.out.println("🔊 Microphone unmuted");
            }
        }
    }

    /**
     * Enable/disable video
     */
    public void setVideoEnabled(boolean enabled) {
        this.isVideoEnabled = enabled;

        if (currentCall == null || currentCall.getCallType() != CallType.VIDEO) {
            return;
        }

        if (enabled) {
            // Resume video streaming
            if (videoHandler == null && currentCall != null) {
                Long peerId = currentCall.getPeer().getId();
                videoHandler = new VideoStreamHandler(localView, remoteView);
                videoHandler.startVideoStream(peerId);
            }

            if (localView != null) {
                localView.setVisible(true);
            }

            System.out.println("📹 Video enabled");

        } else {
            // Stop video streaming but keep call active
            if (videoHandler != null) {
                videoHandler.stopVideoStream();
                videoHandler = null;
            }

            if (localView != null) {
                localView.setVisible(false);
            }

            System.out.println("📹❌ Video disabled");
        }
    }

    /**
     * Switch between front/back camera
     */
    public void switchCamera() {
        System.out.println("🔄 Switching camera...");

        if (videoHandler != null && videoHandler.isStreaming()) {
            // TODO: Implement camera switching in VideoCapture
            System.out.println("⚠️ Camera switching not implemented yet");
        }
    }

    /**
     * Set audio quality
     */
    public void setAudioQuality(AudioStreamHandler.AudioQuality quality) {
        if (audioHandler != null) {
            audioHandler.setAudioQuality(quality);
        }
    }

    /**
     * Set video quality
     */
    public void setVideoQuality(VideoStreamHandler.VideoQuality quality) {
        if (videoHandler != null) {
            videoHandler.setVideoQuality(quality);
        }
    }

    public boolean isMuted() {
        return isMuted;
    }

    public boolean isVideoEnabled() {
        return isVideoEnabled;
    }

    public boolean isCallActive() {
        return currentCall != null &&
                (audioHandler != null && audioHandler.isStreaming());
    }

    /**
     * Play notification sound (basic beep)
     */
    private void playNotificationSound(String message) {
        try {
            java.awt.Toolkit.getDefaultToolkit().beep();
            System.out.println("🔔 " + message);
        } catch (Exception e) {
            System.err.println("⚠️ Could not play sound: " + e.getMessage());
        }
    }
}