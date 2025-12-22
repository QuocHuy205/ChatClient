// FILE: vku/chatapp/client/media/audio/AudioStreamHandler.java
// ✅ OPTIMIZED: Lower latency, better sync

package vku.chatapp.client.media.audio;

import vku.chatapp.client.model.UserSession;
import vku.chatapp.client.p2p.P2PClient;
import vku.chatapp.client.p2p.PeerRegistry;
import vku.chatapp.client.rmi.RMIClient;
import vku.chatapp.common.dto.PeerInfo;
import vku.chatapp.common.protocol.P2PMessage;
import vku.chatapp.common.protocol.P2PMessageType;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AudioStreamHandler {
    private AudioCapture capture;
    private AudioPlayer player;
    private P2PClient p2pClient;
    private PeerRegistry peerRegistry;

    private Thread captureThread;
    private Thread playbackThread;
    private AtomicBoolean isStreaming;
    private AtomicBoolean isMuted;
    private Long remotePeerId;
    private int localP2PPort;

    // ✅ OPTIMIZED: Lower latency settings
    private static final int BUFFER_SIZE = 640; // Smaller = lower latency (40ms at 16kHz)
    private static final int FRAME_RATE = 50; // 50 packets/sec
    private static final int FRAME_INTERVAL_MS = 20; // 20ms intervals

    // ✅ Use ConcurrentLinkedQueue for better performance
    private ConcurrentLinkedQueue<byte[]> audioQueue;

    public AudioStreamHandler() {
        this.capture = new AudioCapture();
        this.player = new AudioPlayer();
        this.p2pClient = new P2PClient();
        this.peerRegistry = PeerRegistry.getInstance();
        this.isStreaming = new AtomicBoolean(false);
        this.isMuted = new AtomicBoolean(false);
        this.audioQueue = new ConcurrentLinkedQueue<>();

        System.out.println("🎤 AudioStreamHandler initialized (Low Latency Mode)");
    }

    public void setLocalP2PPort(int port) {
        this.localP2PPort = port;
    }

    public void startAudioStream(Long peerId) {
        if (isStreaming.get()) {
            System.out.println("⚠️ Audio already streaming");
            return;
        }

        this.remotePeerId = peerId;
        isStreaming.set(true);
        isMuted.set(false);
        audioQueue.clear();

        try {
            // Start capture
            capture.startCapture();

            // Start playback
            player.startPlayback();

            // Start capture thread
            captureThread = new Thread(this::captureAndSendLoop);
            captureThread.setName("AudioCapture-LowLatency");
            captureThread.setPriority(Thread.MAX_PRIORITY);
            captureThread.setDaemon(true);
            captureThread.start();

            // Start playback thread
            playbackThread = new Thread(this::playbackLoop);
            playbackThread.setName("AudioPlayback-LowLatency");
            playbackThread.setPriority(Thread.MAX_PRIORITY);
            playbackThread.setDaemon(true);
            playbackThread.start();

            System.out.println("✅ Audio streaming started");

        } catch (Exception e) {
            System.err.println("❌ Error starting audio: " + e.getMessage());
            e.printStackTrace();
            stopAudioStream();
        }
    }

    private void captureAndSendLoop() {
        long frameCount = 0;
        long lastFrameTime = System.nanoTime();

        while (isStreaming.get() && capture.isCapturing()) {
            try {
                if (!isMuted.get()) {
                    byte[] audioData = capture.captureAudio(BUFFER_SIZE);

                    if (audioData != null && audioData.length > 0) {
                        sendAudioFrame(audioData);
                        frameCount++;
                    }
                }

                // Precise timing using nanoTime
                long currentTime = System.nanoTime();
                long elapsed = (currentTime - lastFrameTime) / 1_000_000; // Convert to ms
                long sleepTime = FRAME_INTERVAL_MS - elapsed;

                if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
                }

                lastFrameTime = System.nanoTime();

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                System.err.println("❌ Capture error: " + e.getMessage());
            }
        }

        System.out.println("🛑 Audio capture loop ended");
    }

    private void playbackLoop() {
        while (isStreaming.get()) {
            try {
                byte[] audioData = audioQueue.poll();

                if (audioData != null) {
                    player.playAudio(audioData);
                } else {
                    // Small sleep if queue is empty
                    Thread.sleep(5);
                }

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                System.err.println("❌ Playback error: " + e.getMessage());
            }
        }

        System.out.println("🛑 Audio playback loop ended");
    }

    private void sendAudioFrame(byte[] audioData) {
        if (remotePeerId == null) return;

        PeerInfo peerInfo = fetchFreshPeerInfo(remotePeerId);
        if (peerInfo == null) return;

        Long senderId = UserSession.getInstance().getCurrentUser().getId();

        P2PMessage message = new P2PMessage(P2PMessageType.AUDIO_STREAM, senderId, remotePeerId);
        message.setMessageId(UUID.randomUUID().toString());
        message.setFileData(audioData);
        message.setTimestamp(System.currentTimeMillis());
        message.setSourcePort(localP2PPort);

        p2pClient.sendMessageAsync(peerInfo.getAddress(), peerInfo.getPort(), message);
    }

    public void receiveAudioStream(P2PMessage message) {
        if (!isStreaming.get()) return;

        try {
            byte[] audioData = message.getFileData();
            if (audioData != null && audioData.length > 0) {
                // Add to queue, limit size to prevent lag
                if (audioQueue.size() < 5) {
                    audioQueue.offer(audioData);
                } else {
                    // Drop oldest if queue is full
                    audioQueue.poll();
                    audioQueue.offer(audioData);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error receiving audio: " + e.getMessage());
        }
    }

    public void setMuted(boolean muted) {
        this.isMuted.set(muted);
        System.out.println(muted ? "🔇 Muted" : "🔊 Unmuted");
    }

    public void stopAudioStream() {
        isStreaming.set(false);

        if (capture != null) {
            capture.stopCapture();
        }

        if (player != null) {
            player.stopPlayback();
        }

        if (captureThread != null && captureThread.isAlive()) {
            try {
                captureThread.join(500);
            } catch (InterruptedException e) {
                captureThread.interrupt();
            }
        }

        if (playbackThread != null && playbackThread.isAlive()) {
            try {
                playbackThread.join(500);
            } catch (InterruptedException e) {
                playbackThread.interrupt();
            }
        }

        audioQueue.clear();
        System.out.println("✅ Audio streaming stopped");
    }

    public boolean isStreaming() {
        return isStreaming.get();
    }

    private PeerInfo fetchFreshPeerInfo(Long userId) {
        PeerInfo peerInfo = peerRegistry.getPeerInfo(userId);

        if (peerInfo == null || peerInfo.getPort() == 0) {
            try {
                peerInfo = RMIClient.getInstance()
                        .getPeerDiscoveryService()
                        .getPeerInfo(userId);

                if (peerInfo != null) {
                    peerRegistry.addPeer(peerInfo);
                }
            } catch (Exception e) {
                // Silent fail
            }
        }

        return peerInfo;
    }

    public void setAudioQuality(AudioQuality quality) {
        System.out.println("🎵 Audio quality: " + quality);
    }

    public enum AudioQuality {
        LOW(8000, 8),
        MEDIUM(16000, 16),
        HIGH(24000, 16);

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