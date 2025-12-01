package vku.chatapp.client.media.video;

import javafx.scene.image.ImageView;
import vku.chatapp.client.p2p.P2PClient;
import vku.chatapp.client.p2p.PeerRegistry;
import vku.chatapp.common.dto.PeerInfo;
import vku.chatapp.common.protocol.P2PMessage;
import vku.chatapp.common.protocol.P2PMessageType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoStreamHandler {
    private VideoCapture capture;
    private VideoRenderer renderer;
    private P2PClient p2pClient;
    private PeerRegistry peerRegistry;

    private Thread captureThread;
    private AtomicBoolean isStreaming;
    private Long remotePeerId;

    private static final int TARGET_FPS = 30;
    private static final int FRAME_INTERVAL_MS = 1000 / TARGET_FPS;
    private static final int VIDEO_WIDTH = 640;
    private static final int VIDEO_HEIGHT = 480;
    private static final float JPEG_QUALITY = 0.7f;

    public VideoStreamHandler(ImageView localView, ImageView remoteView) {
        this.capture = new VideoCapture(VIDEO_WIDTH, VIDEO_HEIGHT);
        this.renderer = new VideoRenderer(remoteView);
        this.p2pClient = new P2PClient();
        this.peerRegistry = PeerRegistry.getInstance();
        this.isStreaming = new AtomicBoolean(false);
    }

    public void startVideoStream(Long peerId) {
        if (isStreaming.get()) {
            System.out.println("Video stream already running");
            return;
        }

        this.remotePeerId = peerId;
        isStreaming.set(true);

        try {
            // Start video capture
            capture.startCapture();
            System.out.println("Video capture started");

            // Start capture and send loop
            captureThread = new Thread(this::captureAndSendLoop);
            captureThread.setName("VideoCapture-Thread");
            captureThread.setDaemon(true);
            captureThread.start();

            System.out.println("Video streaming started to peer: " + peerId);

        } catch (Exception e) {
            System.err.println("Error starting video stream: " + e.getMessage());
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

                // Maintain target FPS
                if (timeSinceLastFrame < FRAME_INTERVAL_MS) {
                    Thread.sleep(FRAME_INTERVAL_MS - timeSinceLastFrame);
                    continue;
                }

                // Capture frame
                BufferedImage frame = capture.captureFrame();

                if (frame != null) {
                    // Encode frame to JPEG
                    byte[] encodedFrame = encodeFrame(frame);

                    if (encodedFrame != null && encodedFrame.length > 0) {
                        // Send to remote peer
                        sendVideoFrame(encodedFrame);
                    }
                }

                lastFrameTime = currentTime;

            } catch (InterruptedException e) {
                System.out.println("Video capture interrupted");
                break;
            } catch (Exception e) {
                System.err.println("Error in video capture loop: " + e.getMessage());
            }
        }
    }

    private byte[] encodeFrame(BufferedImage frame) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(frame, "jpg", baos);
            baos.flush();
            byte[] imageBytes = baos.toByteArray();
            baos.close();
            return imageBytes;

        } catch (Exception e) {
            System.err.println("Error encoding frame: " + e.getMessage());
            return null;
        }
    }

    private void sendVideoFrame(byte[] frameData) {
        if (remotePeerId == null) return;

        PeerInfo peerInfo = peerRegistry.getPeerInfo(remotePeerId);
        if (peerInfo == null) {
            System.err.println("Peer not found: " + remotePeerId);
            return;
        }

        P2PMessage message = new P2PMessage(P2PMessageType.VIDEO_STREAM, null, remotePeerId);
        message.setMessageId(UUID.randomUUID().toString());
        message.setFileData(frameData);
        message.setTimestamp(System.currentTimeMillis());

        // Send asynchronously
        p2pClient.sendMessageAsync(peerInfo.getAddress(), peerInfo.getPort(), message);
    }

    public void receiveVideoStream(P2PMessage message) {
        if (!isStreaming.get()) {
            return;
        }

        try {
            byte[] frameData = message.getFileData();
            if (frameData != null && frameData.length > 0) {
                renderer.renderFrame(frameData);
            }

        } catch (Exception e) {
            System.err.println("Error receiving video stream: " + e.getMessage());
        }
    }

    public void stopVideoStream() {
        isStreaming.set(false);

        // Stop capture
        if (capture != null) {
            capture.stopCapture();
        }

        // Wait for thread to finish
        if (captureThread != null && captureThread.isAlive()) {
            try {
                captureThread.join(1000);
            } catch (InterruptedException e) {
                captureThread.interrupt();
            }
        }

        System.out.println("Video streaming stopped");
    }

    public boolean isStreaming() {
        return isStreaming.get();
    }

    // Video quality control
    public void setVideoQuality(VideoQuality quality) {
        capture.setResolution(quality.width, quality.height);
        System.out.println("Video quality set to: " + quality);
    }

    public enum VideoQuality {
        LOW(320, 240, 15),
        MEDIUM(640, 480, 30),
        HIGH(1280, 720, 30),
        FULL_HD(1920, 1080, 30);

        final int width;
        final int height;
        final int fps;

        VideoQuality(int width, int height, int fps) {
            this.width = width;
            this.height = height;
            this.fps = fps;
        }
    }
}