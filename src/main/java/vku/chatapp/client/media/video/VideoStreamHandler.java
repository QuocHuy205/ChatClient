// FILE: vku/chatapp/client/media/video/VideoStreamHandler.java
// ✅ OPTIMIZED: Higher quality, faster connection, JavaFX integrated

package vku.chatapp.client.media.video;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.bytedeco.javacv.*;
import vku.chatapp.client.model.UserSession;
import vku.chatapp.client.p2p.P2PClient;
import vku.chatapp.client.p2p.PeerRegistry;
import vku.chatapp.client.rmi.RMIClient;
import vku.chatapp.common.dto.PeerInfo;
import vku.chatapp.common.protocol.P2PMessage;
import vku.chatapp.common.protocol.P2PMessageType;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * ✅ OPTIMIZED VideoStreamHandler - JavaFX Integrated
 * - Higher video quality (720p, 85% JPEG)
 * - Faster connection with peer caching
 * - Display in JavaFX ImageView
 */
public class VideoStreamHandler {
    private VideoCapture capture;
    private P2PClient p2pClient;
    private PeerRegistry peerRegistry;

    private Thread captureThread;
    private Thread renderThread;
    private AtomicBoolean isStreaming;
    private AtomicBoolean isVideoEnabled;
    private Long remotePeerId;
    private int localP2PPort;

    // JavaFX views
    private ImageView localVideoView;
    private ImageView remoteVideoView;

    // JavaCV components
    private Java2DFrameConverter converter;

    // Frame buffers
    private LinkedBlockingQueue<BufferedImage> remoteFrameBuffer;
    private BufferedImage currentLocalFrame;

    // ✅ OPTIMIZED: Higher quality settings
    private static final int TARGET_FPS = 30;
    private static final int FRAME_INTERVAL_MS = 1000 / TARGET_FPS;
    private static final int VIDEO_WIDTH = 1280;  // 720p
    private static final int VIDEO_HEIGHT = 720;
    private static final float JPEG_QUALITY = 0.85f; // High quality
    private static final int BUFFER_SIZE = 3;

    // Stats
    private long sentFrames = 0;
    private long receivedFrames = 0;
    private long lastStatsTime = System.currentTimeMillis();

    public VideoStreamHandler() {
        this.capture = new VideoCapture(VIDEO_WIDTH, VIDEO_HEIGHT);
        this.p2pClient = new P2PClient();
        this.peerRegistry = PeerRegistry.getInstance();
        this.isStreaming = new AtomicBoolean(false);
        this.isVideoEnabled = new AtomicBoolean(true);
        this.remoteFrameBuffer = new LinkedBlockingQueue<>(BUFFER_SIZE);
        this.converter = new Java2DFrameConverter();

        System.out.println("📹 VideoStreamHandler initialized (High Quality)");
        System.out.println("   Resolution: " + VIDEO_WIDTH + "x" + VIDEO_HEIGHT);
        System.out.println("   Quality: " + (JPEG_QUALITY * 100) + "%");
        System.out.println("   FPS: " + TARGET_FPS);
    }

    public void setLocalP2PPort(int port) {
        this.localP2PPort = port;
    }

    public void setVideoViews(ImageView localView, ImageView remoteView) {
        this.localVideoView = localView;
        this.remoteVideoView = remoteView;
        System.out.println("✅ JavaFX video views connected");
    }

    public void startVideoStream(Long peerId) {
        if (isStreaming.get()) {
            System.out.println("⚠️ Video already streaming");
            return;
        }

        this.remotePeerId = peerId;
        isStreaming.set(true);
        isVideoEnabled.set(true);
        remoteFrameBuffer.clear();

        try {
            // ✅ Pre-fetch peer info for faster connection
            PeerInfo peerInfo = fetchFreshPeerInfo(peerId);
            if (peerInfo != null) {
                System.out.println("✅ Peer info cached: " + peerInfo.getAddress() + ":" + peerInfo.getPort());
            }

            capture.startCapture();
            System.out.println("✅ Video capture started");

            // Start capture thread
            captureThread = new Thread(this::captureAndSendLoop);
            captureThread.setName("VideoCapture-HQ");
            captureThread.setPriority(Thread.NORM_PRIORITY + 1);
            captureThread.setDaemon(true);
            captureThread.start();

            // Start render thread
            renderThread = new Thread(this::renderLoop);
            renderThread.setName("VideoRender-HQ");
            renderThread.setPriority(Thread.NORM_PRIORITY + 1);
            renderThread.setDaemon(true);
            renderThread.start();

            System.out.println("✅ Video streaming started to peer: " + peerId);

        } catch (Exception e) {
            System.err.println("❌ Error starting video: " + e.getMessage());
            e.printStackTrace();
            stopVideoStream();
        }
    }

