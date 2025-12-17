package vku.chatapp.client.media.video;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.bytedeco.javacv.FrameGrabber;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * VideoCapture using JavaCV with real webcam support
 */
public class VideoCapture {
    private AtomicBoolean isCapturing;
    private int width;
    private int height;

    // JavaCV components
    private OpenCVFrameGrabber grabber;
    private Java2DFrameConverter converter;

    // Fallback test pattern
    private BufferedImage testPattern;
    private boolean useRealCamera = false;

    public VideoCapture(int width, int height) {
        this.width = width;
        this.height = height;
        this.isCapturing = new AtomicBoolean(false);
        this.converter = new Java2DFrameConverter();
        createTestPattern();
    }

    public void startCapture() throws Exception {
        isCapturing.set(true);

        // Try to initialize real camera
        try {
            grabber = new OpenCVFrameGrabber(0); // 0 = default camera
            grabber.setImageWidth(width);
            grabber.setImageHeight(height);
            grabber.setFrameRate(30);

            System.out.println("📹 Attempting to start camera...");
            grabber.start();

            // Test if camera works
            Frame testFrame = grabber.grab();
            if (testFrame != null) {
                useRealCamera = true;
                System.out.println("✅ Real camera started successfully!");
                System.out.println("   Resolution: " + width + "x" + height);
                System.out.println("   Frame rate: 30 fps");
                return;
            }

        } catch (Exception e) {
            System.err.println("⚠️ Could not start camera: " + e.getMessage());
            if (grabber != null) {
                try {
                    grabber.stop();
                    grabber.release();
                } catch (Exception ex) {
                    // Ignore
                }
                grabber = null;
            }
        }

        // Fallback to test pattern
        useRealCamera = false;
        System.out.println("📹 Using test pattern (camera not available)");
    }

    public BufferedImage captureFrame() {
        if (!isCapturing.get()) {
            return null;
        }

        // Try real camera first
        if (useRealCamera && grabber != null) {
            try {
                Frame frame = grabber.grab();
                if (frame != null && frame.image != null) {
                    BufferedImage image = converter.convert(frame);
                    if (image != null) {
                        return image;
                    }
                }
            } catch (Exception e) {
                System.err.println("⚠️ Camera error: " + e.getMessage());
                useRealCamera = false;

                // Try to restart camera
                new Thread(() -> {
                    try {
                        Thread.sleep(2000);
                        System.out.println("🔄 Attempting to restart camera...");
                        if (grabber != null) {
                            grabber.restart();
                            useRealCamera = true;
                            System.out.println("✅ Camera restarted");
                        }
                    } catch (Exception ex) {
                        System.err.println("❌ Camera restart failed");
                    }
                }).start();
            }
        }

        // Fallback to test pattern
        return testPattern;
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
        System.out.println("📹 Video capture stopped");
    }

    public void setResolution(int width, int height) {
        this.width = width;
        this.height = height;
        createTestPattern();

        // Update camera resolution if running
        if (useRealCamera && grabber != null) {
            try {
                grabber.setImageWidth(width);
                grabber.setImageHeight(height);
                System.out.println("📹 Camera resolution updated: " + width + "x" + height);
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

    /**
     * Create animated test pattern for when camera is not available
     */
    private void createTestPattern() {
        testPattern = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = testPattern.createGraphics();

        // Enable antialiasing
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Gradient background
        GradientPaint gradient = new GradientPaint(
                0, 0, new Color(20, 20, 40),
                width, height, new Color(40, 20, 60)
        );
        g.setPaint(gradient);
        g.fillRect(0, 0, width, height);

        // Center camera icon
        int centerX = width / 2;
        int centerY = height / 2;

        // Camera body
        g.setColor(new Color(255, 255, 255, 200));
        g.fillRoundRect(centerX - 70, centerY - 45, 140, 90, 15, 15);

        // Lens
        g.setColor(new Color(50, 50, 50));
        g.fillOval(centerX - 35, centerY - 35, 70, 70);

        // Lens reflection
        g.setColor(new Color(150, 150, 200, 100));
        g.fillOval(centerX - 25, centerY - 25, 30, 30);

        // Text
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 20));
        String title = "Camera Not Available";
        int titleWidth = g.getFontMetrics().stringWidth(title);
        g.drawString(title, centerX - titleWidth/2, centerY + 80);

        g.setFont(new Font("Arial", Font.PLAIN, 14));
        g.setColor(new Color(200, 200, 200));
        String subtitle = "Using test pattern";
        int subtitleWidth = g.getFontMetrics().stringWidth(subtitle);
        g.drawString(subtitle, centerX - subtitleWidth/2, centerY + 105);

        g.dispose();
    }

    /**
     * Get list of available cameras
     */
    public static String[] getAvailableCameras() {
        try {
            // Try to detect cameras
            int maxCameras = 5;
            java.util.List<String> cameras = new java.util.ArrayList<>();

            for (int i = 0; i < maxCameras; i++) {
                try {
                    OpenCVFrameGrabber testGrabber = new OpenCVFrameGrabber(i);
                    testGrabber.start();
                    cameras.add("Camera " + i);
                    testGrabber.stop();
                    testGrabber.release();
                } catch (Exception e) {
                    break;
                }
            }

            return cameras.toArray(new String[0]);

        } catch (Exception e) {
            return new String[0];
        }
    }
}

/* ============================================
 * USAGE EXAMPLE
 * ============================================
 *
 * // Create capture
 * VideoCapture capture = new VideoCapture(640, 480);
 *
 * // Start camera
 * capture.startCapture();
 *
 * // In capture loop:
 * BufferedImage frame = capture.captureFrame();
 * if (frame != null) {
 *     // Process frame
 *     if (capture.isUsingRealCamera()) {
 *         System.out.println("Real camera frame");
 *     } else {
 *         System.out.println("Test pattern");
 *     }
 * }
 *
 * // Stop
 * capture.stopCapture();
 */