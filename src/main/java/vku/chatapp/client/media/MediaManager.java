// FILE: vku/chatapp/client/media/MediaManager.java
// ✅ OPTIMIZED: JavaFX integrated, better quality

package vku.chatapp.client.media;

import javafx.scene.image.ImageView;
import vku.chatapp.client.media.audio.AudioStreamHandler;
import vku.chatapp.client.media.video.VideoStreamHandler;
import vku.chatapp.client.model.CallSession;
import vku.chatapp.common.enums.CallType;
import vku.chatapp.common.protocol.P2PMessage;

/**
 * ✅ OPTIMIZED MediaManager
 * - JavaFX ImageView integration
 * - Better quality settings
 * - Faster initialization
 */
public class MediaManager {
    private static MediaManager instance;

    private boolean isMuted = false;
    private boolean isVideoEnabled = true;
    private CallSession currentCall;

    private AudioStreamHandler audioHandler;
    private VideoStreamHandler videoHandler;

    private int localP2PPort = 0;

    private MediaManager() {
        this.audioHandler = new AudioStreamHandler();
        System.out.println("✅ MediaManager initialized (High Quality Mode)");
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

    public void setLocalP2PPort(int port) {
        this.localP2PPort = port;
        System.out.println("🎬 MediaManager P2P port: " + port);

        if (audioHandler != null) {
            audioHandler.setLocalP2PPort(port);
        }
    }

    /**
     * Start call with JavaFX views
     */
    public void startCall(CallSession callSession, ImageView localVideoView, ImageView remoteVideoView) {
        this.currentCall = callSession;

        System.out.println("🎬 Starting call");
        System.out.println("   Type: " + callSession.getCallType());
        System.out.println("   Peer: " + callSession.getPeer().getDisplayName());

        Long peerId = callSession.getPeer().getId();

        try {
            // Set P2P port
            if (localP2PPort > 0) {
                audioHandler.setLocalP2PPort(localP2PPort);
            }

            // Start audio
            System.out.println("🎤 Starting audio...");
            audioHandler.startAudioStream(peerId);

            // Start video if needed
            if (callSession.getCallType() == CallType.VIDEO) {
                System.out.println("📹 Starting video...");

                videoHandler = new VideoStreamHandler();

                if (localP2PPort > 0) {
                    videoHandler.setLocalP2PPort(localP2PPort);
                }

                // ✅ Connect JavaFX views
                videoHandler.setVideoViews(localVideoView, remoteVideoView);
                videoHandler.startVideoStream(peerId);

                System.out.println("✅ Video stream started");
            }

            playNotificationSound("Call connected");
            System.out.println("✅ Call started successfully");

        } catch (Exception e) {
            System.err.println("❌ Error starting call: " + e.getMessage());
            e.printStackTrace();
            endCall();
        }
    }

    public void endCall() {
        System.out.println("🛑 Ending call");

        if (audioHandler != null && audioHandler.isStreaming()) {
            audioHandler.stopAudioStream();
            System.out.println("🎤 Audio stopped");
        }

        if (videoHandler != null && videoHandler.isStreaming()) {
            videoHandler.stopVideoStream();
            System.out.println("📹 Video stopped");
        }

        currentCall = null;
        videoHandler = null;

        isMuted = false;
        isVideoEnabled = true;

        System.out.println("✅ Call ended");
    }

    public void handleIncomingAudio(P2PMessage message) {
        if (currentCall == null) return;

        if (audioHandler != null) {
            audioHandler.receiveAudioStream(message);
        }
    }

    public void handleIncomingVideo(P2PMessage message) {
        if (currentCall == null) return;

        if (videoHandler != null) {
            videoHandler.receiveVideoStream(message);
        }
    }

    public void setMuted(boolean muted) {
        this.isMuted = muted;

        if (audioHandler != null) {
            audioHandler.setMuted(muted);
        }

        System.out.println((muted ? "🔇" : "🔊") + " Microphone " + (muted ? "muted" : "unmuted"));
    }

    public void setVideoEnabled(boolean enabled) {
        this.isVideoEnabled = enabled;

        if (currentCall == null || currentCall.getCallType() != CallType.VIDEO) {
            return;
        }

        if (videoHandler != null) {
            videoHandler.setVideoEnabled(enabled);
            System.out.println(enabled ? "📹 Video enabled" : "📹❌ Video disabled");
        }
    }

    public void switchCamera() {
        System.out.println("🔄 Switching camera...");

        if (videoHandler != null && videoHandler.isStreaming()) {
            System.out.println("⚠️ Camera switching not implemented");
        }
    }

    public void setAudioQuality(AudioStreamHandler.AudioQuality quality) {
        if (audioHandler != null) {
            audioHandler.setAudioQuality(quality);
        }
    }

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

    private void playNotificationSound(String message) {
        try {
            java.awt.Toolkit.getDefaultToolkit().beep();
            System.out.println("🔔 " + message);
        } catch (Exception e) {
            System.err.println("⚠️ Could not play sound: " + e.getMessage());
        }
    }
}