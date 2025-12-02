// FILE: vku/chatapp/client/media/audio/AudioStreamHandler.java
// ✅ FIX: Add senderId to P2P messages

package vku.chatapp.client.media.audio;

import vku.chatapp.client.model.UserSession;
import vku.chatapp.client.p2p.P2PClient;
import vku.chatapp.client.p2p.PeerRegistry;
import vku.chatapp.common.dto.PeerInfo;
import vku.chatapp.common.protocol.P2PMessage;
import vku.chatapp.common.protocol.P2PMessageType;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioStreamHandler {
    private AudioCapture capture;
    private AudioPlayer player;
    private P2PClient p2pClient;
    private PeerRegistry peerRegistry;

    private Thread captureThread;
    private AtomicBoolean isStreaming;
    private Long remotePeerId;

    private static final int BUFFER_SIZE = 4096;
    private static final int FRAME_RATE = 50; // 50 frames per second
    private static final int FRAME_INTERVAL_MS = 1000 / FRAME_RATE;

    public AudioStreamHandler() {
        this.capture = new AudioCapture();
        this.player = new AudioPlayer();
        this.p2pClient = new P2PClient();
        this.peerRegistry = PeerRegistry.getInstance();
        this.isStreaming = new AtomicBoolean(false);
    }

    public void startAudioStream(Long peerId) {
        if (isStreaming.get()) {
            System.out.println("⚠️ Audio stream already running");
            return;
        }

        this.remotePeerId = peerId;
        isStreaming.set(true);

        try {
            // Start audio capture
            capture.startCapture();
            System.out.println("✅ Audio capture started");

            // Start sending audio
            captureThread = new Thread(this::captureAndSendLoop);
            captureThread.setName("AudioCapture-Thread");
            captureThread.setDaemon(true);
            captureThread.start();

            System.out.println("✅ Audio streaming started to peer: " + peerId);

        } catch (Exception e) {
            System.err.println("❌ Error starting audio stream: " + e.getMessage());
            e.printStackTrace();
            stopAudioStream();
        }
    }

    private void captureAndSendLoop() {
        while (isStreaming.get() && capture.isCapturing()) {
            try {
                // Capture audio frame
                byte[] audioData = capture.captureAudio(BUFFER_SIZE);

                if (audioData.length > 0) {
                    // Send to remote peer
                    sendAudioFrame(audioData);
                }

                // Control frame rate
                Thread.sleep(FRAME_INTERVAL_MS);

            } catch (InterruptedException e) {
                System.out.println("⚠️ Audio capture interrupted");
                break;
            } catch (Exception e) {
                System.err.println("❌ Error in capture loop: " + e.getMessage());
            }
        }
        System.out.println("🛑 Audio capture loop ended");
    }

    private void sendAudioFrame(byte[] audioData) {
        if (remotePeerId == null) return;

        PeerInfo peerInfo = peerRegistry.getPeerInfo(remotePeerId);
        if (peerInfo == null) {
            // System.err.println("❌ Peer not found: " + remotePeerId);
            return;
        }

        // ✅ FIX: Add senderId
        Long senderId = UserSession.getInstance().getCurrentUser().getId();

        P2PMessage message = new P2PMessage(P2PMessageType.AUDIO_STREAM, senderId, remotePeerId);
        message.setMessageId(UUID.randomUUID().toString());
        message.setFileData(audioData);
        message.setTimestamp(System.currentTimeMillis());

        // Send asynchronously to avoid blocking
        p2pClient.sendMessageAsync(peerInfo.getAddress(), peerInfo.getPort(), message);
    }

    public void receiveAudioStream(P2PMessage message) {
        if (!isStreaming.get()) {
            return;
        }

        try {
            // Start player if not started
            if (!player.isPlaying()) {
                player.startPlayback();
                System.out.println("✅ Audio playback started");
            }

            byte[] audioData = message.getFileData();
            if (audioData != null && audioData.length > 0) {
                player.playAudio(audioData);
            }

        } catch (Exception e) {
            System.err.println("❌ Error receiving audio stream: " + e.getMessage());
        }
    }

    public void stopAudioStream() {
        isStreaming.set(false);

        // Stop capture
        if (capture != null) {
            capture.stopCapture();
        }

        // Stop playback
        if (player != null) {
            player.stopPlayback();
        }

        // Wait for threads to finish
        if (captureThread != null && captureThread.isAlive()) {
            try {
                captureThread.join(1000);
            } catch (InterruptedException e) {
                captureThread.interrupt();
            }
        }

        System.out.println("✅ Audio streaming stopped");
    }

    public boolean isStreaming() {
        return isStreaming.get();
    }

    // Audio quality settings
    public void setAudioQuality(AudioQuality quality) {
        System.out.println("🎵 Audio quality set to: " + quality);
        // TODO: Recreate AudioCapture and AudioPlayer with new format
    }

    public enum AudioQuality {
        LOW(22050, 8),      // 22kHz, 8-bit
        MEDIUM(44100, 16),  // 44.1kHz, 16-bit (default)
        HIGH(48000, 16);    // 48kHz, 16-bit

        private final int sampleRate;
        private final int bitDepth;

        AudioQuality(int sampleRate, int bitDepth) {
            this.sampleRate = sampleRate;
            this.bitDepth = bitDepth;
        }

        public int getSampleRate() {
            return sampleRate;
        }

        public int getBitDepth() {
            return bitDepth;
        }
    }
}