    private void captureAndSendLoop() {
        long lastFrameTime = 0;

        while (isStreaming.get() && capture.isCapturing()) {
            try {
                long currentTime = System.currentTimeMillis();
                long timeSinceLastFrame = currentTime - lastFrameTime;

                if (timeSinceLastFrame < FRAME_INTERVAL_MS) {
                    Thread.sleep(FRAME_INTERVAL_MS - timeSinceLastFrame);
                    continue;
                }

                if (isVideoEnabled.get()) {
                    BufferedImage frame = capture.captureFrame();

                    if (frame != null) {
                        currentLocalFrame = frame;

                        // Display local video
                        updateLocalVideo(frame);

                        // Encode and send
                        byte[] encodedFrame = encodeFrame(frame);
                        if (encodedFrame != null && encodedFrame.length > 0) {
                            boolean sent = sendVideoFrame(encodedFrame);
                            if (sent) {
                                sentFrames++;
                            }
                        }

                        // Stats every 3 seconds
                        if (currentTime - lastStatsTime >= 3000 && sentFrames > 0) {
                            int fps = (int) (sentFrames / ((currentTime - lastStatsTime) / 1000.0));
                            System.out.println("📹 Sending: " + fps + " fps, Total: " + sentFrames + " frames");
                            lastStatsTime = currentTime;
                            sentFrames = 0;
                        }
                    }
                }

                lastFrameTime = currentTime;

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                System.err.println("❌ Capture error: " + e.getMessage());
            }
        }

        System.out.println("🛑 Video capture loop ended");
    }

    private void renderLoop() {
        while (isStreaming.get()) {
            try {
                BufferedImage remoteFrame = remoteFrameBuffer.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);

                if (remoteFrame != null) {
                    updateRemoteVideo(remoteFrame);
                }

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                System.err.println("❌ Render error: " + e.getMessage());
            }
        }

