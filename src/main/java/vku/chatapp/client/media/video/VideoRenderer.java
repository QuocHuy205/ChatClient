package vku.chatapp.client.media.video;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

/**
 * VideoRenderer - Optimized for JavaCV
 * Converts byte arrays to JavaCV Frames efficiently
 */
public class VideoRenderer {
    private Java2DFrameConverter converter;
    private int frameCount = 0;
    private long lastStatsTime = System.currentTimeMillis();
    private int statsFrameCount = 0;

    public VideoRenderer() {
        this.converter = new Java2DFrameConverter();
        System.out.println("✅ VideoRenderer initialized (JavaCV mode)");
    }

    /**
     * Render frame data to JavaCV Frame
     * @param frameData JPEG encoded frame data
     * @return JavaCV Frame or null if error
     */
    public Frame renderFrame(byte[] frameData) {
        if (frameData == null || frameData.length == 0) {
            return null;
        }

        try {
            // Decode JPEG to BufferedImage
            ByteArrayInputStream bis = new ByteArrayInputStream(frameData);
            BufferedImage image = ImageIO.read(bis);

            if (image == null) {
                System.err.println("❌ Error decoding image from bytes");
                return null;
            }

            // Convert to JavaCV Frame
            Frame frame = converter.convert(image);

            frameCount++;
            statsFrameCount++;

            if (frameCount == 1) {
                System.out.println("✅ First frame rendered!");
            }

            // Stats every 2 seconds
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastStatsTime >= 2000 && statsFrameCount > 0) {
                int fps = (int) (statsFrameCount / ((currentTime - lastStatsTime) / 1000.0));
                System.out.println("✅ Rendering: " + fps + " fps, " +
                        "Total frames: " + frameCount +
                        ", Frame size: " + (frameData.length / 1024) + " KB");
                lastStatsTime = currentTime;
                statsFrameCount = 0;
            }

            return frame;

        } catch (Exception e) {
            System.err.println("❌ Error rendering video frame: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get total frames rendered
     */
    public int getFrameCount() {
        return frameCount;
    }

    /**
     * Reset frame counter
     */
    public void reset() {
        frameCount = 0;
        statsFrameCount = 0;
        lastStatsTime = System.currentTimeMillis();
        System.out.println("🔄 VideoRenderer reset");
    }
}