// FILE: vku/chatapp/client/media/video/VideoStreamHandler.java
// ✅ FIXED: Camera freeze issue

package vku.chatapp.client.media.video;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

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

    private ImageView localVideoView;
    private ImageView remoteVideoView;

    private ConcurrentLinkedQueue<BufferedImage> remoteFrameBuffer;

    // ✅ OPTIMIZED: Balanced settings
    private static final int TARGET_FPS = 25;
    private static final int FRAME_INTERVAL_MS = 40;
    private static final int VIDEO_WIDTH = 640;
    private static final int VIDEO_HEIGHT = 480;
    private static final float JPEG_QUALITY = 0.60f;
    private static final int BUFFER_SIZE = 2;

    private long sentFrames = 0;
    private long receivedFrames = 0;
    private long lastStatsTime = System.currentTimeMillis();

    // ✅ FIX: Separate update tracking for local and remote
    private AtomicReference<BufferedImage> pendingLocalFrame = new AtomicReference<>();
    private AtomicReference<BufferedImage> pendingRemoteFrame = new AtomicReference<>();
    private volatile boolean localViewNeedsUpdate = false;
    private volatile boolean remoteViewNeedsUpdate = false;

    public VideoStreamHandler() {
        this.capture = new VideoCapture(VIDEO_WIDTH, VIDEO_HEIGHT);
        this.p2pClient = new P2PClient();
        this.peerRegistry = PeerRegistry.getInstance();
        this.isStreaming = new AtomicBoolean(false);
        this.isVideoEnabled = new AtomicBoolean(true);
        this.remoteFrameBuffer = new ConcurrentLinkedQueue<>();

        System.out.println("📹 VideoStreamHandler initialized");
    }

    public void setLocalP2PPort(int port) {
        this.localP2PPort = port;
    }

    public void setVideoViews(ImageView localView, ImageView remoteView) {
        this.localVideoView = localView;
        this.remoteVideoView = remoteView;

        // ✅ FIX: Start UI update timer on JavaFX thread
        Platform.runLater(this::startUIUpdateTimer);

        System.out.println("✅ Video views connected");
    }

    // ✅ FIX: Dedicated UI update thread to prevent freezing
    private void startUIUpdateTimer() {
        javafx.animation.Timeline uiUpdateTimer = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.millis(33), e -> {
                    // Update local view
                    if (localViewNeedsUpdate && pendingLocalFrame.get() != null) {
                        BufferedImage frame = pendingLocalFrame.get();
                        if (frame != null) {
                            try {
                                Image fxImage = bufferedImageToFXImage(frame);
                                if (localVideoView != null) {
                                    localVideoView.setImage(fxImage);
                                }
                            } catch (Exception ex) {
                                // Silent fail
                            }
                        }
                        localViewNeedsUpdate = false;
                    }

                    // Update remote view
                    if (remoteViewNeedsUpdate && pendingRemoteFrame.get() != null) {
                        BufferedImage frame = pendingRemoteFrame.get();
                        if (frame != null) {
                            try {
                                Image fxImage = bufferedImageToFXImage(frame);
                                if (remoteVideoView != null) {
                                    remoteVideoView.setImage(fxImage);
                                }
                            } catch (Exception ex) {
                                // Silent fail
                            }
                        }
                        remoteViewNeedsUpdate = false;
                    }
                })
        );
        uiUpdateTimer.setCycleCount(javafx.animation.Timeline.INDEFINITE);
        uiUpdateTimer.play();

        System.out.println("✅ UI update timer started");
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
            PeerInfo peerInfo = fetchFreshPeerInfo(peerId);
            if (peerInfo != null) {
                System.out.println("✅ Peer ready: " + peerInfo.getAddress() + ":" + peerInfo.getPort());
            }

            capture.startCapture();
            System.out.println("✅ Video capture started");

            captureThread = new Thread(this::captureAndSendLoop);
            captureThread.setName("VideoCapture-Optimized");
            captureThread.setPriority(Thread.NORM_PRIORITY);
            captureThread.setDaemon(true);
            captureThread.start();

            renderThread = new Thread(this::renderLoop);
            renderThread.setName("VideoRender-Optimized");
            renderThread.setPriority(Thread.NORM_PRIORITY);
            renderThread.setDaemon(true);
            renderThread.start();

            System.out.println("✅ Video streaming started");

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
                    Thread.sleep(Math.max(1, FRAME_INTERVAL_MS - timeSinceLastFrame));
                    continue;
                }

                if (isVideoEnabled.get()) {
                    BufferedImage frame = capture.captureFrame();

                    if (frame != null) {
                        // ✅ FIX: Non-blocking local view update
                        pendingLocalFrame.set(frame);
                        localViewNeedsUpdate = true;

                        byte[] encodedFrame = encodeFrame(frame);
                        if (encodedFrame != null && encodedFrame.length > 0) {
                            boolean sent = sendVideoFrame(encodedFrame);
                            if (sent) {
                                sentFrames++;
                            }
                        }

                        if (currentTime - lastStatsTime >= 5000 && sentFrames > 0) {
                            int fps = (int) (sentFrames / ((currentTime - lastStatsTime) / 1000.0));
                            System.out.println("📹 Sending: " + fps + " fps");
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
                BufferedImage remoteFrame = remoteFrameBuffer.poll();

                if (remoteFrame != null) {
                    // ✅ FIX: Non-blocking remote view update
                    pendingRemoteFrame.set(remoteFrame);
                    remoteViewNeedsUpdate = true;
                } else {
                    Thread.sleep(10);
                }

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                System.err.println("❌ Render error: " + e.getMessage());
            }
        }

        System.out.println("🛑 Render loop ended");
    }

    // ✅ FIX: Optimized image conversion
    private Image bufferedImageToFXImage(BufferedImage bimg) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(bimg, "jpg", baos);
        ByteArrayInputStream bis = new ByteArrayInputStream(baos.toByteArray());
        return new Image(bis);
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
                    if (remoteFrameBuffer.size() >= BUFFER_SIZE) {
                        remoteFrameBuffer.poll();
                    }
                    remoteFrameBuffer.offer(image);

                    receivedFrames++;

                    if (receivedFrames == 1) {
                        System.out.println("✅ First frame received!");
                    }
                }
            }
        } catch (Exception e) {
            // Silent fail
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
                captureThread.join(500);
            } catch (InterruptedException e) {
                captureThread.interrupt();
            }
        }

        if (renderThread != null && renderThread.isAlive()) {
            try {
                renderThread.join(500);
            } catch (InterruptedException e) {
                renderThread.interrupt();
            }
        }

        if (localVideoView != null) {
            Platform.runLater(() -> localVideoView.setImage(null));
        }
        if (remoteVideoView != null) {
            Platform.runLater(() -> remoteVideoView.setImage(null));
        }

        remoteFrameBuffer.clear();
        pendingLocalFrame.set(null);
        pendingRemoteFrame.set(null);

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
        HIGH(1280, 720, 30);

        final int width, height, fps;
        VideoQuality(int w, int h, int f) {
            width = w;
            height = h;
            fps = f;
        }
    }
}