        System.out.println("🛑 Render loop ended");
    }

    private void updateLocalVideo(BufferedImage frame) {
        if (localVideoView == null) return;

        Platform.runLater(() -> {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                javax.imageio.ImageIO.write(frame, "jpg", baos);
                ByteArrayInputStream bis = new ByteArrayInputStream(baos.toByteArray());
                Image fxImage = new Image(bis);
                localVideoView.setImage(fxImage);
            } catch (Exception e) {
                System.err.println("⚠️ Error updating local video: " + e.getMessage());
            }
        });
    }

    private void updateRemoteVideo(BufferedImage frame) {
        if (remoteVideoView == null) return;

        Platform.runLater(() -> {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                javax.imageio.ImageIO.write(frame, "jpg", baos);
                ByteArrayInputStream bis = new ByteArrayInputStream(baos.toByteArray());
                Image fxImage = new Image(bis);
                remoteVideoView.setImage(fxImage);
            } catch (Exception e) {
                System.err.println("⚠️ Error updating remote video: " + e.getMessage());
            }
        });
    }

    private byte[] encodeFrame(BufferedImage frame) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            javax.imageio.ImageWriter writer = javax.imageio.ImageIO.getImageWritersByFormatName("jpg").next();
            javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();

            if (param.canWriteCompressed()) {
                param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }

            writer.setOutput(javax.imageio.ImageIO.createImageOutputStream(baos));
            writer.write(null, new javax.imageio.IIOImage(frame, null, null), param);
            writer.dispose();

            return baos.toByteArray();
        } catch (Exception e) {
            System.err.println("❌ Encode error: " + e.getMessage());
            return null;
        }
    }

    private boolean sendVideoFrame(byte[] frameData) {
        if (remotePeerId == null) return false;

        PeerInfo peerInfo = peerRegistry.getPeerInfo(remotePeerId);
        if (peerInfo == null) return false;

        Long senderId = UserSession.getInstance().getCurrentUser().getId();
        P2PMessage message = new P2PMessage(P2PMessageType.VIDEO_STREAM, senderId, remotePeerId);
        message.setMessageId(UUID.randomUUID().toString());
        message.setFileData(frameData);
        message.setTimestamp(System.currentTimeMillis());
        message.setSourcePort(localP2PPort);

        return p2pClient.sendMessage(peerInfo.getAddress(), peerInfo.getPort(), message);
    }

    public void receiveVideoStream(P2PMessage message) {
        if (!isStreaming.get()) return;

        try {
            byte[] frameData = message.getFileData();
            if (frameData != null && frameData.length > 0) {
                ByteArrayInputStream bis = new ByteArrayInputStream(frameData);
                BufferedImage image = javax.imageio.ImageIO.read(bis);

                if (image != null) {
                    if (!remoteFrameBuffer.offer(image)) {
                        remoteFrameBuffer.poll();
                        remoteFrameBuffer.offer(image);
                    }

                    receivedFrames++;

                    if (receivedFrames == 1) {
                        System.out.println("✅ First remote frame received!");
                    }

                    if (receivedFrames % 90 == 0) {
                        System.out.println("✅ Received " + receivedFrames + " frames, " +
                                (frameData.length / 1024) + " KB/frame");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error receiving video: " + e.getMessage());
        }
    }

    public void setVideoEnabled(boolean enabled) {
        this.isVideoEnabled.set(enabled);

        if (!enabled && localVideoView != null) {
            Platform.runLater(() -> localVideoView.setImage(null));
        }

        System.out.println(enabled ? "📹 Video enabled" : "📹❌ Video disabled");
    }

    public void stopVideoStream() {
        isStreaming.set(false);

        if (capture != null) {
            capture.stopCapture();
        }

        if (captureThread != null && captureThread.isAlive()) {
            try {
                captureThread.join(1000);
            } catch (InterruptedException e) {
                captureThread.interrupt();
            }
        }

        if (renderThread != null && renderThread.isAlive()) {
            try {
                renderThread.join(1000);
            } catch (InterruptedException e) {
                renderThread.interrupt();
            }
        }

        // Clear views
        if (localVideoView != null) {
            Platform.runLater(() -> localVideoView.setImage(null));
        }
        if (remoteVideoView != null) {
            Platform.runLater(() -> remoteVideoView.setImage(null));
        }

        System.out.println("✅ Video streaming stopped");
    }

    public boolean isStreaming() {
        return isStreaming.get();
    }

    private PeerInfo fetchFreshPeerInfo(Long userId) {
        PeerInfo peerInfo = peerRegistry.getPeerInfo(userId);

        if (peerInfo == null || peerInfo.getPort() == 0) {
            try {
                peerInfo = RMIClient.getInstance().getPeerDiscoveryService().getPeerInfo(userId);
                if (peerInfo != null) {
                    peerRegistry.addPeer(peerInfo);
                    System.out.println("✅ Fetched peer info: " + peerInfo.getAddress() + ":" + peerInfo.getPort());
                }
            } catch (Exception e) {
                System.err.println("❌ Error fetching peer info: " + e.getMessage());
            }
        }

        return peerInfo;
    }

    public void setVideoQuality(VideoQuality quality) {
        capture.setResolution(quality.width, quality.height);
        System.out.println("📹 Video quality: " + quality);
    }

    public enum VideoQuality {
        LOW(320, 240, 15),
        MEDIUM(640, 480, 25),
        HIGH(1280, 720, 30),
        FULL_HD(1920, 1080, 30);

        final int width, height, fps;
        VideoQuality(int w, int h, int f) {
            width = w;
            height = h;
            fps = f;
        }
    }
}