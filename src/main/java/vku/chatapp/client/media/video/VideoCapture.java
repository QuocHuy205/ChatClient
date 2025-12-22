// FILE: vku/chatapp/client/media/video/VideoCapture.java
// ✅ OPTIMIZED: Faster startup, smoother video

package vku.chatapp.client.media.video;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoCapture {
    private AtomicBoolean isCapturing;
    private int width;
    private int height;

    private OpenCVFrameGrabber grabber;
    private Java2DFrameConverter converter;

    private BufferedImage testPattern;
    private boolean useRealCamera = false;

    // ✅ Cache first frame
    private BufferedImage cachedFrame;
    private long lastFrameTime = 0;
    private static final long FRAME_CACHE_MS = 33; // ~30fps

    public VideoCapture(int width, int height) {
        this.width = width;
        this.height = height;
        this.isCapturing = new AtomicBoolean(false);
        this.converter = new Java2DFrameConverter();
        createTestPattern();
    }

    public void startCapture() throws Exception {
        isCapturing.set(true);

        try {
            grabber = new OpenCVFrameGrabber(0);

            // ✅ Lower resolution for faster processing
            grabber.setImageWidth(640);
            grabber.setImageHeight(480);
            grabber.setFrameRate(30);

            // ✅ Reduce startup time
            grabber.setTimeout(3000); // 3s timeout

            System.out.println("📹 Starting camera...");
            grabber.start();

            // Quick test
            Frame testFrame = grabber.grab();
            if (testFrame != null && testFrame.image != null) {
                useRealCamera = true;
                cachedFrame = converter.convert(testFrame);
                System.out.println("✅ Camera ready!");
                return;
            }

        } catch (Exception e) {
            System.err.println("⚠️ Camera not available: " + e.getMessage());
            if (grabber != null) {
                try {
                    grabber.stop();
                    grabber.release();
                } catch (Exception ex) {}
                grabber = null;
            }
        }

        useRealCamera = false;
        System.out.println("📹 Using test pattern");
    }

    public BufferedImage captureFrame() {
        if (!isCapturing.get()) {
            return null;
        }

        if (useRealCamera && grabber != null) {
            try {
                // ✅ Frame caching to reduce CPU
                long currentTime = System.currentTimeMillis();
                if (cachedFrame != null && (currentTime - lastFrameTime) < FRAME_CACHE_MS) {
                    return cachedFrame;
                }

                Frame frame = grabber.grab();
                if (frame != null && frame.image != null) {
                    cachedFrame = converter.convert(frame);
                    lastFrameTime = currentTime;

                    // ✅ Resize if needed for better performance
                    if (cachedFrame.getWidth() > width || cachedFrame.getHeight() > height) {
                        cachedFrame = resizeImage(cachedFrame, width, height);
                    }

                    return cachedFrame;
                }
            } catch (Exception e) {
                System.err.println("⚠️ Camera error: " + e.getMessage());
                useRealCamera = false;
            }
        }

        return testPattern;
    }

    private BufferedImage resizeImage(BufferedImage original, int targetWidth, int targetHeight) {
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        g.dispose();
        return resized;
    }

    public void stopCapture() {
        isCapturing.set(false);

        if (grabber != null) {
            try {
                grabber.stop();
                grabber.release();
                System.out.println("✅ Camera released");
            } catch (Exception e) {
                System.err.println("⚠️ Error releasing camera: " + e.getMessage());
            }
            grabber = null;
        }

        useRealCamera = false;
        cachedFrame = null;
        System.out.println("📹 Video capture stopped");
    }

    public void setResolution(int width, int height) {
        this.width = width;
        this.height = height;
        createTestPattern();

        if (useRealCamera && grabber != null) {
            try {
                grabber.setImageWidth(width);
                grabber.setImageHeight(height);
            } catch (Exception e) {
                System.err.println("⚠️ Could not update resolution: " + e.getMessage());
            }
        }
    }

    public boolean isCapturing() {
        return isCapturing.get();
    }

    public boolean isUsingRealCamera() {
        return useRealCamera;
    }

    private void createTestPattern() {
        testPattern = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = testPattern.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        GradientPaint gradient = new GradientPaint(
                0, 0, new Color(20, 20, 40),
                width, height, new Color(40, 20, 60)
        );
        g.setPaint(gradient);
        g.fillRect(0, 0, width, height);

        int centerX = width / 2;
        int centerY = height / 2;

        g.setColor(new Color(255, 255, 255, 200));
        g.fillRoundRect(centerX - 70, centerY - 45, 140, 90, 15, 15);

        g.setColor(new Color(50, 50, 50));
        g.fillOval(centerX - 35, centerY - 35, 70, 70);

        g.setColor(new Color(150, 150, 200, 100));
        g.fillOval(centerX - 25, centerY - 25, 30, 30);

        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 20));
        String title = "Camera Not Available";
        int titleWidth = g.getFontMetrics().stringWidth(title);
        g.drawString(title, centerX - titleWidth/2, centerY + 80);

        g.dispose();
    }
}