// FILE: vku/chatapp/client/media/audio/AudioPlayer.java
// ✅ OPTIMIZED: Lower latency, smoother playback

package vku.chatapp.client.media.audio;

import javax.sound.sampled.*;

public class AudioPlayer {
    private SourceDataLine speakers;
    private AudioFormat format;
    private boolean isPlaying = false;

    public AudioPlayer() {
        // ✅ Match capture format: 16kHz for lower latency
        format = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                16000.0f,  // 16kHz
                16,        // 16-bit
                1,         // Mono
                2,         // Frame size
                16000.0f,  // Frame rate
                false      // Little endian
        );
    }

    public void startPlayback() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

        if (!AudioSystem.isLineSupported(info)) {
            System.err.println("⚠️ Line not supported, trying fallback");
            format = new AudioFormat(16000, 16, 1, true, false);
            info = new DataLine.Info(SourceDataLine.class, format);
        }

        speakers = (SourceDataLine) AudioSystem.getLine(info);

        // ✅ Smaller buffer for lower latency
        int bufferSize = (int) (format.getSampleRate() * format.getFrameSize() * 0.1); // 100ms
        speakers.open(format, bufferSize);
        speakers.start();
        isPlaying = true;

        System.out.println("✅ Audio playback started:");
        System.out.println("   Sample Rate: " + format.getSampleRate() + " Hz");
        System.out.println("   Buffer: " + bufferSize + " bytes (low latency)");
    }

    public void playAudio(byte[] audioData) {
        if (!isPlaying || speakers == null || audioData == null || audioData.length == 0) {
            return;
        }

        try {
            // ✅ Write data immediately without chunking
            int written = 0;
            while (written < audioData.length && isPlaying) {
                int result = speakers.write(audioData, written, audioData.length - written);
                if (result > 0) {
                    written += result;
                } else {
                    break;
                }
            }

        } catch (Exception e) {
            System.err.println("⚠️ Playback error: " + e.getMessage());
        }
    }

    public void stopPlayback() {
        if (speakers != null) {
            isPlaying = false;

            try {
                speakers.flush(); // Clear buffer instead of drain
                speakers.stop();
                speakers.close();
                System.out.println("✅ Audio playback stopped");
            } catch (Exception e) {
                System.err.println("⚠️ Error stopping playback: " + e.getMessage());
            }
        }
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public AudioFormat getFormat() {
        return format;
    }
}