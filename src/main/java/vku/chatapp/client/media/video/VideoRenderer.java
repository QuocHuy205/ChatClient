package vku.chatapp.client.media.video;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.ByteArrayInputStream;

public class VideoRenderer {
    private ImageView videoView;

    public VideoRenderer(ImageView videoView) {
        this.videoView = videoView;
    }

    public void renderFrame(byte[] frameData) {
        if (frameData == null || frameData.length == 0) {
            return;
        }

        try {
            // Decode and display frame
            ByteArrayInputStream bis = new ByteArrayInputStream(frameData);
            Image image = new Image(bis);

            javafx.application.Platform.runLater(() -> {
                videoView.setImage(image);
            });

        } catch (Exception e) {
            System.err.println("Error rendering video frame: " + e.getMessage());
        }
    }
}
