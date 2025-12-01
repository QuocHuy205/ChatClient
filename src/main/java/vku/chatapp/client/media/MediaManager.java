package vku.chatapp.client.media;

import javafx.scene.image.ImageView;
import vku.chatapp.client.media.audio.AudioStreamHandler;
import vku.chatapp.client.media.video.VideoStreamHandler;
import vku.chatapp.client.model.CallSession;
import vku.chatapp.common.enums.CallType;
import vku.chatapp.common.protocol.P2PMessage;

public class MediaManager {
    private static MediaManager instance;

    private AudioStreamHandler audioHandler;
    private VideoStreamHandler videoHandler;
    private CallSession currentCall;

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

    public void startCall(CallSession callSession, ImageView localView, ImageView remoteView) {
        this.currentCall = callSession;

        if (callSession.getCallType() == CallType.AUDIO) {
            startAudioCall(callSession.getPeer().getId());
        } else if (callSession.getCallType() == CallType.VIDEO) {
            startVideoCall(callSession.getPeer().getId(), localView, remoteView);
        }
    }

    private void startAudioCall(Long peerId) {
        audioHandler.startAudioStream(peerId);
    }

    private void startVideoCall(Long peerId, ImageView localView, ImageView remoteView) {
        // Start audio
        audioHandler.startAudioStream(peerId);

        // Start video
        if (videoHandler == null) {
            videoHandler = new VideoStreamHandler(localView, remoteView);
        }
        videoHandler.startVideoStream(peerId);
    }

    public void handleIncomingAudio(P2PMessage message) {
        if (audioHandler != null) {
            audioHandler.receiveAudioStream(message);
        }
    }

    public void handleIncomingVideo(P2PMessage message) {
        if (videoHandler != null) {
            videoHandler.receiveVideoStream(message);
        }
    }

    public void endCall() {
        if (audioHandler != null && audioHandler.isStreaming()) {
            audioHandler.stopAudioStream();
        }

        if (videoHandler != null && videoHandler.isStreaming()) {
            videoHandler.stopVideoStream();
        }

        if (currentCall != null) {
            currentCall.end();
            currentCall = null;
        }
    }

    public boolean isInCall() {
        return currentCall != null &&
                (audioHandler.isStreaming() ||
                        (videoHandler != null && videoHandler.isStreaming()));
    }

    public CallSession getCurrentCall() {
        return currentCall;
    }

    // Quality control
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
}