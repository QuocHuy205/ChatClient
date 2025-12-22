// FILE: vku/chatapp/client/media/audio/AudioCapture.java
// ✅ OPTIMIZED: Reduced echo, better noise cancellation

package vku.chatapp.client.media.audio;

import javax.sound.sampled.*;

public class AudioCapture {
    private TargetDataLine microphone;
    private AudioFormat format;
    private boolean isCapturing = false;

    // Echo cancellation buffer
    private byte[] previousBuffer;
    private static final float ECHO_SUPPRESSION = 0.3f;

    public AudioCapture() {
        // ✅ OPTIMIZED: Lower sample rate for less latency
        format = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                16000.0f,  // 16kHz (lower = less data = less lag)
                16,        // 16-bit
                1,         // Mono
                2,         // Frame size
                16000.0f,  // Frame rate
                false      // Little endian
        );
    }

    public void startCapture() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        if (!AudioSystem.isLineSupported(info)) {
            System.err.println("⚠️ Line not supported, trying fallback format");
            format = new AudioFormat(16000, 16, 1, true, false);
            info = new DataLine.Info(TargetDataLine.class, format);
        }

        microphone = (TargetDataLine) AudioSystem.getLine(info);

        // ✅ Smaller buffer = less latency
        int bufferSize = (int) (format.getSampleRate() * format.getFrameSize() * 0.1); // 100ms buffer
        microphone.open(format, bufferSize);
        microphone.start();
        isCapturing = true;

        System.out.println("✅ Audio capture started:");
        System.out.println("   Sample Rate: " + format.getSampleRate() + " Hz");
        System.out.println("   Bit Depth: " + format.getSampleSizeInBits() + " bit");
        System.out.println("   Buffer: " + bufferSize + " bytes (low latency)");
    }

    public byte[] captureAudio(int bufferSize) {
        if (!isCapturing || microphone == null) {
            return new byte[0];
        }

        byte[] buffer = new byte[bufferSize];
        int bytesRead = microphone.read(buffer, 0, buffer.length);

        if (bytesRead > 0) {
            // ✅ Apply noise gate and echo cancellation
            applyNoiseGate(buffer, bytesRead);
            applyEchoCancellation(buffer, bytesRead);

            byte[] audioData = new byte[bytesRead];
            System.arraycopy(buffer, 0, audioData, 0, bytesRead);

            // Store for echo cancellation
            previousBuffer = audioData.clone();

            return audioData;
        }

        return new byte[0];
    }

    /**
     * Aggressive noise gate to remove background noise and echo
     */
    private void applyNoiseGate(byte[] buffer, int length) {
        int threshold = 800; // Higher threshold to reduce echo

        for (int i = 0; i < length - 1; i += 2) {
            int sample = (buffer[i + 1] << 8) | (buffer[i] & 0xFF);

            // Apply noise gate with smooth transition
            if (Math.abs(sample) < threshold) {
                buffer[i] = 0;
                buffer[i + 1] = 0;
            } else {
                // Reduce amplitude slightly to prevent clipping
                sample = (int) (sample * 0.85);
                buffer[i] = (byte) (sample & 0xFF);
                buffer[i + 1] = (byte) ((sample >> 8) & 0xFF);
            }
        }
    }

    /**
     * Simple echo cancellation
     */
    private void applyEchoCancellation(byte[] buffer, int length) {
        if (previousBuffer == null || previousBuffer.length != length) {
            return;
        }

        for (int i = 0; i < length - 1; i += 2) {
            int currentSample = (buffer[i + 1] << 8) | (buffer[i] & 0xFF);
            int previousSample = (previousBuffer[i + 1] << 8) | (previousBuffer[i] & 0xFF);

            // Subtract echo (previous signal)
            int cleanSample = (int) (currentSample - (previousSample * ECHO_SUPPRESSION));

            // Clamp to valid range
            cleanSample = Math.max(-32768, Math.min(32767, cleanSample));

            buffer[i] = (byte) (cleanSample & 0xFF);
            buffer[i + 1] = (byte) ((cleanSample >> 8) & 0xFF);
        }
    }

    public void stopCapture() {
        if (microphone != null) {
            isCapturing = false;
            microphone.stop();
            microphone.close();
            System.out.println("✅ Audio capture stopped");
        }
        previousBuffer = null;
    }

    public boolean isCapturing() {
        return isCapturing;
    }

    public AudioFormat getFormat() {
        return format;
    }
}