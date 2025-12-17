// FILE: vku/chatapp/client/media/audio/AudioCapture.java
// ✅ OPTIMIZED: 48kHz, noise reduction

package vku.chatapp.client.media.audio;

import javax.sound.sampled.*;

public class AudioCapture {
    private TargetDataLine microphone;
    private AudioFormat format;
    private boolean isCapturing = false;

    public AudioCapture() {
        // ✅ HIGH QUALITY: 48kHz, 16-bit, mono
        format = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                48000.0f,  // 48kHz sample rate
                16,        // 16-bit
                1,         // Mono
                2,         // Frame size
                48000.0f,  // Frame rate
                false      // Little endian
        );
    }

    public void startCapture() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        if (!AudioSystem.isLineSupported(info)) {
            System.err.println("⚠️ Line not supported, trying fallback format");
            // Fallback to 44.1kHz
            format = new AudioFormat(44100, 16, 1, true, false);
            info = new DataLine.Info(TargetDataLine.class, format);
        }

        microphone = (TargetDataLine) AudioSystem.getLine(info);

        // ✅ Larger buffer for smoother capture
        int bufferSize = (int) format.getSampleRate() * format.getFrameSize();
        microphone.open(format, bufferSize);
        microphone.start();
        isCapturing = true;

        System.out.println("✅ Audio capture started:");
        System.out.println("   Sample Rate: " + format.getSampleRate() + " Hz");
        System.out.println("   Bit Depth: " + format.getSampleSizeInBits() + " bit");
        System.out.println("   Channels: " + format.getChannels());
        System.out.println("   Buffer: " + bufferSize + " bytes");
    }

    public byte[] captureAudio(int bufferSize) {
        if (!isCapturing || microphone == null) {
            return new byte[0];
        }

        byte[] buffer = new byte[bufferSize];
        int bytesRead = microphone.read(buffer, 0, buffer.length);

        if (bytesRead > 0) {
            // ✅ Apply simple noise gate (optional)
            applyNoiseGate(buffer, bytesRead);

            byte[] audioData = new byte[bytesRead];
            System.arraycopy(buffer, 0, audioData, 0, bytesRead);
            return audioData;
        }

        return new byte[0];
    }

    /**
     * Simple noise gate to reduce background noise
     */
    private void applyNoiseGate(byte[] buffer, int length) {
        int threshold = 500; // Noise gate threshold

        for (int i = 0; i < length - 1; i += 2) {
            // Read 16-bit sample (little endian)
            int sample = (buffer[i + 1] << 8) | (buffer[i] & 0xFF);

            // Apply noise gate
            if (Math.abs(sample) < threshold) {
                buffer[i] = 0;
                buffer[i + 1] = 0;
            }
        }
    }

    public void stopCapture() {
        if (microphone != null) {
            isCapturing = false;
            microphone.stop();
            microphone.close();
            System.out.println("✅ Audio capture stopped");
        }
    }

    public boolean isCapturing() {
        return isCapturing;
    }

    public AudioFormat getFormat() {
        return format;
    }
}